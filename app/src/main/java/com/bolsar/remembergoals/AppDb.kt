package com.bolsar.remembergoals

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    /** Goals and drafts (the main list). Drafts never have a deadline, so `deadline IS NOT NULL` means a real goal. */
    @Query("SELECT * FROM Goal WHERE deadline IS NOT NULL OR draft = 1 ORDER BY createdAt DESC, id DESC")
    fun goals(): Flow<List<Goal>>

    @Query("SELECT * FROM Goal WHERE deadline IS NULL AND draft = 0 ORDER BY createdAt DESC, id DESC")
    fun dreams(): Flow<List<Goal>>

    @Query("SELECT * FROM Goal WHERE id = :id")
    suspend fun getById(id: Long): Goal?

    @Insert
    suspend fun insert(goal: Goal): Long

    /** Add Goal page only: a reopened draft (existing id) is overwritten in place; id 0 inserts. Returns the row id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(goal: Goal): Long

    @Query("SELECT id FROM Goal WHERE draft = 1 AND createdAt = :createdAt LIMIT 1")
    suspend fun draftIdAt(createdAt: Long): Long?

    /**
     * Add Goal page write, idempotent: a page that already left a draft (a background save, before or after process
     * death) is found by its saved [createdAt] when it has no id, so repeated saves update that one row.
     * One transaction: two quick saves can't both miss the lookup and insert twice.
     */
    @Transaction
    suspend fun savePage(goal: Goal, createdAt: Long): Long =
        save(goal.copy(id = goal.id.takeIf { it != 0L } ?: draftIdAt(createdAt) ?: 0L))

    /** Page left empty: drop its draft (by [id], or by the page's [createdAt] if a background save wrote one). */
    @Query("DELETE FROM Goal WHERE draft = 1 AND (id = :id OR createdAt = :createdAt)")
    suspend fun deletePageDraft(id: Long, createdAt: Long)

    // Targeted updates (not @Update of a UI snapshot): a stale row can't overwrite newer state, and the WHERE guards
    // make resolving an already-resolved goal or promoting a non-dream a no-op.
    @Query("UPDATE Goal SET status = :status, note = :note, resolvedAt = :resolvedAt WHERE id = :id AND status = 'ACTIVE' AND deadline IS NOT NULL")
    suspend fun resolve(id: Long, status: Status, note: String, resolvedAt: Long)

    /** A dream may gain a deadline (and a new createdAt, see [Goal.edited]); a goal never loses its deadline. */
    @Query("UPDATE Goal SET title = :title, description = :description, durationAmount = :amount, durationUnit = :unit, deadline = :deadline, createdAt = :createdAt WHERE id = :id AND status = 'ACTIVE' AND draft = 0 AND (deadline IS NULL OR :deadline IS NOT NULL)")
    suspend fun edit(id: Long, title: String, description: String?, amount: Int?, unit: DurationUnit?, deadline: Long?, createdAt: Long): Int

    @Insert
    suspend fun logEdit(edit: GoalEdit)

    /** Edit + its log row in one transaction; nothing is logged when the guard made the edit a no-op. */
    @Transaction
    suspend fun editLogged(goal: Goal, at: Long): Boolean =
        (edit(goal.id, goal.title, goal.description, goal.durationAmount, goal.durationUnit, goal.deadline, goal.createdAt) > 0)
            .also { if (it) logEdit(GoalEdit(goalId = goal.id, at = at)) }

    @Query("SELECT at FROM GoalEdit WHERE goalId = :id ORDER BY at")
    fun edits(id: Long): Flow<List<Long>>

    /** Goals/dreams with at least one edit: their cards say "edited". */
    @Query("SELECT DISTINCT goalId FROM GoalEdit")
    fun editedIds(): Flow<List<Long>>

    @Query("UPDATE Goal SET durationAmount = :amount, durationUnit = :unit, deadline = :deadline, createdAt = :now WHERE id = :id AND deadline IS NULL")
    suspend fun promote(id: Long, amount: Int, unit: DurationUnit, deadline: Long, now: Long): Int
}

@Database(entities = [Goal::class, GoalEdit::class], version = 3, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun goalDao(): GoalDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "goals.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}

/** v2: drafts. Column only, existing rows become non-drafts. Never destructive. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) = db.execSQL("ALTER TABLE Goal ADD COLUMN draft INTEGER NOT NULL DEFAULT 0")
}

/** v3: edit log. A new table only (same SQL Room generates for [GoalEdit]); existing rows untouched. Never destructive. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) =
        db.execSQL("CREATE TABLE IF NOT EXISTS `GoalEdit` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `goalId` INTEGER NOT NULL, `at` INTEGER NOT NULL)")
}
