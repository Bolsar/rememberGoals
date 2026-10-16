package com.bolsar.remembergoals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditGoalTest {
    private val start = 1_800_000_000_000L
    private val goal = Goal(id = 4, title = "Run", durationAmount = 2, durationUnit = DurationUnit.WEEKS,
        deadline = deadlineFrom(start, 2, DurationUnit.WEEKS), createdAt = start)
    private val now = start + 86_400_000L * 5
    private val dream = Goal(id = 7, title = "Fly", createdAt = start)

    @Test fun keepsStartAndRecountsDeadlineFromIt() {
        val e = goal.edited(" Run far ", " ", "3", DurationUnit.MONTHS, now)!!
        assertEquals(goal.copy(title = "Run far", durationAmount = 3, durationUnit = DurationUnit.MONTHS,
            deadline = deadlineFrom(start, 3, DurationUnit.MONTHS)), e)
    }

    @Test fun invalidEditsAreRejected() {
        assertNull(goal.edited(" ", "", "2", DurationUnit.DAYS, now)) // no title
        assertNull(goal.edited("Run", "", "", DurationUnit.DAYS, now)) // a goal can't lose its duration
        assertNull(goal.edited("Run", "", "0", DurationUnit.DAYS, now))
    }

    @Test fun dreamWithoutDurationStaysADream() {
        assertEquals(dream.copy(title = "Fly high", description = "plane"), dream.edited("Fly high", " plane ", "", DurationUnit.DAYS, now))
        assertNull(dream.edited(" ", "", "", DurationUnit.DAYS, now))
    }

    @Test fun dreamWithDurationBecomesAGoalFromNow() {
        assertEquals(
            dream.copy(durationAmount = 3, durationUnit = DurationUnit.DAYS, deadline = deadlineFrom(now, 3, DurationUnit.DAYS), createdAt = now),
            dream.edited("Fly", "", "3", DurationUnit.DAYS, now),
        )
    }
}
