package com.bolsar.remembergoals

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GoalsViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).goalDao()

    // null until the first DB emission, so the UI can tell "loading" from "none yet".
    val goals: StateFlow<List<Goal>?> = dao.goals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val dreams: StateFlow<List<Goal>?> = dao.dreams().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val editedIds: StateFlow<Set<Long>> =
        dao.editedIds().map { it.toSet() }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Edit times of one goal/dream, oldest first (its history tree). */
    fun edits(id: Long): Flow<List<Long>> = dao.edits(id)

    /**
     * Add Goal page closed or sent to the background: [goal] from [pageGoal] (goal, dream or draft) is written over
     * draft [id] (0 = new page), or over the draft this page already left (found by the page's [createdAt]).
     * null = nothing typed: that draft, if any, is discarded.
     */
    fun savePage(id: Long, createdAt: Long, goal: Goal?) {
        viewModelScope.launch {
            if (goal == null) { dao.deletePageDraft(id, createdAt); return@launch }
            val rowId = dao.savePage(goal, createdAt)
            goal.deadline?.let { scheduleReminders(getApplication(), rowId, it) }
        }
    }

    /**
     * [goal] from [Goal.edited], logged as one edit. Only ACTIVE goals/dreams (one resolved meanwhile stays as it was).
     * Reminders follow the new deadline; a dream that just became a goal gets its first ones.
     */
    fun edit(goal: Goal) {
        viewModelScope.launch {
            if (!dao.editLogged(goal, System.currentTimeMillis())) return@launch
            val deadline = goal.deadline ?: return@launch // still a dream: nothing to remind
            cancelReminders(getApplication(), goal.id) // an old "pre" may no longer fit before the new deadline
            scheduleReminders(getApplication(), goal.id, deadline)
        }
    }

    /** Only ACTIVE goals; no-op otherwise. Caller must ensure [note] is not blank. */
    fun resolve(goal: Goal, achieved: Boolean, note: String) {
        viewModelScope.launch {
            dao.resolve(goal.id, if (achieved) Status.ACHIEVED else Status.FAILED, note, System.currentTimeMillis())
            cancelReminders(getApplication(), goal.id)
        }
    }

    /** Inserts a fresh ACTIVE copy; the FAILED original stays untouched. Dreams are ignored (no duration to copy). */
    fun setAgain(goal: Goal) {
        if (goal.deadline == null) return
        viewModelScope.launch {
            val copy = goal.setAgainCopy(System.currentTimeMillis())
            scheduleReminders(getApplication(), dao.insert(copy), copy.deadline!!)
        }
    }

    /** Dream -> goal. createdAt resets to now so it lands on top of the newest-first list. */
    fun promote(dream: Goal, amount: Int, unit: DurationUnit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val deadline = deadlineFrom(now, amount, unit)
            if (dao.promote(dream.id, amount, unit, deadline, now) > 0) scheduleReminders(getApplication(), dream.id, deadline)
        }
    }
}
