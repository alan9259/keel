package com.therecalibrationyears.keel.checkin

import com.therecalibrationyears.keel.data.db.SymptomEntity
import com.therecalibrationyears.keel.domain.SymptomCatalog
import com.therecalibrationyears.keel.domain.SymptomCategory

/** One group in the "more" picker. */
data class SymptomGroup(
    val raw: String,
    val label: String,
    val items: List<SymptomEntity>,
    val isSensitive: Boolean,
    val intro: String?,
)

/**
 * The picker's groups (port of iOS `SymptomPickerSheet.groups`): every built-in group, always
 * shown so each can be added to and dropped on, plus any group of her own, after them. Within
 * a group, the catalog's order (most common first), then her own additions by name.
 */
fun pickerGroups(symptoms: List<SymptomEntity>): List<SymptomGroup> {
    val byRaw = symptoms.groupBy { it.categoryRaw }
    val raws = byRaw.keys + SymptomCategory.entries.map { it.raw }
    return raws.sortedWith(compareBy<String> { SymptomCategory.sortOrderForRaw(it) }.thenBy { it }).map { raw ->
        val category = SymptomCategory.fromRaw(raw)
        SymptomGroup(
            raw = raw,
            label = SymptomCategory.labelForRaw(raw),
            items = byRaw[raw].orEmpty().sortedWith(compareBy<SymptomEntity> { SymptomCatalog.rank(it.name) }.thenBy { it.name }),
            isSensitive = category?.isSensitive ?: false,
            intro = category?.intro,
        )
    }
}
