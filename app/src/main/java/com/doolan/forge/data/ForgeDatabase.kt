package com.doolan.forge.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ExerciseEntity::class,
        EquipmentEntity::class,
        SessionEntity::class,
        SessionExerciseEntity::class,
        SetLogEntity::class,
        BodyweightEntity::class,
        ProgrammeEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class ForgeDatabase : RoomDatabase() {
    abstract fun exercises(): ExerciseDao
    abstract fun equipment(): EquipmentDao
    abstract fun sessions(): SessionDao
    abstract fun sessionExercises(): SessionExerciseDao
    abstract fun setLogs(): SetLogDao
    abstract fun bodyweight(): BodyweightDao
    abstract fun programmes(): ProgrammeDao

    companion object {
        fun build(context: Context): ForgeDatabase =
            Room.databaseBuilder(context, ForgeDatabase::class.java, "forge.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN workout TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN reason TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE sessions ADD COLUMN programmeId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE session_exercises ADD COLUMN targetWeightKg REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE session_exercises ADD COLUMN setPlan TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE session_exercises ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS programmes (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "name TEXT NOT NULL, goal TEXT NOT NULL, split TEXT NOT NULL, method TEXT NOT NULL, " +
                        "startDay INTEGER NOT NULL, weeks INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                )
            }
        }
    }
}
