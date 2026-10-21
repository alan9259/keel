package com.therecalibrationyears.keel.domain

/**
 * Gentle grouping for symptoms in the "more" picker (port of iOS `SymptomCategory`). Raw
 * values are stored on the symptom, so the older names (`sleep`, `body`, `cognition`,
 * `mood`, `digestion`) stay even though their labels changed. A symptom can also carry a
 * group she made herself: its raw value is then its own label.
 */
enum class SymptomCategory(val raw: String, val label: String) {
    SLEEP("sleep", "Sleep & rest"),
    ENERGY("energy", "Energy"),
    BODY("body", "Body & temperature"),
    ACHES("aches", "Aches & joints"),
    COGNITION("cognition", "Head & thinking"),
    MOOD("mood", "Mood & self"),
    SKIN("skin", "Skin, hair & eyes"),
    DIGESTION("digestion", "Tummy & appetite"),
    INTIMACY("intimacy", "Intimacy & bladder");

    /** Display order in the "more" picker. */
    val sortOrder: Int get() = ordinal

    /** Private by default: the group stays folded away until she opts in. */
    val isSensitive: Boolean get() = this == INTIMACY

    /** Soft framing shown above a sensitive group. */
    val intro: String?
        get() = when (this) {
            INTIMACY -> "These can be harder to talk about. Note only what feels relevant to you. They stay on your phone unless you choose to share them."
            else -> null
        }

    companion object {
        fun fromRaw(raw: String?): SymptomCategory? = entries.firstOrNull { it.raw == raw }

        /** Label for any stored raw value: a built-in's label, or her own group's name. */
        fun labelForRaw(raw: String): String = fromRaw(raw)?.label ?: raw

        /** Built-ins keep their order; her own groups sort after them. */
        fun sortOrderForRaw(raw: String): Int = fromRaw(raw)?.sortOrder ?: 100
    }
}

/**
 * The built-in symptom catalog (port of iOS `SymptomCatalog`, from the symptom list build
 * spec). A small set of common, validating chips shows straight away in the check-in;
 * everything else sits behind "more", grouped gently.
 */
object SymptomCatalog {
    data class Entry(val category: SymptomCategory, val name: String, val isDefault: Boolean = false)

    /** Bumped whenever the entries change, so an existing store picks up the new set once. */
    const val VERSION = 2

    private fun e(c: SymptomCategory, name: String, isDefault: Boolean = false) = Entry(c, name, isDefault)

    val builtIn: List<Entry> = listOf(
        e(SymptomCategory.SLEEP, "Trouble sleeping", true),
        e(SymptomCategory.SLEEP, "Restless sleep"),
        e(SymptomCategory.SLEEP, "Night sweats", true),
        e(SymptomCategory.SLEEP, "Vivid dreams"),
        e(SymptomCategory.SLEEP, "Restless legs"),
        e(SymptomCategory.ENERGY, "Fatigue or low energy", true),
        e(SymptomCategory.ENERGY, "Afternoon crashes"),
        e(SymptomCategory.BODY, "Hot flushes", true),
        e(SymptomCategory.BODY, "Palpitations"),
        e(SymptomCategory.BODY, "Dizziness"),
        e(SymptomCategory.BODY, "Headache", true),
        e(SymptomCategory.BODY, "Migraine"),
        e(SymptomCategory.BODY, "Tinnitus (ringing in the ears)"),
        e(SymptomCategory.BODY, "Pins and needles"),
        e(SymptomCategory.BODY, "Skin-crawling sensation"),
        e(SymptomCategory.ACHES, "Joint pain", true),
        e(SymptomCategory.ACHES, "Muscle aches"),
        e(SymptomCategory.ACHES, "Stiffness"),
        e(SymptomCategory.ACHES, "Frozen shoulder"),
        e(SymptomCategory.ACHES, "Foot pain"),
        e(SymptomCategory.COGNITION, "Brain fog", true),
        e(SymptomCategory.COGNITION, "Memory slips"),
        e(SymptomCategory.COGNITION, "Word-finding difficulty"),
        e(SymptomCategory.COGNITION, "Trouble focusing"),
        e(SymptomCategory.MOOD, "Anxious", true),
        e(SymptomCategory.MOOD, "Irritable", true),
        e(SymptomCategory.MOOD, "Low mood", true),
        e(SymptomCategory.MOOD, "Mood swings", true),
        e(SymptomCategory.MOOD, "Not feeling like myself", true),
        e(SymptomCategory.MOOD, "Overwhelmed"),
        e(SymptomCategory.MOOD, "Flat or numb"),
        e(SymptomCategory.MOOD, "Tearful"),
        e(SymptomCategory.MOOD, "Low motivation"),
        e(SymptomCategory.MOOD, "Loss of confidence"),
        e(SymptomCategory.SKIN, "Dry skin"),
        e(SymptomCategory.SKIN, "Itchy skin"),
        e(SymptomCategory.SKIN, "Hair thinning"),
        e(SymptomCategory.SKIN, "Brittle nails"),
        e(SymptomCategory.SKIN, "Dry eyes"),
        e(SymptomCategory.SKIN, "Dry mouth"),
        e(SymptomCategory.DIGESTION, "Bloating"),
        e(SymptomCategory.DIGESTION, "Nausea"),
        e(SymptomCategory.DIGESTION, "Weight changes"),
        e(SymptomCategory.DIGESTION, "Constipation"),
        e(SymptomCategory.DIGESTION, "Heartburn"),
        e(SymptomCategory.DIGESTION, "Appetite changes"),
        e(SymptomCategory.DIGESTION, "Reduced alcohol tolerance"),
        e(SymptomCategory.INTIMACY, "Vaginal dryness"),
        e(SymptomCategory.INTIMACY, "Low libido"),
        e(SymptomCategory.INTIMACY, "Painful sex"),
        e(SymptomCategory.INTIMACY, "Urinary urgency or frequency"),
        e(SymptomCategory.INTIMACY, "Recurring UTIs"),
        e(SymptomCategory.INTIMACY, "Bladder leaks"),
        e(SymptomCategory.INTIMACY, "Waking to wee"),
    )

    /** The order the default chips appear in before she has history. Common, validating first. */
    val defaultOrder: List<String> = listOf(
        "Hot flushes", "Night sweats", "Trouble sleeping", "Fatigue or low energy", "Brain fog",
        "Headache", "Joint pain", "Anxious", "Irritable", "Low mood", "Mood swings", "Not feeling like myself",
    )

    /** Earlier built-in names and their current wording, so history carries across a rename. */
    val renamed: Map<String, String> = mapOf(
        "Insomnia" to "Trouble sleeping",
        "Restless" to "Restless sleep",
        "Memory issues" to "Memory slips",
        "Difficulty focusing" to "Trouble focusing",
        "Sad" to "Low mood",
        "Changes in appetite" to "Appetite changes",
    )

    fun defaultRank(name: String): Int = defaultOrder.indexOf(name).let { if (it < 0) defaultOrder.size else it }

    private val rankByName = builtIn.withIndex().associate { (i, e) -> e.name.lowercase() to i }

    /** Position within a group (the spec's order, most common first); her own additions last. */
    fun rank(name: String): Int = rankByName[name.lowercase()] ?: builtIn.size
}

/** One symptom as the quick-chip ranking sees it. */
data class QuickChipCandidate(val id: String, val name: String, val isDefaultChip: Boolean)

/**
 * The check-in's quick-pick chips (port of iOS `SymptomRepository.rankedQuickChipIDs`): her
 * most-logged symptoms first (any she has logged, custom or sensitive included), then the
 * catalog default chips. Ties go to default chips, then catalog order, then name, so a fresh
 * account reads in the familiar order. Capped at [limit].
 */
object QuickChipRanking {
    fun rankedIds(candidates: List<QuickChipCandidate>, counts: Map<String, Int>, limit: Int): List<String> =
        candidates
            .filter { it.isDefaultChip || (counts[it.id] ?: 0) > 0 }
            .sortedWith(
                compareByDescending<QuickChipCandidate> { counts[it.id] ?: 0 }
                    .thenByDescending { it.isDefaultChip }
                    .thenBy { SymptomCatalog.defaultRank(it.name) }
                    .thenBy { it.name },
            )
            .take(maxOf(limit, 0))
            .map { it.id }
}
