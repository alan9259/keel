package com.therecalibrationyears.keel.data.db

import androidx.room.Dao
import androidx.room.Query

/**
 * Imports earlier iOS builds wrote into her own records (Apple Health symptom links, cycle
 * entries and activity). They can reach Android in a restored iPhone backup; the launch clean-up
 * removes them, leaving everything she logged herself (iOS `purgeDiscontinuedHealthImports`).
 */
@Dao
interface LegacyImportDao {
    @Query("DELETE FROM check_in_symptom WHERE sourceRaw = 'healthKit'")
    suspend fun deleteImportedSymptomLinks(): Int

    @Query("DELETE FROM cycle_entry WHERE sourceRaw = 'healthKit'")
    suspend fun deleteImportedCycleEntries(): Int

    @Query("DELETE FROM activity_log WHERE sourceRaw = 'healthKit'")
    suspend fun deleteImportedActivityLogs(): Int
}
