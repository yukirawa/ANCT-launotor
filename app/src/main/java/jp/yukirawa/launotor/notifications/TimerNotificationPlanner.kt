package jp.yukirawa.launotor.notifications

import jp.yukirawa.launotor.domain.model.AppSettings
import jp.yukirawa.launotor.domain.model.LaundryRoomCatalog
import jp.yukirawa.launotor.domain.model.MachineSpec
import jp.yukirawa.launotor.domain.model.MachineState
import jp.yukirawa.launotor.domain.model.MachineStatus
import jp.yukirawa.launotor.domain.model.MachineTimerCalculator
import jp.yukirawa.launotor.domain.model.MachineTimerRecord
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class AlarmKind {
    ALMOST_DONE,
    FINISHED,
}

data class ScheduledAlarmSpec(
    val machineId: String,
    val kind: AlarmKind,
    val triggerAtEpochMillis: Long,
    val expectedEndTimeEpochMillis: Long,
)

data class DueNotificationSpec(
    val machine: MachineSpec,
    val kind: AlarmKind,
    val endTimeEpochMillis: Long,
    val remainingMillis: Long,
    val title: String,
    val body: String,
)

data class LiveUpdateRenderModel(
    val primaryMachine: MachineSpec,
    val primaryStatus: MachineStatus,
    val shortCriticalText: String,
    val contentTitle: String,
    val contentText: String,
    val subText: String?,
    val progress: Int,
    val maxProgress: Int,
    val almostDonePointPosition: Int?,
    val activeCount: Int,
    val finishedCount: Int,
)

object TimerNotificationPlanner {
    const val PROGRESS_MAX = 1000
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun computeScheduledAlarms(
        records: Map<String, MachineTimerRecord>,
        settings: AppSettings,
        nowEpochMillis: Long,
    ): List<ScheduledAlarmSpec> {
        val thresholdMillis = settings.almostDoneThresholdMillis
        val result = mutableListOf<ScheduledAlarmSpec>()

        for (spec in LaundryRoomCatalog.allMachines) {
            val record = records[spec.id] ?: continue
            if (!record.hasActiveSchedule) continue

            if (
                thresholdMillis > 0L &&
                record.initialDurationMillis > thresholdMillis &&
                record.almostDoneNotifiedForEndTime != record.endTimeEpochMillis
            ) {
                val almostDoneAt = record.endTimeEpochMillis - thresholdMillis
                if (almostDoneAt > nowEpochMillis) {
                    result += ScheduledAlarmSpec(
                        machineId = spec.id,
                        kind = AlarmKind.ALMOST_DONE,
                        triggerAtEpochMillis = almostDoneAt,
                        expectedEndTimeEpochMillis = record.endTimeEpochMillis,
                    )
                }
            }

            if (
                record.endTimeEpochMillis > nowEpochMillis &&
                record.finishedNotifiedForEndTime != record.endTimeEpochMillis
            ) {
                result += ScheduledAlarmSpec(
                    machineId = spec.id,
                    kind = AlarmKind.FINISHED,
                    triggerAtEpochMillis = record.endTimeEpochMillis,
                    expectedEndTimeEpochMillis = record.endTimeEpochMillis,
                )
            }
        }
        return result
    }

    fun computeDueNotifications(
        records: Map<String, MachineTimerRecord>,
        settings: AppSettings,
        nowEpochMillis: Long,
    ): List<DueNotificationSpec> {
        val thresholdMillis = settings.almostDoneThresholdMillis
        val due = mutableListOf<DueNotificationSpec>()

        for (spec in LaundryRoomCatalog.allMachines) {
            val record = records[spec.id] ?: continue
            if (!record.hasActiveSchedule) continue

            val remaining = record.endTimeEpochMillis - nowEpochMillis
            if (remaining <= 0L) {
                if (record.finishedNotifiedForEndTime != record.endTimeEpochMillis) {
                    due += DueNotificationSpec(
                        machine = spec,
                        kind = AlarmKind.FINISHED,
                        endTimeEpochMillis = record.endTimeEpochMillis,
                        remainingMillis = 0L,
                        title = "${spec.fullNameNoSpace}が空きました！",
                        body = "運転が終了しました。取り出し後に「空きにする」をタップしてください。",
                    )
                }
            } else if (
                thresholdMillis > 0L &&
                record.initialDurationMillis > thresholdMillis &&
                remaining <= thresholdMillis &&
                record.almostDoneNotifiedForEndTime != record.endTimeEpochMillis
            ) {
                val mins = ceil(remaining / 60_000.0).toInt().coerceAtLeast(1)
                due += DueNotificationSpec(
                    machine = spec,
                    kind = AlarmKind.ALMOST_DONE,
                    endTimeEpochMillis = record.endTimeEpochMillis,
                    remainingMillis = remaining,
                    title = "${spec.fullNameNoSpace}が空きそうです（残り${mins}分）",
                    body = "まもなく終了予定です。終了時刻 ${formatEpochTime(record.endTimeEpochMillis)}",
                )
            }
        }
        return due
    }

    fun hasRunningTimers(
        records: Map<String, MachineTimerRecord>,
        nowEpochMillis: Long,
    ): Boolean {
        return records.values.any { it.hasActiveSchedule && it.endTimeEpochMillis > nowEpochMillis }
    }

    fun buildLiveUpdateModel(
        records: Map<String, MachineTimerRecord>,
        settings: AppSettings,
        nowEpochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): LiveUpdateRenderModel? {
        val states = LaundryRoomCatalog.allMachines.map { spec ->
            MachineTimerCalculator.computeState(
                spec = spec,
                record = records[spec.id],
                nowEpochMillis = nowEpochMillis,
                almostDoneThresholdMinutes = settings.almostDoneThresholdMinutes,
            )
        }

        val activeStates = states
            .filter { it.status == MachineStatus.RUNNING || it.status == MachineStatus.ALMOST_DONE }
            .sortedBy { it.remainingMillis }
        val finishedStates = states.filter { it.status == MachineStatus.FINISHED }

        if (activeStates.isEmpty()) {
            return null
        }

        val primary = activeStates.first()
        val remainingMins = primary.remainingWholeMinutesCeil
        val shortChip = "${primary.spec.compactName} ${remainingMins}分"

        val othersCount = activeStates.size - 1
        val title = if (othersCount > 0) {
            "${primary.spec.fullName} 残り${remainingMins}分（他${othersCount}台監視中）"
        } else {
            "${primary.spec.fullName} 稼働中（残り${remainingMins}分）"
        }

        val endFormatted = formatEpochTime(primary.record.endTimeEpochMillis, zoneId)
        val otherSummaries = activeStates.drop(1).take(2).joinToString(" / ") { state ->
            "${state.spec.compactName} あと${state.remainingWholeMinutesCeil}分"
        }
        val extraMore = (activeStates.size - 3).coerceAtLeast(0)
        val detailParts = buildList {
            add("終了予定 $endFormatted")
            if (otherSummaries.isNotEmpty()) {
                val suffix = if (extraMore > 0) " 他${extraMore}台" else ""
                add("$otherSummaries$suffix")
            }
            if (finishedStates.isNotEmpty()) {
                add("完了 ${finishedStates.size}台")
            }
        }

        val progress = (primary.elapsedFraction * PROGRESS_MAX).roundToInt().coerceIn(0, PROGRESS_MAX)
        val thresholdMillis = settings.almostDoneThresholdMillis
        val pointPos = if (
            thresholdMillis > 0L &&
            primary.record.initialDurationMillis > thresholdMillis
        ) {
            val triggerFraction =
                (primary.record.initialDurationMillis - thresholdMillis).toFloat() /
                    primary.record.initialDurationMillis.toFloat()
            (triggerFraction * PROGRESS_MAX).roundToInt().coerceIn(1, PROGRESS_MAX - 1)
        } else {
            null
        }

        return LiveUpdateRenderModel(
            primaryMachine = primary.spec,
            primaryStatus = primary.status,
            shortCriticalText = shortChip,
            contentTitle = title,
            contentText = detailParts.joinToString(" • "),
            subText = "稼働 ${activeStates.size}台",
            progress = progress,
            maxProgress = PROGRESS_MAX,
            almostDonePointPosition = pointPos,
            activeCount = activeStates.size,
            finishedCount = finishedStates.size,
        )
    }

    fun formatEpochTime(
        epochMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(timeFormatter)
    }
}
