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
        ProgrammeEntity::class,
        CheckInEntity::class,
        FoodEntryEntity::class,
        MeasurementEntity::class
    ],
    version = 5,
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
    abstract fun checkIns(): CheckInDao
    abstract fun foods(): FoodDao
    abstract fun measurements(): MeasurementDao

    companion object {
        fun build(context: Context): ForgeDatabase =
            Room.databaseBuilder(context, ForgeDatabase::class.java, "forge.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS food_entries (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, dateEpochDay INTEGER NOT NULL, " +
                        "name TEXT NOT NULL, kcal INTEGER NOT NULL, proteinG REAL NOT NULL, carbsG REAL NOT NULL, fatG REAL NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS measurements (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, dateEpochDay INTEGER NOT NULL, " +
                        "type TEXT NOT NULL, value REAL NOT NULL)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS checkins (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, dateEpochDay INTEGER NOT NULL, " +
                        "sleepHours REAL NOT NULL, sleepQuality INTEGER NOT NULL, soreness INTEGER NOT NULL, " +
                        "stress INTEGER NOT NULL, energy INTEGER NOT NULL, restingHr INTEGER NOT NULL, note TEXT NOT NULL)"
                )
            }
        }

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
