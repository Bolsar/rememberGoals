package com.bolsar.remembergoals

import com.bolsar.remembergoals.HistoryKind.ACHIEVED
import com.bolsar.remembergoals.HistoryKind.CREATED
import com.bolsar.remembergoals.HistoryKind.EDITED
import com.bolsar.remembergoals.HistoryKind.FAILED
import com.bolsar.remembergoals.HistoryKind.OVERDUE
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryTest {
    private fun goal(deadline: Long? = 100, status: Status = Status.ACTIVE, resolvedAt: Long? = null) =
        Goal(id = 1, title = "g", deadline = deadline, createdAt = 10, status = status, resolvedAt = resolvedAt)

    @Test fun activeBeforeDeadline() = assertEquals(listOf(CREATED to 10L), history(goal(), emptyList(), now = 50))

    @Test fun editsInOrder() =
        assertEquals(listOf(CREATED to 10L, EDITED to 20L, EDITED to 30L), history(goal(), listOf(20, 30), now = 50))

    @Test fun overdueWhileActive() =
        assertEquals(listOf(CREATED to 10L, EDITED to 20L, OVERDUE to 100L), history(goal(), listOf(20), now = 200))

    @Test fun overdueThenAchieved() = assertEquals(
        listOf(CREATED to 10L, OVERDUE to 100L, ACHIEVED to 150L),
        history(goal(status = Status.ACHIEVED, resolvedAt = 150), emptyList(), now = 200),
    )

    @Test fun achievedEarlyHasNoOverdue() = assertEquals(
        listOf(CREATED to 10L, EDITED to 30L, ACHIEVED to 50L),
        history(goal(status = Status.ACHIEVED, resolvedAt = 50), listOf(30), now = 200),
    )

    @Test fun failed() = assertEquals(
        listOf(CREATED to 10L, FAILED to 80L),
        history(goal(status = Status.FAILED, resolvedAt = 80), emptyList(), now = 200),
    )

    @Test fun dreamHasNoOverdue() = assertEquals(listOf(CREATED to 10L, EDITED to 20L), history(goal(deadline = null), listOf(20), now = 200))
}
