package com.bolsar.remembergoals

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM Goal WHERE deadline IS NOT NULL ORDER BY createdAt DESC, id DESC")
    fun goals(): Flow<List<Goal>>

    @Query("SELECT * FROM Goal WHERE deadline IS NULL ORDER BY createdAt DESC, id DESC")
    fun dreams(): Flow<List<Goal>>

    @Query("SELECT * FROM Goal WHERE id = :id")
    suspend fun getById(id: Long): Goal?

    @Insert
    suspend fun insert(goal: Goal): Long

    // Targeted updates (not @Update of a UI snapshot): a stale row can't overwrite newer state, and the WHERE guards
    // make resolving an already-resolved goal or promoting a non-dream a no-op.
    @Query("UPDATE Goal SET status = :status, note = :note, resolvedAt = :resolvedAt WHERE id = :id AND status = 'ACTIVE' AND deadline IS NOT NULL")
    suspend fun resolve(id: Long, status: Status, note: String, resolvedAt: Long)

    @Query("UPDATE Goal SET durationAmount = :amount, durationUnit = :unit, deadline = :deadline, createdAt = :now WHERE id = :id AND deadline IS NULL")
    suspend fun promote(id: Long, amount: Int, unit: DurationUnit, deadline: Long, now: Long): Int
}

@Database(entities = [Goal::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun goalDao(): GoalDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "goals.db").build()
                .also { instance = it }
        }
    }
}
