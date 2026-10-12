package com.bolsar.remembergoals

import org.junit.Assert.assertEquals
import org.junit.Test

class RemindersTest {
    private val day = 24 * 60 * 60 * 1000L

    @Test fun bothReminders() = assertEquals(mapOf("pre" to 2 * day, "due" to 3 * day), reminderDelays(3 * day, 0))

    @Test fun preDroppedWithinLastDay() = assertEquals(mapOf("due" to day / 2), reminderDelays(day / 2, 0))

    @Test fun preDroppedExactlyOneDayOut() = assertEquals(mapOf("due" to day), reminderDelays(day, 0))

    @Test fun pastDeadlineFiresNow() = assertEquals(mapOf("due" to 0L), reminderDelays(0, day))
}
