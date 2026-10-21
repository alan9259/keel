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
@Database(entities = [UserProfileEntity::class], version = 1, exportSchema = true)
@TypeConverters(Converters::class)
abstract class MainDatabase : RoomDatabase() {
    abstract fun userProfiles(): UserProfileDao

    companion object {
        private const val FILE_NAME = "keel-main.db"

        fun open(context: Context): MainDatabase =
            Room.databaseBuilder(context, MainDatabase::class.java, FILE_NAME).build()

        fun inMemory(context: Context): MainDatabase =
            Room.inMemoryDatabaseBuilder(context, MainDatabase::class.java).build()
    }
}
