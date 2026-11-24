package jp.yukirawa.launotor.sync

import jp.yukirawa.launotor.domain.model.LaundryRoomCatalog
import jp.yukirawa.launotor.domain.model.MachineTimerRecord

/**
 * Compact binary codec for Launotor hybrid P2P sync:
 * - Header (3 bytes): [version: uint8 = 0x01][senderShortId: uint16 BE]
 * - Entry (7 bytes each):
 *   - [0]: machineIndex (bits 0..3) | isActive (bit 4) | isClearedTombstone (bit 5)
 *   - [1..2]: remainingSeconds (uint16 BE)
 *   - [3]: initialDurationMinutes (uint8)
 *   - [4..5]: ageSeconds (uint16 BE)
 *   - [6]: originHash (uint8)
 *
 * Full 14-machine snapshot = 3 + 14 * 7 = 101 bytes (for LAN UDP).
 * Legacy BLE frame = 3 + (1..3) * 7 = 10..24 bytes (fits within 31-byte legacy ADV payload).
 */
object SyncPayloadCodec {
    const val PROTOCOL_VERSION = 0x01
    const val HEADER_SIZE = 3
    const val ENTRY_SIZE = 7
    const val MAX_LAN_ENTRIES = LaundryRoomCatalog.TOTAL_MACHINES
    const val MAX_BLE_ENTRIES = 3
    const val FULL_LAN_PACKET_SIZE = HEADER_SIZE + MAX_LAN_ENTRIES * ENTRY_SIZE // 101 bytes
    const val MAX_BLE_FRAME_SIZE = HEADER_SIZE + MAX_BLE_ENTRIES * ENTRY_SIZE // 24 bytes

    const val FLAG_IS_ACTIVE = 0x10
    const val FLAG_IS_CLEARED_TOMBSTONE = 0x20
    const val MACHINE_INDEX_MASK = 0x0F

    const val TOMBSTONE_MAX_AGE_SECONDS = 6 * 3600 // 6 hours
    const val EXPIRED_FINISHED_MAX_AGE_SECONDS = 6 * 3600 // 6 hours
    const val RECENT_TOMBSTONE_PRIORITY_SECONDS = 300 // 5 minutes

    fun encode(packet: SyncPacket): ByteArray {
        val entries = packet.entries.take(MAX_LAN_ENTRIES)
        val out = ByteArray(HEADER_SIZE + entries.size * ENTRY_SIZE)
        out[0] = (packet.version and 0xFF).toByte()
        out[1] = ((packet.senderShortId ushr 8) and 0xFF).toByte()
        out[2] = (packet.senderShortId and 0xFF).toByte()

        var offset = HEADER_SIZE
        for (entry in entries) {
            val idx = entry.machineIndex.coerceIn(0, MAX_LAN_ENTRIES - 1) and MACHINE_INDEX_MASK
            val activeBit = if (entry.isActive) FLAG_IS_ACTIVE else 0
            val tombstoneBit = if (entry.isClearedTombstone) FLAG_IS_CLEARED_TOMBSTONE else 0
            out[offset] = (idx or activeBit or tombstoneBit).toByte()

            val remSec = entry.remainingSeconds.coerceIn(0, 0xFFFF)
            out[offset + 1] = ((remSec ushr 8) and 0xFF).toByte()
            out[offset + 2] = (remSec and 0xFF).toByte()

            out[offset + 3] = entry.initialDurationMinutes.coerceIn(0, 0xFF).toByte()

            val ageSec = entry.ageSeconds.coerceIn(0, 0xFFFF)
            out[offset + 4] = ((ageSec ushr 8) and 0xFF).toByte()
            out[offset + 5] = (ageSec and 0xFF).toByte()

            out[offset + 6] = (entry.originHash and 0xFF).toByte()
            offset += ENTRY_SIZE
        }
        return out
    }

    fun decode(bytes: ByteArray): SyncPacket? {
        if (bytes.size < HEADER_SIZE) return null
        val version = bytes[0].toInt() and 0xFF
        if (version != PROTOCOL_VERSION) return null

        val payloadSize = bytes.size - HEADER_SIZE
        if (payloadSize % ENTRY_SIZE != 0) return null
        val entryCount = payloadSize / ENTRY_SIZE
        if (entryCount > MAX_LAN_ENTRIES) return null

        val senderShortId = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[2].toInt() and 0xFF)
        val entries = ArrayList<SyncEntry>(entryCount)

        var offset = HEADER_SIZE
        for (i in 0 until entryCount) {
            val b0 = bytes[offset].toInt() and 0xFF
            val machineIndex = b0 and MACHINE_INDEX_MASK
            if (machineIndex >= LaundryRoomCatalog.TOTAL_MACHINES) {
                return null
            }
            val isActive = (b0 and FLAG_IS_ACTIVE) != 0
            val isClearedTombstone = (b0 and FLAG_IS_CLEARED_TOMBSTONE) != 0
            val remainingSeconds =
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or (bytes[offset + 2].toInt() and 0xFF)
            val initialDurationMinutes = bytes[offset + 3].toInt() and 0xFF
            val ageSeconds =
                ((bytes[offset + 4].toInt() and 0xFF) shl 8) or (bytes[offset + 5].toInt() and 0xFF)
            val originHash = bytes[offset + 6].toInt() and 0xFF

            entries += SyncEntry(
                machineIndex = machineIndex,
                isActive = isActive,
                isClearedTombstone = isClearedTombstone,
                remainingSeconds = remainingSeconds,
                initialDurationMinutes = initialDurationMinutes,
                ageSeconds = ageSeconds,
                originHash = originHash,
            )
            offset += ENTRY_SIZE
        }

        return SyncPacket(
            version = version,
            senderShortId = senderShortId,
            entries = entries,
        )
    }

    /**
     * Converts local machine records to a full 14-machine [SyncPacket] (101 bytes when encoded)
     * suitable for LAN UDP broadcast/unicast.
     */
    fun buildLanSnapshotPacket(
        records: Map<String, MachineTimerRecord>,
        senderShortId: Int,
        nowEpochMillis: Long,
    ): SyncPacket {
        val entries = LaundryRoomCatalog.allMachines.mapIndexed { index, spec ->
            toSyncEntry(index, records[spec.id], nowEpochMillis)
        }
        return SyncPacket(
            version = PROTOCOL_VERSION,
            senderShortId = senderShortId and 0xFFFF,
            entries = entries,
        )
    }

    /**
     * Splits active and non-expired tombstone entries into legacy BLE 24-byte rotating frames
     * (up to 3 entries per frame), prioritizing running machines and recent tombstones.
     * Returns an empty list if all machines are idle with no recent tombstones.
     */
    fun buildBleFrames(
        records: Map<String, MachineTimerRecord>,
        senderShortId: Int,
        nowEpochMillis: Long,
    ): List<ByteArray> {
        val syncable = LaundryRoomCatalog.allMachines.mapIndexedNotNull { index, spec ->
            val entry = toSyncEntry(index, records[spec.id], nowEpochMillis)
            val include = when {
                entry.isActive -> {
                    entry.remainingSeconds > 0 || entry.ageSeconds <= EXPIRED_FINISHED_MAX_AGE_SECONDS
                }
                entry.isClearedTombstone -> entry.ageSeconds <= TOMBSTONE_MAX_AGE_SECONDS
                else -> false
            }
            if (include) entry else null
        }

        if (syncable.isEmpty()) return emptyList()

        val prioritized = syncable.sortedWith(
            compareBy<SyncEntry>(
                { priorityTier(it) },
                { it.ageSeconds },
                { it.machineIndex },
            ),
        )

        return prioritized
            .chunked(MAX_BLE_ENTRIES)
            .map { chunk ->
                encode(
                    SyncPacket(
                        version = PROTOCOL_VERSION,
                        senderShortId = senderShortId and 0xFFFF,
                        entries = chunk,
                    ),
                )
            }
    }

    private fun priorityTier(entry: SyncEntry): Int {
        return when {
            entry.isActive && entry.remainingSeconds > 0 -> 0
            entry.isClearedTombstone && entry.ageSeconds <= RECENT_TOMBSTONE_PRIORITY_SECONDS -> 0
            entry.isActive -> 1
            else -> 2
        }
    }

    private fun toSyncEntry(
        machineIndex: Int,
        record: MachineTimerRecord?,
        nowEpochMillis: Long,
    ): SyncEntry {
        if (record == null || (!record.hasActiveSchedule && record.updatedAtEpochMillis <= 0L)) {
            return SyncEntry(
                machineIndex = machineIndex,
                isActive = false,
                isClearedTombstone = false,
                remainingSeconds = 0,
                initialDurationMinutes = 0,
                ageSeconds = 0xFFFF,
                originHash = 0,
            )
        }

        val ageSeconds = ((nowEpochMillis - record.updatedAtEpochMillis).coerceAtLeast(0L) / 1000L)
            .toInt()
            .coerceIn(0, 0xFFFF)

        if (record.hasActiveSchedule) {
            val remainingMs = (record.endTimeEpochMillis - nowEpochMillis).coerceAtLeast(0L)
            val remainingSeconds = if (remainingMs == 0L) {
                0
            } else {
                ((remainingMs + 999L) / 1000L).toInt().coerceIn(1, 0xFFFF)
            }
            val initialMinutes = (record.initialDurationMillis / 60_000L)
                .toInt()
                .coerceIn(LaundryRoomCatalog.MIN_DURATION_MINUTES, LaundryRoomCatalog.MAX_DURATION_MINUTES)

            return SyncEntry(
                machineIndex = machineIndex,
                isActive = true,
                isClearedTombstone = false,
                remainingSeconds = remainingSeconds,
                initialDurationMinutes = initialMinutes,
                ageSeconds = ageSeconds,
                originHash = record.originHash and 0xFF,
            )
        }

        // Cleared tombstone
        return SyncEntry(
            machineIndex = machineIndex,
            isActive = false,
            isClearedTombstone = true,
            remainingSeconds = 0,
            initialDurationMinutes = 0,
            ageSeconds = ageSeconds,
            originHash = record.originHash and 0xFF,
        )
    }
}
