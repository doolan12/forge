package com.doolan.forge.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ExerciseEntity::class,
        EquipmentEntity::class,
        SessionEntity::class,
        SessionExerciseEntity::class,
        SetLogEntity::class,
        BodyweightEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ForgeDatabase : RoomDatabase() {
    abstract fun exercises(): ExerciseDao
    abstract fun equipment(): EquipmentDao
    abstract fun sessions(): SessionDao
    abstract fun sessionExercises(): SessionExerciseDao
    abstract fun setLogs(): SetLogDao
    abstract fun bodyweight(): BodyweightDao

    companion object {
        fun build(context: Context): ForgeDatabase =
            Room.databaseBuilder(context, ForgeDatabase::class.java, "forge.db").build()
    }
}
