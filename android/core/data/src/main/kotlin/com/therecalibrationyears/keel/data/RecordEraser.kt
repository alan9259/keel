package com.therecalibrationyears.keel.data

import com.therecalibrationyears.keel.data.db.MainDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deletes every record Keel holds in its database (the delete half of iOS `eraseAllData`):
 * her profile, entries, symptoms, medicines and their logs, cycle days, insights and
 * activity, in one transaction. The built-in symptom catalog is re-seeded afterwards by
 * [SymptomRepository.syncBuiltIns], which seeds an empty table.
 */
class RecordEraser(private val database: MainDatabase) {
    suspend fun deleteEverything() = withContext(Dispatchers.IO) { database.clearAllTables() }
}
