package com.bolsar.remembergoals

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
