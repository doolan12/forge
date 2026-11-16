package com.doolan.forge.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name")
    fun all(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY name")
    suspend fun allNow(): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun byId(id: String): ExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: ExerciseEntity)

    @Query("UPDATE exercises SET isFavourite = :fav WHERE id = :id")
    suspend fun setFavourite(id: String, fav: Boolean)

    @Query("DELETE FROM exercises WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustom(id: String)
}

@Dao
interface EquipmentDao {
    @Query("SELECT * FROM equipment ORDER BY name")
    fun all(): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment ORDER BY name")
    suspend fun allNow(): List<EquipmentEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(list: List<EquipmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(list: List<EquipmentEntity>)

    @Query("UPDATE equipment SET available = :available WHERE name = :name")
    suspend fun setAvailable(name: String, available: Boolean)

    @Query("DELETE FROM equipment WHERE name = :name AND isCustom = 1")
    suspend fun deleteCustom(name: String)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE dateEpochDay BETWEEN :from AND :to ORDER BY dateEpochDay, id")
    fun between(from: Long, to: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions ORDER BY dateEpochDay, id")
    suspend fun allNow(): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun byId(id: Long): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(s: SessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<SessionEntity>)

    @Update
    suspend fun update(s: SessionEntity)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface SessionExerciseDao {
    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY orderIdx, id")
    suspend fun forSession(sessionId: Long): List<SessionExerciseEntity>

    @Query("SELECT * FROM session_exercises")
    suspend fun allNow(): List<SessionExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: SessionExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<SessionExerciseEntity>)

    @Update
    suspend fun update(e: SessionExerciseEntity)

    @Query("DELETE FROM session_exercises WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM session_exercises WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)
}

@Dao
interface SetLogDao {
    @Insert
    suspend fun insert(l: SetLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<SetLogEntity>)

    @Query("DELETE FROM set_logs WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM set_logs WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    @Query("DELETE FROM set_logs WHERE sessionExerciseId = :seId")
    suspend fun deleteForSessionExercise(seId: Long)

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY setIndex")
    suspend fun forSession(sessionId: Long): List<SetLogEntity>

    @Query("SELECT * FROM set_logs WHERE exerciseId = :exerciseId AND sessionId != :excludeSession ORDER BY completedAt DESC")
    suspend fun historyFor(exerciseId: String, excludeSession: Long): List<SetLogEntity>

    @Query("SELECT * FROM set_logs ORDER BY completedAt")
    suspend fun allNow(): List<SetLogEntity>

    @Query("SELECT * FROM set_logs WHERE dateEpochDay BETWEEN :from AND :to ORDER BY completedAt")
    fun between(from: Long, to: Long): Flow<List<SetLogEntity>>
}

@Dao
interface BodyweightDao {
    @Insert
    suspend fun insert(b: BodyweightEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<BodyweightEntity>)

    @Delete
    suspend fun delete(b: BodyweightEntity)

    @Query("SELECT * FROM bodyweight ORDER BY dateEpochDay, id")
    fun all(): Flow<List<BodyweightEntity>>

    @Query("SELECT * FROM bodyweight ORDER BY dateEpochDay, id")
    suspend fun allNow(): List<BodyweightEntity>
}
