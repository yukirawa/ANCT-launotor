package jp.yukirawa.launotor.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import jp.yukirawa.launotor.domain.model.MachineTimerRecord
import jp.yukirawa.launotor.domain.model.UpdateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Local Wi-Fi LAN transport combining `NsdManager` (`_launotor._udp.`) peer discovery
 * with compact 101-byte UDP snapshot broadcast & unicast on port 47821.
 *
 * Gracefully degrades under AP isolation, missing Wi-Fi, or firewall restrictions.
 */
class LanSyncTransport(
    context: Context,
    private val deviceIdProvider: SyncDeviceIdProvider,
) : SyncTransport {

    override val source: UpdateSource = UpdateSource.P2P_LAN

    private val appContext = context.applicationContext
    private val nsdManager: NsdManager? = appContext.getSystemService(NsdManager::class.java)
    private val wifiManager: WifiManager? = appContext.getSystemService(WifiManager::class.java)

    private val stateLock = Any()
    private var scope: CoroutineScope? = null
    private var receiveJob: Job? = null
    private var heartbeatJob: Job? = null
    private var resolveJob: Job? = null

    @Volatile
    private var isStarted = false

    @Volatile
    private var isForeground = true

    @Volatile
    private var latestRecords: Map<String, MachineTimerRecord> = emptyMap()

    @Volatile
    private var socket: DatagramSocket? = null

    @Volatile
    private var boundPort: Int = UDP_PORT

    private var multicastLock: WifiManager.MulticastLock? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val api34Callbacks = ConcurrentHashMap<String, NsdManager.ServiceInfoCallback>()
    private val nsdExecutor = Executors.newSingleThreadExecutor()

    private val peers = ConcurrentHashMap<String, InetSocketAddress>()
    private val learnedIpPeers = ConcurrentHashMap<String, Long>()
    private var resolveQueue: Channel<NsdServiceInfo>? = null

    private val localServiceName: String
        get() = "$SERVICE_NAME_PREFIX${deviceIdProvider.senderShortId.toString(16)}"

    @Volatile
    private var registeredServiceName: String = ""

    override fun start(onPacketReceived: (ReceivedSyncPacket) -> Unit) {
        synchronized(stateLock) {
            if (isStarted) return
            isStarted = true

            val transportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = transportScope
            resolveQueue = Channel(Channel.UNLIMITED)

            acquireMulticastLock()
            val openedSocket = openUdpSocket()
            socket = openedSocket
            boundPort = openedSocket?.localPort ?: UDP_PORT

            if (openedSocket != null) {
                receiveJob = transportScope.launch {
                    runReceiveLoop(openedSocket, onPacketReceived)
                }
            }

            resolveJob = transportScope.launch {
                processResolveQueue()
            }

            startNsd()

            heartbeatJob = transportScope.launch {
                // Initial burst so a freshly opened device discovers/catches up quickly
                delay(400L)
                sendSnapshotInternal(latestRecords, System.currentTimeMillis())
                while (isActive && isStarted) {
                    val intervalMs = if (isForeground) FOREGROUND_HEARTBEAT_MS else BACKGROUND_HEARTBEAT_MS
                    delay(intervalMs)
                    if (isStarted) {
                        sendSnapshotInternal(latestRecords, System.currentTimeMillis())
                    }
                }
            }
        }
    }

    override fun updatePayload(records: Map<String, MachineTimerRecord>, nowEpochMillis: Long) {
        latestRecords = records
    }

    override fun broadcastNow(records: Map<String, MachineTimerRecord>, nowEpochMillis: Long) {
        latestRecords = records
        if (!isStarted) return
        scope?.launch {
            sendSnapshotInternal(records, nowEpochMillis)
        }
    }

    override fun setPowerMode(foreground: Boolean) {
        isForeground = foreground
    }

    override fun stop() {
        synchronized(stateLock) {
            if (!isStarted) return
            isStarted = false

            stopNsd()
            receiveJob?.cancel()
            heartbeatJob?.cancel()
            resolveJob?.cancel()
            resolveQueue?.close()
            resolveQueue = null

            runCatching { socket?.close() }
            socket = null
            peers.clear()
            learnedIpPeers.clear()

            releaseMulticastLock()
            scope?.cancel()
            scope = null
        }
    }

    private fun openUdpSocket(): DatagramSocket? {
        return runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(UDP_PORT))
            }
        }.getOrElse {
            // Fallback to ephemeral port if 47821 is temporarily occupied
            runCatching {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(0))
                }
            }.getOrNull()
        }
    }

    private fun runReceiveLoop(
        sock: DatagramSocket,
        onPacketReceived: (ReceivedSyncPacket) -> Unit,
    ) {
        val buffer = ByteArray(MAX_UDP_RECEIVE_BYTES)
        val localShortId = deviceIdProvider.senderShortId and 0xFFFF

        while (isStarted && !sock.isClosed) {
            try {
                val datagram = DatagramPacket(buffer, buffer.size)
                sock.receive(datagram)
                val length = datagram.length
                if (length < SyncPayloadCodec.HEADER_SIZE) continue

                val bytes = buffer.copyOfRange(0, length)
                val decoded = SyncPayloadCodec.decode(bytes) ?: continue
                if (decoded.senderShortId == localShortId) continue

                val senderAddr = datagram.address
                val senderPort = datagram.port
                if (senderAddr != null && senderPort > 0) {
                    val peerKey = "${senderAddr.hostAddress}:$senderPort"
                    val isNewPeer = !learnedIpPeers.containsKey(peerKey)
                    learnedIpPeers[peerKey] = System.currentTimeMillis()
                    if (isNewPeer) {
                        scope?.launch {
                            sendSnapshotToAddress(
                                sock = sock,
                                address = InetSocketAddress(senderAddr, senderPort),
                                records = latestRecords,
                                nowEpochMillis = System.currentTimeMillis(),
                            )
                        }
                    }
                }

                onPacketReceived(ReceivedSyncPacket(decoded, UpdateSource.P2P_LAN))
            } catch (_: Exception) {
                if (!isStarted || sock.isClosed) break
            }
        }
    }

    private fun sendSnapshotInternal(
        records: Map<String, MachineTimerRecord>,
        nowEpochMillis: Long,
    ) {
        val sock = socket ?: return
        if (sock.isClosed) return

        val packet = SyncPayloadCodec.buildLanSnapshotPacket(
            records = records,
            senderShortId = deviceIdProvider.senderShortId,
            nowEpochMillis = nowEpochMillis,
        )
        val payload = SyncPayloadCodec.encode(packet)
        val targets = LinkedHashSet<InetSocketAddress>()

        // 1. NSD-discovered peers (unicast)
        targets.addAll(peers.values)

        // 2. Recently heard UDP peers (unicast fallback if mDNS is blocked)
        val cutoff = nowEpochMillis - LEARNED_PEER_TTL_MS
        learnedIpPeers.entries.removeIf { it.value < cutoff }
        for (key in learnedIpPeers.keys) {
            val sep = key.lastIndexOf(':')
            if (sep > 0) {
                val host = key.substring(0, sep)
                val port = key.substring(sep + 1).toIntOrNull()
                if (port != null && port > 0) {
                    runCatching { InetAddress.getByName(host) }.getOrNull()?.let { addr ->
                        targets.add(InetSocketAddress(addr, port))
                    }
                }
            }
        }

        // 3. Interface subnet broadcasts + global broadcast on standard port
        for (bcast in collectBroadcastAddresses()) {
            targets.add(InetSocketAddress(bcast, UDP_PORT))
        }

        for (target in targets) {
            try {
                sock.send(DatagramPacket(payload, payload.size, target))
            } catch (_: Exception) {
                // Silent degradation under AP isolation / network transitions
            }
        }
    }

    private fun sendSnapshotToAddress(
        sock: DatagramSocket,
        address: InetSocketAddress,
        records: Map<String, MachineTimerRecord>,
        nowEpochMillis: Long,
    ) {
        if (sock.isClosed || !isStarted) return
        val packet = SyncPayloadCodec.buildLanSnapshotPacket(
            records = records,
            senderShortId = deviceIdProvider.senderShortId,
            nowEpochMillis = nowEpochMillis,
        )
        val payload = SyncPayloadCodec.encode(packet)
        try {
            sock.send(DatagramPacket(payload, payload.size, address))
        } catch (_: Exception) {
            // Ignore transient send errors
        }
    }

    private fun collectBroadcastAddresses(): List<InetAddress> {
        val result = LinkedHashSet<InetAddress>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                for (ifAddr in nif.interfaceAddresses) {
                    val addr = ifAddr.address
                    val bcast = ifAddr.broadcast
                    if (addr is Inet4Address && bcast != null) {
                        result.add(bcast)
                    }
                }
            }
            runCatching { InetAddress.getByName("255.255.255.255") }.getOrNull()?.let {
                result.add(it)
            }
        } catch (_: Exception) {
            // Ignore interface enumeration errors
        }
        return result.toList()
    }

    private fun acquireMulticastLock() {
        try {
            val lock = wifiManager?.createMulticastLock("launotor_lan_sync")?.apply {
                setReferenceCounted(false)
                acquire()
            }
            multicastLock = lock
        } catch (_: Exception) {
            multicastLock = null
        }
    }

    private fun releaseMulticastLock() {
        try {
            val lock = multicastLock
            if (lock != null && lock.isHeld) {
                lock.release()
            }
        } catch (_: Exception) {
            // Ignore double-release or system error
        } finally {
            multicastLock = null
        }
    }

    private fun startNsd() {
        val manager = nsdManager ?: return

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = localServiceName
            serviceType = NSD_SERVICE_TYPE
            port = boundPort
        }

        val regListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registeredServiceName = info.serviceName ?: localServiceName
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.d(TAG, "NSD registration failed: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = regListener
        runCatching {
            manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, regListener)
        }

        val discListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.d(TAG, "NSD start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                synchronized(stateLock) {
                    if (!isStarted) return
                    val foundName = service.serviceName
                    if (foundName == localServiceName ||
                        (registeredServiceName.isNotEmpty() && foundName == registeredServiceName)
                    ) {
                        return
                    }
                    if (!service.serviceType.contains("_launotor")) return

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        registerApi34CallbackLocked(manager, service)
                    } else {
                        resolveQueue?.trySend(service)
                    }
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                peers.remove(service.serviceName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    api34Callbacks.remove(service.serviceName)?.let { cb ->
                        runCatching { manager.unregisterServiceInfoCallback(cb) }
                    }
                }
            }
        }
        discoveryListener = discListener
        runCatching {
            manager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discListener)
        }
    }

    private fun registerApi34CallbackLocked(manager: NsdManager, service: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (!isStarted) return
        val name = service.serviceName ?: return
        if (api34Callbacks.containsKey(name)) return

        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                api34Callbacks.remove(name)
            }

            override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                if (!isStarted) return
                val host = serviceInfo.hostAddresses.firstOrNull { it is Inet4Address }
                    ?: serviceInfo.hostAddresses.firstOrNull()
                val port = serviceInfo.port
                if (host != null && port > 0) {
                    val peerAddr = InetSocketAddress(host, port)
                    val isNew = peers.put(name, peerAddr) == null
                    if (isNew) {
                        scope?.launch {
                            socket?.let { sock ->
                                sendSnapshotToAddress(
                                    sock = sock,
                                    address = peerAddr,
                                    records = latestRecords,
                                    nowEpochMillis = System.currentTimeMillis(),
                                )
                            }
                        }
                    }
                }
            }

            override fun onServiceLost() {
                peers.remove(name)
            }

            override fun onServiceInfoCallbackUnregistered() {}
        }

        if (api34Callbacks.putIfAbsent(name, callback) == null) {
            runCatching {
                manager.registerServiceInfoCallback(service, nsdExecutor, callback)
            }.onFailure {
                api34Callbacks.remove(name)
            }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun processResolveQueue() {
        val queue = resolveQueue ?: return
        val manager = nsdManager ?: return
        for (service in queue) {
            if (!isStarted) break
            val done = kotlinx.coroutines.CompletableDeferred<Unit>()
            try {
                manager.resolveService(
                    service,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            done.complete(Unit)
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host
                            val port = serviceInfo.port
                            if (host != null && port > 0) {
                                val peerAddr = InetSocketAddress(host, port)
                                val isNew = peers.put(serviceInfo.serviceName, peerAddr) == null
                                if (isNew) {
                                    scope?.launch {
                                        socket?.let { sock ->
                                            sendSnapshotToAddress(
                                                sock = sock,
                                                address = peerAddr,
                                                records = latestRecords,
                                                nowEpochMillis = System.currentTimeMillis(),
                                            )
                                        }
                                    }
                                }
                            }
                            done.complete(Unit)
                        }
                    },
                )
                withTimeoutOrNull(5_000L) {
                    done.await()
                }
            } catch (_: Exception) {
                // Ignore NSD resolve failures
            }
        }
    }

    private fun stopNsd() {
        val manager = nsdManager ?: return
        registrationListener?.let { listener ->
            runCatching { manager.unregisterService(listener) }
        }
        registrationListener = null
        registeredServiceName = ""

        discoveryListener?.let { listener ->
            runCatching { manager.stopServiceDiscovery(listener) }
        }
        discoveryListener = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            api34Callbacks.values.forEach { cb ->
                runCatching { manager.unregisterServiceInfoCallback(cb) }
            }
            api34Callbacks.clear()
        }
    }

    companion object {
        private const val TAG = "LanSyncTransport"
        const val UDP_PORT = 47821
        const val NSD_SERVICE_TYPE = "_launotor._udp."
        private const val SERVICE_NAME_PREFIX = "Launotor-"
        private const val MAX_UDP_RECEIVE_BYTES = 512
        private const val FOREGROUND_HEARTBEAT_MS = 12_000L
        private const val BACKGROUND_HEARTBEAT_MS = 30_000L
        private const val LEARNED_PEER_TTL_MS = 120_000L
    }
}
