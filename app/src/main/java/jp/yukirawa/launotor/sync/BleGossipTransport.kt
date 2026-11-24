package jp.yukirawa.launotor.sync

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import jp.yukirawa.launotor.domain.model.MachineTimerRecord
import jp.yukirawa.launotor.domain.model.UpdateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Connectionless legacy BLE gossip transport using 16-bit Service Data UUID `0xF1A0`.
 *
 * Rotates compact 24-byte frames (header + up to 3 machine entries) every 2.5 seconds
 * when active/tombstone machines exist, and scans passively using `SCAN_MODE_BALANCED`
 * in foreground or `SCAN_MODE_LOW_POWER` in background.
 *
 * Silently degrades when Bluetooth is off, hardware is unavailable, or optional BLE
 * runtime permissions are not granted.
 */
class BleGossipTransport(
    context: Context,
    private val deviceIdProvider: SyncDeviceIdProvider,
) : SyncTransport {

    override val source: UpdateSource = UpdateSource.P2P_BLE

    private val appContext = context.applicationContext
    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(BluetoothManager::class.java)

    private val stateLock = Any()
    private var scope: CoroutineScope? = null
    private var advertiseLoopJob: Job? = null
    private var btStateReceiver: BroadcastReceiver? = null

    @Volatile
    private var isStarted = false

    @Volatile
    private var isForeground = true

    @Volatile
    private var latestRecords: Map<String, MachineTimerRecord> = emptyMap()

    @Volatile
    private var packetCallback: ((ReceivedSyncPacket) -> Unit)? = null

    @Volatile
    private var activeScanner: BluetoothLeScanner? = null

    @Volatile
    private var activeScanCallback: ScanCallback? = null

    @Volatile
    private var activeAdvertiser: BluetoothLeAdvertiser? = null

    @Volatile
    private var activeAdvertiseCallback: AdvertiseCallback? = null

    private var frameRotationIndex = 0
    private val recentFrameDedup = ConcurrentHashMap<Int, Long>()

    override fun start(onPacketReceived: (ReceivedSyncPacket) -> Unit) {
        synchronized(stateLock) {
            packetCallback = onPacketReceived
            if (isStarted) {
                // Re-attempt scanner/advertiser in case permissions or BT state changed
                restartBleOperations()
                return
            }
            isStarted = true
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            registerBluetoothStateReceiver()
            restartBleOperations()
        }
    }

    override fun updatePayload(records: Map<String, MachineTimerRecord>, nowEpochMillis: Long) {
        latestRecords = records
    }

    override fun broadcastNow(records: Map<String, MachineTimerRecord>, nowEpochMillis: Long) {
        latestRecords = records
        if (!isStarted) return
        synchronized(stateLock) {
            advertiseSingleTick(nowEpochMillis)
        }
    }

    override fun setPowerMode(foreground: Boolean) {
        synchronized(stateLock) {
            if (isForeground == foreground) return
            isForeground = foreground
            if (isStarted) {
                stopScanning()
                startScanning()
                advertiseSingleTick(System.currentTimeMillis())
            }
        }
    }

    fun refreshCapabilities() {
        synchronized(stateLock) {
            if (isStarted) {
                restartBleOperations()
            }
        }
    }

    override fun stop() {
        synchronized(stateLock) {
            if (!isStarted) return
            isStarted = false
            packetCallback = null
            advertiseLoopJob?.cancel()
            advertiseLoopJob = null
            stopScanning()
            stopAdvertising()
            unregisterBluetoothStateReceiver()
            recentFrameDedup.clear()
            scope?.cancel()
            scope = null
        }
    }

    private fun restartBleOperations() {
        stopScanning()
        stopAdvertising()
        advertiseLoopJob?.cancel()
        advertiseLoopJob = null

        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            return
        }

        startScanning()
        startAdvertiseLoop()
    }

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        if (!hasScanPermission(appContext)) return
        val adapter = bluetoothManager?.adapter ?: return
        if (!adapter.isEnabled) return
        val scanner = runCatching { adapter.bluetoothLeScanner }.getOrNull() ?: return

        val filter = ScanFilter.Builder()
            .setServiceData(SERVICE_UUID, ByteArray(0))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(
                if (isForeground) ScanSettings.SCAN_MODE_BALANCED else ScanSettings.SCAN_MODE_LOW_POWER,
            )
            .setReportDelay(0L)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                for (result in results) {
                    handleScanResult(result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                // Silent fallback (e.g. too many scanners or BT turning off)
            }
        }

        try {
            scanner.startScan(listOf(filter), settings, callback)
            activeScanner = scanner
            activeScanCallback = callback
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        } catch (_: Exception) {
            // Hardware / stack transient error
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        val scanner = activeScanner
        val callback = activeScanCallback
        activeScanner = null
        activeScanCallback = null
        if (scanner != null && callback != null) {
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {
                // Ignore if BT turned off or permission missing
            }
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val bytes = result.scanRecord?.getServiceData(SERVICE_UUID) ?: return
        if (bytes.size < SyncPayloadCodec.HEADER_SIZE) return

        val now = System.currentTimeMillis()
        val dedupKey = bytes.contentHashCode()
        val lastSeen = recentFrameDedup[dedupKey]
        if (lastSeen != null && now - lastSeen < SCAN_FRAME_DEDUP_MS) {
            return
        }
        recentFrameDedup[dedupKey] = now
        if (recentFrameDedup.size > 64) {
            val cutoff = now - SCAN_FRAME_DEDUP_MS * 2
            recentFrameDedup.entries.removeIf { it.value < cutoff }
        }

        val packet = SyncPayloadCodec.decode(bytes) ?: return
        if (packet.senderShortId == (deviceIdProvider.senderShortId and 0xFFFF)) return
        packetCallback?.invoke(ReceivedSyncPacket(packet, UpdateSource.P2P_BLE))
    }

    private fun startAdvertiseLoop() {
        val currentScope = scope ?: return
        advertiseLoopJob = currentScope.launch {
            var tickCount = 0
            while (isActive && isStarted) {
                synchronized(stateLock) {
                    val frames = buildCurrentFrames(System.currentTimeMillis())
                    if (frames.isEmpty()) {
                        stopAdvertising()
                    } else if (frames.size > 1 || tickCount % 2 == 0 || activeAdvertiseCallback == null) {
                        val frame = frames[frameRotationIndex % frames.size]
                        frameRotationIndex = (frameRotationIndex + 1) % frames.size
                        advertiseFrame(frame)
                    }
                }
                tickCount++
                delay(ROTATION_INTERVAL_MS)
            }
        }
    }

    private fun advertiseSingleTick(nowEpochMillis: Long) {
        val frames = buildCurrentFrames(nowEpochMillis)
        if (frames.isEmpty()) {
            stopAdvertising()
            return
        }
        val frame = frames[frameRotationIndex % frames.size]
        frameRotationIndex = (frameRotationIndex + 1) % frames.size
        advertiseFrame(frame)
    }

    private fun buildCurrentFrames(nowEpochMillis: Long): List<ByteArray> {
        return SyncPayloadCodec.buildBleFrames(
            records = latestRecords,
            senderShortId = deviceIdProvider.senderShortId,
            nowEpochMillis = nowEpochMillis,
        )
    }

    @SuppressLint("MissingPermission")
    private fun advertiseFrame(frameBytes: ByteArray) {
        if (!hasAdvertisePermission(appContext)) return
        val adapter = bluetoothManager?.adapter ?: return
        if (!adapter.isEnabled) return
        val advertiser = runCatching { adapter.bluetoothLeAdvertiser }.getOrNull() ?: return

        stopAdvertising()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(
                if (isForeground) {
                    AdvertiseSettings.ADVERTISE_MODE_BALANCED
                } else {
                    AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
                },
            )
            .setConnectable(false)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        // IMPORTANT: Omit addServiceUuid() so 3B Flags + 4B ServiceData header + 24B payload = 31B max!
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceData(SERVICE_UUID, frameBytes)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                activeAdvertiseCallback = null
            }
        }

        try {
            advertiser.startAdvertising(settings, data, callback)
            activeAdvertiser = advertiser
            activeAdvertiseCallback = callback
        } catch (_: SecurityException) {
            // Permission revoked concurrently
        } catch (_: Exception) {
            // Hardware / stack transient error
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        val advertiser = activeAdvertiser
        val callback = activeAdvertiseCallback
        activeAdvertiser = null
        activeAdvertiseCallback = null
        if (advertiser != null && callback != null) {
            try {
                advertiser.stopAdvertising(callback)
            } catch (_: Exception) {
                // Ignore if BT turned off or permission missing
            }
        }
    }

    private fun registerBluetoothStateReceiver() {
        if (btStateReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                synchronized(stateLock) {
                    if (!isStarted) return
                    when (state) {
                        BluetoothAdapter.STATE_ON -> restartBleOperations()
                        BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                            advertiseLoopJob?.cancel()
                            advertiseLoopJob = null
                            stopScanning()
                            stopAdvertising()
                        }
                    }
                }
            }
        }
        btStateReceiver = receiver
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun unregisterBluetoothStateReceiver() {
        btStateReceiver?.let { receiver ->
            runCatching { appContext.unregisterReceiver(receiver) }
        }
        btStateReceiver = null
    }

    companion object {
        val SERVICE_UUID: ParcelUuid = ParcelUuid(
            UUID.fromString("0000F1A0-0000-1000-8000-00805F9B34FB"),
        )
        private const val ROTATION_INTERVAL_MS = 2_500L
        private const val SCAN_FRAME_DEDUP_MS = 900L

        fun requiredRuntimePermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                )
            } else {
                emptyArray()
            }
        }

        fun hasAllBlePermissions(context: Context): Boolean {
            return hasScanPermission(context) && hasAdvertisePermission(context)
        }

        fun hasScanPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        fun hasAdvertisePermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }
    }
}
