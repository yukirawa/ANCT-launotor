package jp.yukirawa.launotor.sync

import android.content.Context
import androidx.core.content.edit
import java.security.SecureRandom

/**
 * Compact wire representation of a single laundry machine state.
 */
data class SyncEntry(
    val machineIndex: Int,
    val isActive: Boolean,
    val isClearedTombstone: Boolean,
    val remainingSeconds: Int,
    val initialDurationMinutes: Int,
    val ageSeconds: Int,
    val originHash: Int,
)

/**
 * Decoded binary P2P sync packet carrying a 16-bit sender identifier and 0..14 machine entries.
 */
data class SyncPacket(
    val version: Int = SyncPayloadCodec.PROTOCOL_VERSION,
    val senderShortId: Int,
    val entries: List<SyncEntry>,
)

/**
 * Provides persistent per-install random identifiers for P2P gossip loop prevention
 * (`senderShortId`) and deterministic simultaneous-write tie-breaking (`originHash`).
 */
class SyncDeviceIdProvider(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lock = Any()

    @Volatile
    private var cachedSenderShortId: Int = -1

    @Volatile
    private var cachedOriginHash: Int = -1

    val senderShortId: Int
        get() {
            val cached = cachedSenderShortId
            if (cached >= 0) return cached
            return synchronized(lock) {
                if (cachedSenderShortId >= 0) return@synchronized cachedSenderShortId
                val existing = prefs.getInt(KEY_SENDER_SHORT_ID, -1)
                val resolved = if (existing in 1..0xFFFF) {
                    existing
                } else {
                    (SecureRandom().nextInt(0xFFFE) + 1).also { generated ->
                        prefs.edit { putInt(KEY_SENDER_SHORT_ID, generated) }
                    }
                }
                cachedSenderShortId = resolved
                resolved
            }
        }

    val originHash: Int
        get() {
            val cached = cachedOriginHash
            if (cached >= 0) return cached
            return synchronized(lock) {
                if (cachedOriginHash >= 0) return@synchronized cachedOriginHash
                val existing = prefs.getInt(KEY_ORIGIN_HASH, -1)
                val resolved = if (existing in 0..0xFF) {
                    existing
                } else {
                    SecureRandom().nextInt(0x100).also { generated ->
                        prefs.edit { putInt(KEY_ORIGIN_HASH, generated) }
                    }
                }
                cachedOriginHash = resolved
                resolved
            }
        }

    private companion object {
        const val PREFS_NAME = "launotor_sync_device_ids"
        const val KEY_SENDER_SHORT_ID = "sender_short_id"
        const val KEY_ORIGIN_HASH = "origin_hash"
    }
}
