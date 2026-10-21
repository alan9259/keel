package com.therecalibrationyears.keel.domain

import java.text.Collator
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Decoded shape of `treatment-catalog.json` (port of iOS `TreatmentCatalog`). The list is
 * data, not code: it ships as the iOS file, copied at build time, and a newer cached copy
 * wins. A logging taxonomy: nothing here recommends, doses or ranks.
 */
@Serializable
data class TreatmentCatalog(val version: Int, val groups: List<Group>) {

    @Serializable
    data class Group(
        val id: String,
        @SerialName("kind") val kindRaw: String,
        /** Heading that several groups can share, e.g. "Oestrogen". */
        val section: String,
        val title: String,
        /** Plain framing shown above the group. */
        val note: String? = null,
        @SerialName("defaultMethod") val defaultMethodRaw: String? = null,
        val items: List<Item>,
    ) {
        val kind: TreatmentKind get() = TreatmentKind.fromRaw(kindRaw)
        val defaultMethod: MedicationMethod? get() = MedicationMethod.fromRaw(defaultMethodRaw)

        /** Alphabetical, never editorial: no product leads because we put it there. */
        fun sortedItems(locale: Locale = Locale.getDefault()): List<Item> {
            val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
            return items.sortedWith { a, b -> collator.compare(a.name, b.name) }
        }
    }

    @Serializable
    data class Item(
        val name: String,
        /** Overrides the group's method, e.g. an IUD inside the progestogen group. */
        @SerialName("method") val methodRaw: String? = null,
        /** Prescribed outside its approved use. */
        val offLabel: Boolean? = null,
        /** Compounded: not standardised, so strength isn't captured. */
        val compounded: Boolean? = null,
    ) {
        val method: MedicationMethod? get() = MedicationMethod.fromRaw(methodRaw)
        val isOffLabel: Boolean get() = offLabel ?: false
        val isCompounded: Boolean get() = compounded ?: false
    }

    fun groups(kind: TreatmentKind): List<Group> = groups.filter { it.kind == kind }

    companion object {
        val empty = TreatmentCatalog(version = 0, groups = emptyList())

        private val json = Json { ignoreUnknownKeys = true }

        /** Null when the text isn't a catalog, so a broken cached copy falls back to the bundled one. */
        fun decode(text: String): TreatmentCatalog? = runCatching { json.decodeFromString(serializer(), text) }.getOrNull()

        /**
         * Groups with an item whose name contains [query] (case-insensitive), each keeping only
         * its matching items. A blank query returns the groups unchanged.
         */
        fun filter(groups: List<Group>, query: String): List<Group> {
            val q = query.trim()
            if (q.isEmpty()) return groups
            return groups.mapNotNull { group ->
                val matches = group.items.filter { it.name.contains(q, ignoreCase = true) }
                if (matches.isEmpty()) null else group.copy(items = matches)
            }
        }
    }
}
