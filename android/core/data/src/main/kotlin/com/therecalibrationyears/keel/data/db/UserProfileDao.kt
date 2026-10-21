package com.therecalibrationyears.keel.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    /** Her profile: normally the only one; if a restore brought several, the oldest wins. */
    @Query("SELECT * FROM user_profile WHERE deletedAt IS NULL ORDER BY createdAt LIMIT 1")
    suspend fun current(): UserProfileEntity?

    @Query("SELECT * FROM user_profile WHERE deletedAt IS NULL ORDER BY createdAt LIMIT 1")
    fun observeCurrent(): Flow<UserProfileEntity?>

    @Upsert
    suspend fun upsert(profile: UserProfileEntity)
}
