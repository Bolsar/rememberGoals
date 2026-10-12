package com.bolsar.remembergoals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageGoalTest {
    private val now = 1_800_000_000_000L
    private fun page(title: String = "", description: String = "", amount: String = "", id: Long = 0, draft: Boolean) =
        pageGoal(id, createdAt = 5L, title, description, amount, DurationUnit.WEEKS, now, draft)

    @Test fun nothingTypedSavesNoDraft() {
        assertNull(page(title = "  ", description = " ", draft = true))
        assertNull(page(id = 9, draft = true)) // a reopened draft emptied out => discarded
    }

    @Test fun draftKeepsWhatWasTypedAndItsPlace() {
        val d = page(title = " Run ", amount = "2", id = 9, draft = true)!!
        assertEquals(Goal(id = 9, title = "Run", durationAmount = 2, durationUnit = DurationUnit.WEEKS, createdAt = 5L, draft = true), d)
        assertNull(page(description = "why", amount = "0", draft = true)!!.durationAmount) // "0" is not kept
    }

    @Test fun addWithAmountIsAGoalFromNow() {
        val g = page(title = "Run", description = " ", amount = "2", id = 9, draft = false)!!
        assertEquals(
            Goal(9, "Run", null, 2, DurationUnit.WEEKS, deadlineFrom(now, 2, DurationUnit.WEEKS), createdAt = now), g,
        )
    }

    @Test fun addWithoutAmountIsADream() {
        val g = page(title = "Sail", id = 9, draft = false)!!
        assertEquals(Goal(9, "Sail", createdAt = now), g)
    }

    @Test fun draftIsACardWithoutEvents() {
        val d = page(title = "Run", draft = true)!!.copy(id = 1)
        assertEquals(listOf(GoalCard(d)), timeline(listOf(d), now))
    }
}
