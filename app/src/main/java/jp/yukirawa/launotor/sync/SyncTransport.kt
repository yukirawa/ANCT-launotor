package jp.yukirawa.launotor.sync

import jp.yukirawa.launotor.domain.model.MachineTimerRecord
import jp.yukirawa.launotor.domain.model.UpdateSource

/**
 * Packet received from a P2P transport along with its transport source.
 */
data class ReceivedSyncPacket(
    val packet: SyncPacket,
    val source: UpdateSource,
)

/**
 * Common lifecycle contract for P2P sync transports (BLE Gossip & Wi-Fi LAN).
 */
interface SyncTransport {
    val source: UpdateSource

    fun start(onPacketReceived: (ReceivedSyncPacket) -> Unit)

    fun updatePayload(records: Map<String, MachineTimerRecord>, nowEpochMillis: Long)

    fun broadcastNow(records: Map<String, MachineTimerRecord>, nowEpochMillis: Long)

    fun setPowerMode(foreground: Boolean)

    fun stop()
}
