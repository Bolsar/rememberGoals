package com.bolsar.remembergoals

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class GoalTest {
    private val utc = ZoneOffset.UTC
    private fun millis(s: String, zone: ZoneId = utc) = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()

    @Test fun days() = assertEquals(millis("2026-01-11T10:00"), deadlineFrom(millis("2026-01-01T10:00"), 10, DurationUnit.DAYS, utc))

    @Test fun weeks() = assertEquals(millis("2026-01-15T10:00"), deadlineFrom(millis("2026-01-01T10:00"), 2, DurationUnit.WEEKS, utc))

    @Test fun monthsClampToMonthEnd() =
        assertEquals(millis("2026-02-28T10:00"), deadlineFrom(millis("2026-01-31T10:00"), 1, DurationUnit.MONTHS, utc))

    @Test fun daysKeepWallClockAcrossDst() {
        val berlin = ZoneId.of("Europe/Berlin") // DST starts 2026-03-29
        assertEquals(millis("2026-03-30T10:00", berlin), deadlineFrom(millis("2026-03-28T10:00", berlin), 2, DurationUnit.DAYS, berlin))
    }

    @Test fun setAgainCopy() {
        val failed = Goal(
            id = 7, title = "Run", description = "5k", durationAmount = 3, durationUnit = DurationUnit.WEEKS,
            deadline = 1L, createdAt = 0L, status = Status.FAILED, note = "nope", resolvedAt = 2L,
        )
        val now = millis("2026-05-01T08:00")
        val expected = failed.copy(
            id = 0, status = Status.ACTIVE, createdAt = now, deadline = deadlineFrom(now, 3, DurationUnit.WEEKS), note = null, resolvedAt = null,
        )
        assertEquals(expected, failed.setAgainCopy(now))
    }
}
