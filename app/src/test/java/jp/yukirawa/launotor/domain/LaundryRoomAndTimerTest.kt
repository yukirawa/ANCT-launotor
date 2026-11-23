package jp.yukirawa.launotor.domain

import jp.yukirawa.launotor.domain.model.LaundryRoomCatalog
import jp.yukirawa.launotor.domain.model.MachineStatus
import jp.yukirawa.launotor.domain.model.MachineTimerCalculator
import jp.yukirawa.launotor.domain.model.MachineTimerRecord
import jp.yukirawa.launotor.domain.model.MachineType
import jp.yukirawa.launotor.domain.model.RoomZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaundryRoomAndTimerTest {

    @Test
    fun catalogHasExact14MachinesInPhysicalLayout() {
        assertEquals(14, LaundryRoomCatalog.allMachines.size)
        assertEquals(14, LaundryRoomCatalog.allMachines.map { it.id }.toSet().size)

        assertEquals(listOf(1, 2, 3, 4), LaundryRoomCatalog.leftWashers.map { it.number })
        assertTrue(LaundryRoomCatalog.leftWashers.all {
            it.type == MachineType.WASHER && it.zone == RoomZone.LEFT_WALL
        })

        assertEquals(listOf(1, 2, 3), LaundryRoomCatalog.centerLeftDryers.map { it.number })
        assertTrue(LaundryRoomCatalog.centerLeftDryers.all {
            it.type == MachineType.DRYER && it.zone == RoomZone.CENTER_LEFT
        })

        assertEquals(listOf(4, 5, 6), LaundryRoomCatalog.centerRightDryers.map { it.number })
        assertTrue(LaundryRoomCatalog.centerRightDryers.all {
            it.type == MachineType.DRYER && it.zone == RoomZone.CENTER_RIGHT
        })

        assertEquals(listOf(5, 6, 7, 8), LaundryRoomCatalog.rightWashers.map { it.number })
        assertTrue(LaundryRoomCatalog.rightWashers.all {
            it.type == MachineType.WASHER && it.zone == RoomZone.RIGHT_WALL
        })
    }

    @Test
    fun computeStateTransitionsAcrossLifecycle() {
        val spec = LaundryRoomCatalog.findById("washer_1")!!
        val start = 1_000_000L
        val record = MachineTimerRecord(
            machineId = spec.id,
            initialDurationMillis = 30 * 60_000L,
            endTimeEpochMillis = start + 30 * 60_000L,
            updatedAtEpochMillis = start,
        )

        val idle = MachineTimerCalculator.computeState(spec, null, start, 5)
        assertEquals(MachineStatus.IDLE, idle.status)
        assertEquals(0L, idle.remainingMillis)

        val running = MachineTimerCalculator.computeState(
            spec = spec,
            record = record,
            nowEpochMillis = start + 15 * 60_000L,
            almostDoneThresholdMinutes = 5,
        )
        assertEquals(MachineStatus.RUNNING, running.status)
        assertEquals(15 * 60_000L, running.remainingMillis)
        assertEquals(0.5f, running.elapsedFraction, 0.001f)

        val almostDone = MachineTimerCalculator.computeState(
            spec = spec,
            record = record,
            nowEpochMillis = start + 26 * 60_000L,
            almostDoneThresholdMinutes = 5,
        )
        assertEquals(MachineStatus.ALMOST_DONE, almostDone.status)
        assertEquals(4 * 60_000L, almostDone.remainingMillis)

        val thresholdOff = MachineTimerCalculator.computeState(
            spec = spec,
            record = record,
            nowEpochMillis = start + 29 * 60_000L,
            almostDoneThresholdMinutes = 0,
        )
        assertEquals(MachineStatus.RUNNING, thresholdOff.status)

        val finished = MachineTimerCalculator.computeState(
            spec = spec,
            record = record,
            nowEpochMillis = start + 31 * 60_000L,
            almostDoneThresholdMinutes = 5,
        )
        assertEquals(MachineStatus.FINISHED, finished.status)
        assertEquals(0L, finished.remainingMillis)
        assertEquals(1f, finished.elapsedFraction, 0.001f)
    }

    @Test
    fun formatCountdownHandlesMinutesAndHours() {
        assertEquals("00:00", MachineTimerCalculator.formatCountdown(0L))
        assertEquals("04:59", MachineTimerCalculator.formatCountdown(4 * 60_000L + 58_200L))
        assertEquals("1:05:00", MachineTimerCalculator.formatCountdown(65 * 60_000L))
    }

    @Test
    fun formatCompactRemainingUsesMinutesAndSecondsCleanly() {
        assertEquals("0秒", MachineTimerCalculator.formatCompactRemaining(0L))
        assertEquals("45秒", MachineTimerCalculator.formatCompactRemaining(44_500L))
        assertEquals("1分", MachineTimerCalculator.formatCompactRemaining(59_200L))
        assertEquals("24分", MachineTimerCalculator.formatCompactRemaining(24 * 60_000L))
        assertEquals("65分", MachineTimerCalculator.formatCompactRemaining(65 * 60_000L))
    }
}
