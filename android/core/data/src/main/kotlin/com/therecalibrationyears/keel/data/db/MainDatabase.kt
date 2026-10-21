package com.therecalibrationyears.keel.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Her records (port of the iOS main SwiftData store). Tables are added as each milestone
 * ports its models. Version 1 stays editable until the first Play upload; from then on
 * every schema change is a migration and never a destructive reset (plan D6).
 */
@Database(
    entities = [
        UserProfileEntity::class, CheckInEntity::class, SymptomEntity::class, CheckInSymptomEntity::class,
        MedicationEntity::class, MedicationLogEntity::class, ActivityLogEntity::class, CycleEntryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MainDatabase : RoomDatabase() {
    abstract fun userProfiles(): UserProfileDao
    abstract fun checkIns(): CheckInDao
    abstract fun symptoms(): SymptomDao
    abstract fun medications(): MedicationDao
    abstract fun activityLogs(): ActivityLogDao
    abstract fun cycle(): CycleDao

    companion object {
        private const val FILE_NAME = "keel-main.db"

        fun open(context: Context): MainDatabase =
            Room.databaseBuilder(context, MainDatabase::class.java, FILE_NAME).build()

        fun inMemory(context: Context): MainDatabase =
            Room.inMemoryDatabaseBuilder(context, MainDatabase::class.java).build()
    }
}
