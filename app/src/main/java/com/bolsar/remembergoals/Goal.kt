package com.bolsar.remembergoals

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class Status { ACTIVE, ACHIEVED, FAILED }

enum class DurationUnit(val chrono: ChronoUnit) { DAYS(ChronoUnit.DAYS), WEEKS(ChronoUnit.WEEKS), MONTHS(ChronoUnit.MONTHS) }

@Entity
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String? = null,
    val durationAmount: Int? = null,
    val durationUnit: DurationUnit? = null,
    val deadline: Long? = null, // epoch millis; null => dream
    val createdAt: Long,
    val status: Status = Status.ACTIVE,
    val note: String? = null,
    val resolvedAt: Long? = null,
    @ColumnInfo(defaultValue = "0") val draft: Boolean = false, // unfinished Add Goal page (chunk 9); hidden from goals/dreams
)

/** Calendar-aware: +1 month from Jan 31 is Feb 28/29, days survive DST shifts. */
fun deadlineFrom(start: Long, amount: Int, unit: DurationUnit, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(start).atZone(zone).plus(amount.toLong(), unit.chrono).toInstant().toEpochMilli()

/** New ACTIVE goal with the same duration, counted from [now]. Only for goals (not dreams). */
fun Goal.setAgainCopy(now: Long): Goal = copy(
    id = 0,
    status = Status.ACTIVE,
    createdAt = now,
    deadline = deadlineFrom(now, requireNotNull(durationAmount), requireNotNull(durationUnit)),
    note = null,
    resolvedAt = null,
)

/** One row of the main list: a goal card (at createdAt, never moves) or an event about a goal (at the event time). */
sealed interface Entry {
    val goal: Goal
    val time: Long
    val key: String
}

data class GoalCard(override val goal: Goal) : Entry {
    override val time get() = goal.createdAt
    override val key get() = "g${goal.id}"
}

/** [overdue]: the deadline passed (at deadline); else the goal was resolved (at resolvedAt, [Goal.status] says how). */
data class GoalEvent(override val goal: Goal, val overdue: Boolean) : Entry {
    override val time get() = if (overdue) goal.deadline!! else goal.resolvedAt!!
    override val key get() = (if (overdue) "o" else "r") + goal.id
}

/**
 * Events are derived, not stored: OVERDUE once the deadline is past (unless it was resolved in time), and the
 * resolution. Everything sorted newest first, so new events land on top and cards stay where they were.
 * [goals] come newest first; the stable sort keeps that order on equal times.
 */
fun timeline(goals: List<Goal>, now: Long): List<Entry> = goals.flatMap { g ->
    listOfNotNull(
        GoalCard(g),
        GoalEvent(g, overdue = true).takeIf {
            g.deadline != null && g.deadline < now && (g.status == Status.ACTIVE || (g.resolvedAt ?: 0) > g.deadline)
        },
        GoalEvent(g, overdue = false).takeIf { g.status != Status.ACTIVE && g.resolvedAt != null },
    )
}.sortedByDescending { it.time }
