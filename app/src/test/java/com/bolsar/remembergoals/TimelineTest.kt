package com.bolsar.remembergoals

import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTest {
    private fun goal(id: Long, created: Long, deadline: Long, status: Status = Status.ACTIVE, resolvedAt: Long? = null) =
        Goal(id = id, title = "g$id", deadline = deadline, createdAt = created, status = status, resolvedAt = resolvedAt)

    @Test fun eventsLandOnTopAndCardsKeepTheirPlace() {
        val early = goal(1, created = 10, deadline = 100, Status.ACHIEVED, resolvedAt = 50) // resolved in time: no overdue
        val late = goal(2, created = 20, deadline = 60, Status.FAILED, resolvedAt = 90) // overdue at 60, then failed at 90
        val open = goal(3, created = 30, deadline = 70) // still ACTIVE, overdue at 70
        val future = goal(4, created = 40, deadline = 500) // nothing yet
        val keys = timeline(listOf(future, open, late, early), now = 200).map { it.key }
        assertEquals(listOf("r2", "o3", "o2", "r1", "g4", "g3", "g2", "g1"), keys)
    }

    @Test fun overdueAppearsOnlyOnceNowPassesTheDeadline() {
        val g = goal(1, created = 0, deadline = 100)
        assertEquals(listOf("g1"), timeline(listOf(g), now = 100).map { it.key })
        assertEquals(listOf("o1", "g1"), timeline(listOf(g), now = 101).map { it.key })
    }
}
