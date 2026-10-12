package com.bolsar.remembergoals

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GoalsViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).goalDao()

    val goals: StateFlow<List<Goal>> = dao.goals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dreams: StateFlow<List<Goal>> = dao.dreams().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** No amount/unit => saved as a dream. */
    fun addGoal(title: String, description: String?, amount: Int?, unit: DurationUnit?) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val deadline = if (amount != null && unit != null) deadlineFrom(now, amount, unit) else null
            dao.insert(
                Goal(
                    title = title,
                    description = description,
                    durationAmount = amount.takeIf { deadline != null },
                    durationUnit = unit.takeIf { deadline != null },
                    deadline = deadline,
                    createdAt = now,
                ),
            )
        }
    }

    /** Only ACTIVE goals; no-op otherwise. Caller must ensure [note] is not blank. */
    fun resolve(goal: Goal, achieved: Boolean, note: String) {
        viewModelScope.launch {
            dao.resolve(goal.id, if (achieved) Status.ACHIEVED else Status.FAILED, note, System.currentTimeMillis())
        }
    }

    /** Inserts a fresh ACTIVE copy; the FAILED original stays untouched. Dreams are ignored (no duration to copy). */
    fun setAgain(goal: Goal) {
        if (goal.deadline == null) return
        viewModelScope.launch { dao.insert(goal.setAgainCopy(System.currentTimeMillis())) }
    }

    /** Dream -> goal. createdAt resets to now so it lands on top of the newest-first list. */
    fun promote(dream: Goal, amount: Int, unit: DurationUnit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.promote(dream.id, amount, unit, deadlineFrom(now, amount, unit), now)
        }
    }
}
