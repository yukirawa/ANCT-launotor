package jp.yukirawa.launotor.domain.model

import java.util.Locale
import kotlin.math.ceil

enum class MachineType(
    val displayPrefix: String,
    val compactPrefix: String,
    val presetMinutes: List<Int>,
) {
    WASHER(
        displayPrefix = "洗濯機",
        compactPrefix = "洗",
        presetMinutes = listOf(5, 10, 15, 20, 30, 40, 45),
    ),
    DRYER(
        displayPrefix = "乾燥機",
        compactPrefix = "乾",
        presetMinutes = listOf(5, 10, 15, 20, 30, 45, 60),
    ),
}

enum class RoomZone {
    LEFT_WALL,
    CENTER_LEFT,
    CENTER_RIGHT,
    RIGHT_WALL,
}

enum class UpdateSource {
    LOCAL,
    P2P_BLE,
    P2P_LAN,
}

enum class MachineStatus {
    IDLE,
    RUNNING,
    ALMOST_DONE,
    FINISHED,
}

data class MachineSpec(
    val id: String,
    val type: MachineType,
    val number: Int,
    val zone: RoomZone,
    val slotIndex: Int,
) {
    val fullName: String get() = "${type.displayPrefix} $number"
    val fullNameNoSpace: String get() = "${type.displayPrefix}$number"
    val compactName: String get() = "${type.compactPrefix}$number"
}

object LaundryRoomCatalog {
    const val MIN_DURATION_MINUTES = 1
    const val SLIDER_MAX_DURATION_MINUTES = 90
    const val MAX_DURATION_MINUTES = 180
    const val TOTAL_WASHERS = 8
    const val TOTAL_DRYERS = 6
    const val TOTAL_MACHINES = TOTAL_WASHERS + TOTAL_DRYERS

    val leftWashers: List<MachineSpec> = (1..4).mapIndexed { index, num ->
        MachineSpec(
            id = "washer_$num",
            type = MachineType.WASHER,
            number = num,
            zone = RoomZone.LEFT_WALL,
            slotIndex = index,
        )
    }

    val centerLeftDryers: List<MachineSpec> = (1..3).mapIndexed { index, num ->
        MachineSpec(
            id = "dryer_$num",
            type = MachineType.DRYER,
            number = num,
            zone = RoomZone.CENTER_LEFT,
            slotIndex = index,
        )
    }

    val centerRightDryers: List<MachineSpec> = (4..6).mapIndexed { index, num ->
        MachineSpec(
            id = "dryer_$num",
            type = MachineType.DRYER,
            number = num,
            zone = RoomZone.CENTER_RIGHT,
            slotIndex = index,
        )
    }

    val rightWashers: List<MachineSpec> = (5..8).mapIndexed { index, num ->
        MachineSpec(
            id = "washer_$num",
            type = MachineType.WASHER,
            number = num,
            zone = RoomZone.RIGHT_WALL,
            slotIndex = index,
        )
    }

    val allMachines: List<MachineSpec> =
        leftWashers + centerLeftDryers + centerRightDryers + rightWashers

    private val byId: Map<String, MachineSpec> = allMachines.associateBy { it.id }

    fun findById(id: String): MachineSpec? = byId[id]
}

data class MachineTimerRecord(
    val machineId: String,
    val initialDurationMillis: Long = 0L,
    val endTimeEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
    val updateSource: UpdateSource = UpdateSource.LOCAL,
    val almostDoneNotifiedForEndTime: Long = 0L,
    val finishedNotifiedForEndTime: Long = 0L,
    val isWatchedByMe: Boolean = false,
    val originHash: Int = 0,
) {
    val hasActiveSchedule: Boolean
        get() = initialDurationMillis > 0L && endTimeEpochMillis > 0L
}

data class AppSettings(
    val almostDoneThresholdMinutes: Int = DEFAULT_ALMOST_DONE_THRESHOLD_MINUTES,
    val hapticsEnabled: Boolean = true,
    val liveUpdatesEnabled: Boolean = true,
    val p2pSyncEnabled: Boolean = true,
    val backgroundP2pEnabled: Boolean = false,
) {
    val almostDoneThresholdMillis: Long
        get() = almostDoneThresholdMinutes.coerceAtLeast(0) * 60_000L

    companion object {
        const val DEFAULT_ALMOST_DONE_THRESHOLD_MINUTES = 5
        val THRESHOLD_OPTIONS_MINUTES = listOf(0, 3, 5, 10)
    }
}

data class MachineState(
    val spec: MachineSpec,
    val record: MachineTimerRecord,
    val status: MachineStatus,
    val remainingMillis: Long,
    val elapsedFraction: Float,
) {
    val remainingWholeMinutesCeil: Int
        get() = if (remainingMillis <= 0L) 0 else ceil(remainingMillis / 60_000.0).toInt().coerceAtLeast(1)
}

object MachineTimerCalculator {
    fun computeState(
        spec: MachineSpec,
        record: MachineTimerRecord?,
        nowEpochMillis: Long,
        almostDoneThresholdMinutes: Int,
    ): MachineState {
        val safeRecord = record ?: MachineTimerRecord(machineId = spec.id)
        if (!safeRecord.hasActiveSchedule) {
            return MachineState(
                spec = spec,
                record = safeRecord,
                status = MachineStatus.IDLE,
                remainingMillis = 0L,
                elapsedFraction = 0f,
            )
        }

        val remainingMillis = (safeRecord.endTimeEpochMillis - nowEpochMillis).coerceAtLeast(0L)
        val thresholdMillis = almostDoneThresholdMinutes.coerceAtLeast(0) * 60_000L

        val status = when {
            remainingMillis <= 0L -> MachineStatus.FINISHED
            thresholdMillis > 0L && remainingMillis <= thresholdMillis -> MachineStatus.ALMOST_DONE
            else -> MachineStatus.RUNNING
        }

        val elapsedMillis = (safeRecord.initialDurationMillis - remainingMillis)
            .coerceIn(0L, safeRecord.initialDurationMillis)
        val elapsedFraction = if (safeRecord.initialDurationMillis > 0L) {
            (elapsedMillis.toFloat() / safeRecord.initialDurationMillis.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        return MachineState(
            spec = spec,
            record = safeRecord,
            status = status,
            remainingMillis = remainingMillis,
            elapsedFraction = elapsedFraction,
        )
    }

    fun formatCountdown(remainingMillis: Long): String {
        if (remainingMillis <= 0L) return "00:00"
        val totalSeconds = ceil(remainingMillis / 1000.0).toLong()
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatMinutesCeil(remainingMillis: Long): String {
        if (remainingMillis <= 0L) return "0分"
        val mins = ceil(remainingMillis / 60_000.0).toInt().coerceAtLeast(1)
        return "${mins}分"
    }

    fun formatCompactRemaining(remainingMillis: Long): String {
        if (remainingMillis <= 0L) return "0秒"
        val totalSeconds = ceil(remainingMillis / 1000.0).toLong()
        return if (totalSeconds >= 60L) {
            val mins = ceil(remainingMillis / 60_000.0).toLong().coerceAtLeast(1L)
            "${mins}分"
        } else {
            "${totalSeconds}秒"
        }
    }
}
