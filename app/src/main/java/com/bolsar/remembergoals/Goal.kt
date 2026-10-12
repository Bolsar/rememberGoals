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
    @ColumnInfo(defaultValue = "0") val draft: Boolean = false, // Add Goal page left without "Add"; never has a deadline
)

/**
 * What the Add Goal page writes. [id]: 0 for a new page, else the reopened draft (overwritten in place, no duplicate).
 * [amount] is the raw digits field ("0" is dropped, the page blocks Add on it).
 * - [draft] (page left without Add): whatever was typed, with no deadline and the draft's [createdAt] kept (it never
 *   moves). null when nothing was typed: nothing to keep (and a reopened draft emptied this way is discarded).
 * - Add: a goal with the deadline counted from [now], or a dream without a valid amount. createdAt = [now] (lands on top).
 */
fun pageGoal(
    id: Long, createdAt: Long, title: String, description: String, amount: String, unit: DurationUnit, now: Long, draft: Boolean,
): Goal? {
    val n = amount.toIntOrNull()?.takeIf { it > 0 } // "0" counts as not typed
    if (draft && title.isBlank() && description.isBlank() && n == null) return null
    val deadline = if (!draft && n != null) deadlineFrom(now, n, unit) else null
    return Goal(
        id = id,
        title = title.trim(),
        description = description.trim().ifBlank { null },
        durationAmount = n,
        durationUnit = unit.takeIf { n != null },
        deadline = deadline,
        createdAt = if (draft) createdAt else now,
        draft = draft,
    )
}

/**
 * Edit page "Save" on an ACTIVE goal: same start (createdAt), so the card keeps its place; the deadline is re-counted
 * from that start. null = invalid (blank title, or no amount: a goal can't lose its duration here).
 */
fun Goal.edited(title: String, description: String, amount: String, unit: DurationUnit): Goal? {
    val n = amount.toIntOrNull()?.takeIf { it > 0 } ?: return null
    if (title.isBlank()) return null
    return copy(
        title = title.trim(),
        description = description.trim().ifBlank { null },
        durationAmount = n,
        durationUnit = unit,
        deadline = deadlineFrom(createdAt, n, unit),
    )
}

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

/**
 * One row of the main list: a goal card (at createdAt, never moves; a draft is a card too, with no events) or an event
 * about a goal (at the event time).
 */
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
        GoalEvent(g, overdue = false).takeIf { g.resolvedAt != null },
    )
}.sortedByDescending { it.time }
