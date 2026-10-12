package com.bolsar.remembergoals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditGoalTest {
    private val start = 1_800_000_000_000L
    private val goal = Goal(id = 4, title = "Run", durationAmount = 2, durationUnit = DurationUnit.WEEKS,
        deadline = deadlineFrom(start, 2, DurationUnit.WEEKS), createdAt = start)

    @Test fun keepsStartAndRecountsDeadlineFromIt() {
        val e = goal.edited(" Run far ", " ", "3", DurationUnit.MONTHS)!!
        assertEquals(goal.copy(title = "Run far", durationAmount = 3, durationUnit = DurationUnit.MONTHS,
            deadline = deadlineFrom(start, 3, DurationUnit.MONTHS)), e)
    }

    @Test fun invalidEditsAreRejected() {
        assertNull(goal.edited(" ", "", "2", DurationUnit.DAYS)) // no title
        assertNull(goal.edited("Run", "", "", DurationUnit.DAYS)) // a goal can't lose its duration
        assertNull(goal.edited("Run", "", "0", DurationUnit.DAYS))
    }
}
