package com.bolsar.remembergoals

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

private const val DAY_MS = 24 * 60 * 60 * 1000L
const val CHANNEL = "deadlines"
const val EXTRA_GOAL_ID = "goalId"
const val EXTRA_ACHIEVED = "achieved"

/** Delay in ms until each reminder ("pre" = a day before, "due" = at the deadline). A past "pre" is dropped. */
fun reminderDelays(deadline: Long, now: Long): Map<String, Long> = buildMap {
    if (deadline - DAY_MS > now) put("pre", deadline - DAY_MS - now)
    put("due", maxOf(0, deadline - now))
}

fun scheduleReminders(context: Context, goalId: Long, deadline: Long) {
    val wm = WorkManager.getInstance(context)
    reminderDelays(deadline, System.currentTimeMillis()).forEach { (kind, delay) ->
        val request = OneTimeWorkRequestBuilder<DueWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(EXTRA_GOAL_ID to goalId, "kind" to kind))
            .build()
        wm.enqueueUniqueWork("goal-$goalId-$kind", ExistingWorkPolicy.REPLACE, request)
    }
}

fun cancelReminders(context: Context, goalId: Long) {
    val wm = WorkManager.getInstance(context)
    wm.cancelUniqueWork("goal-$goalId-pre")
    wm.cancelUniqueWork("goal-$goalId-due")
    NotificationManagerCompat.from(context).cancel(goalId.toInt())
}

class DueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val id = inputData.getLong(EXTRA_GOAL_ID, -1)
        val goal = AppDb.get(ctx).goalDao().getById(id)
        val pre = inputData.getString("kind") == "pre"
        if (goal == null || goal.status != Status.ACTIVE) return Result.success()
        // A late "pre" (Doze, reboot) must not post "due tomorrow" over the "due" one.
        if (pre && System.currentTimeMillis() >= (goal.deadline ?: 0)) return Result.success()
        // ponytail: denied permission = silent no-op; the row's "Nd over" still tells the story in-app.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        ctx.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Deadlines", NotificationManager.IMPORTANCE_DEFAULT))
        // Distinct request codes per goal and button, so the three PendingIntents don't overwrite each other's extras.
        fun open(n: Int, achieved: Boolean): PendingIntent = PendingIntent.getActivity(
            ctx, (id * 3 + n).toInt(),
            Intent(ctx, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_GOAL_ID, id)
                .putExtra(EXTRA_ACHIEVED, achieved),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (pre) "Due tomorrow. On track?" else "Today's the day. Did you make it?"
        // One notification id per goal: the "due" one replaces a still-showing "pre".
        NotificationManagerCompat.from(ctx).notify(
            id.toInt(),
            NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_goal)
                .setContentTitle(goal.title)
                .setContentText(text)
                .setContentIntent(open(0, true))
                .setAutoCancel(true)
                .addAction(0, "Achieved", open(1, true))
                .addAction(0, "Not achieved", open(2, false))
                .build(),
        )
        return Result.success()
    }
}
