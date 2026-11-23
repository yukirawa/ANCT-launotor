package jp.yukirawa.launotor.notifications

import jp.yukirawa.launotor.domain.model.AppSettings
import jp.yukirawa.launotor.domain.model.MachineStatus
import jp.yukirawa.launotor.domain.model.MachineTimerRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class TimerNotificationPlannerTest {

    @Test
    fun scheduledAlarmsSkipAlmostDoneWhenInitialDurationAtOrBelowThreshold() {
        val now = 10_000_000L
        val records = mapOf(
            "washer_1" to MachineTimerRecord(
                machineId = "washer_1",
                initialDurationMillis = 30 * 60_000L,
                endTimeEpochMillis = now + 30 * 60_000L,
            ),
            "dryer_4" to MachineTimerRecord(
                machineId = "dryer_4",
                initialDurationMillis = 5 * 60_000L,
                endTimeEpochMillis = now + 5 * 60_000L,
            ),
        )

        val alarms = TimerNotificationPlanner.computeScheduledAlarms(
            records = records,
            settings = AppSettings(almostDoneThresholdMinutes = 5),
            nowEpochMillis = now,
        )

        val washer1Alarms = alarms.filter { it.machineId == "washer_1" }
        assertEquals(2, washer1Alarms.size)
        assertEquals(now + 25 * 60_000L, washer1Alarms.first { it.kind == AlarmKind.ALMOST_DONE }.triggerAtEpochMillis)
        assertEquals(now + 30 * 60_000L, washer1Alarms.first { it.kind == AlarmKind.FINISHED }.triggerAtEpochMillis)

        val dryer4Alarms = alarms.filter { it.machineId == "dryer_4" }
        assertEquals(1, dryer4Alarms.size)
        assertEquals(AlarmKind.FINISHED, dryer4Alarms.single().kind)
    }

    @Test
    fun dueNotificationsDeduplicateByEndTime() {
        val now = 10_000_000L
        val endTime = now + 4 * 60_000L
        val record = MachineTimerRecord(
            machineId = "washer_2",
            initialDurationMillis = 30 * 60_000L,
            endTimeEpochMillis = endTime,
        )

        val firstPass = TimerNotificationPlanner.computeDueNotifications(
            records = mapOf("washer_2" to record),
            settings = AppSettings(almostDoneThresholdMinutes = 5),
            nowEpochMillis = now,
        )
        assertEquals(1, firstPass.size)
        assertEquals(AlarmKind.ALMOST_DONE, firstPass.single().kind)
        assertTrue(firstPass.single().title.contains("洗濯機2"))

        val afterAlmostDoneMarked = record.copy(almostDoneNotifiedForEndTime = endTime)
        val secondPass = TimerNotificationPlanner.computeDueNotifications(
            records = mapOf("washer_2" to afterAlmostDoneMarked),
            settings = AppSettings(almostDoneThresholdMinutes = 5),
            nowEpochMillis = now + 60_000L,
        )
        assertTrue(secondPass.isEmpty())

        val atFinish = TimerNotificationPlanner.computeDueNotifications(
            records = mapOf("washer_2" to afterAlmostDoneMarked),
            settings = AppSettings(almostDoneThresholdMinutes = 5),
            nowEpochMillis = endTime,
        )
        assertEquals(1, atFinish.size)
        assertEquals(AlarmKind.FINISHED, atFinish.single().kind)
        assertTrue(atFinish.single().title.contains("洗濯機2が空きました"))

        val afterFinishedMarked = afterAlmostDoneMarked.copy(finishedNotifiedForEndTime = endTime)
        val postFinish = TimerNotificationPlanner.computeDueNotifications(
            records = mapOf("washer_2" to afterFinishedMarked),
            settings = AppSettings(almostDoneThresholdMinutes = 5),
            nowEpochMillis = endTime + 60_000L,
        )
        assertTrue(postFinish.isEmpty())
    }

    @Test
    fun liveUpdateModelPicksSoonestFinishingMachineAndBuildsProgress() {
        val now = 1_700_000_000_000L
        val records = mapOf(
            "washer_1" to MachineTimerRecord(
                machineId = "washer_1",
                initialDurationMillis = 30 * 60_000L,
                endTimeEpochMillis = now + 15 * 60_000L,
            ),
            "dryer_4" to MachineTimerRecord(
                machineId = "dryer_4",
                initialDurationMillis = 20 * 60_000L,
                endTimeEpochMillis = now + 4 * 60_000L,
            ),
            "washer_8" to MachineTimerRecord(
                machineId = "washer_8",
                initialDurationMillis = 10 * 60_000L,
                endTimeEpochMillis = now - 60_000L,
            ),
        )

        val model = TimerNotificationPlanner.buildLiveUpdateModel(
            records = records,
            settings = AppSettings(almostDoneThresholdMinutes = 5),
            nowEpochMillis = now,
            zoneId = ZoneOffset.UTC,
        )

        assertNotNull(model)
        model!!
        assertEquals("dryer_4", model.primaryMachine.id)
        assertEquals(MachineStatus.ALMOST_DONE, model.primaryStatus)
        assertEquals("乾4 4分", model.shortCriticalText)
        assertTrue(model.contentTitle.contains("乾燥機 4"))
        assertTrue(model.contentTitle.contains("他1台"))
        assertEquals(800, model.progress)
        assertEquals(750, model.almostDonePointPosition)
        assertEquals(2, model.activeCount)
        assertEquals(1, model.finishedCount)
    }

    @Test
    fun liveUpdateModelReturnsNullWhenNoMachinesRunning() {
        val now = 1_700_000_000_000L
        val records = mapOf(
            "washer_1" to MachineTimerRecord(
                machineId = "washer_1",
                initialDurationMillis = 10 * 60_000L,
                endTimeEpochMillis = now - 1000L,
            ),
        )
        val model = TimerNotificationPlanner.buildLiveUpdateModel(
            records = records,
            settings = AppSettings(),
            nowEpochMillis = now,
        )
        assertNull(model)
    }
}
