package com.therecalibrationyears.keel.data.db

import androidx.room.TypeConverter
import java.time.Instant

/** Instants are stored as epoch milliseconds. */
class Converters {
    @TypeConverter fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
