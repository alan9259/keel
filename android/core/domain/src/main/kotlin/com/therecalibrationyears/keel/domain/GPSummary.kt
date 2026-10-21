package com.therecalibrationyears.keel.domain

import com.therecalibrationyears.keel.domain.parity.formatOneDecimal
import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The GP Visit Summary's value types and pure rules (port of iOS `GPSummaryDocument.swift`
 * and `GPSummaryBuilder.swift`, Keel/Features/GPSummary). Days are `LocalDate`s in her zone,
 * so nothing here depends on a clock or time zone.
 */

/**
 * The time span the summary covers. Default is twelve weeks (spec); custom is her own two
 * dates. The window it gives also carries the equal-length window just before it, for the
 * symptom section's "previous period" column.
 */
sealed interface GPPeriod {
    val label: String

    data object FourWeeks : GPPeriod { override val label = "4 weeks" }
    data object EightWeeks : GPPeriod { override val label = "8 weeks" }
    data object TwelveWeeks : GPPeriod { override val label = "12 weeks" }
    data object SixMonths : GPPeriod { override val label = "6 months" }
    data class Custom(val start: LocalDate, val end: LocalDate) : GPPeriod { override val label = "Custom" }

    fun window(today: LocalDate): GPDateWindow = when (this) {
        FourWeeks -> GPDateWindow(today.minusDays(27), today)
        EightWeeks -> GPDateWindow(today.minusDays(55), today)
        TwelveWeeks -> GPDateWindow(today.minusDays(83), today)
        SixMonths -> GPDateWindow(today.minusMonths(6), today)
        is Custom -> GPDateWindow(minOf(start, end), maxOf(start, end))
    }

    // Getters, not stored values: the companion is built while the nested objects are still
    // being initialised, so a stored list would hold nulls.
    companion object {
        val default: GPPeriod get() = TwelveWeeks

        /** The pickable presets, in spec order. Custom is offered separately. */
        val presets: List<GPPeriod> get() = listOf(FourWeeks, EightWeeks, TwelveWeeks, SixMonths)
    }
}

/** An inclusive day range plus the equal-length range immediately before it. */
data class GPDateWindow(val start: LocalDate, val end: LocalDate) {
    /** Inclusive day count. */
    val dayCount: Int = maxOf(ChronoUnit.DAYS.between(start, end).toInt() + 1, 1)
    val previousEnd: LocalDate = start.minusDays(1)
    val previousStart: LocalDate = start.minusDays(dayCount.toLong())

    fun contains(day: LocalDate): Boolean = day in start..end
    fun previousContains(day: LocalDate): Boolean = day in previousStart..previousEnd
}

/**
 * The auto-populated sections she can remove at the review step. Period dates and check-in
 * counts are the identifying frame and stay in; everything here is optional.
 */
enum class GPSummarySection { CYCLE, SLEEP, ENERGY, MOOD, ALCOHOL }

/**
 * Everything she adds at step 3, plus what she chose to remove at step 2. Kept separate from
 * the store-derived data so the builder never invents any of it.
 */
data class GPSummaryInputs(
    val period: GPPeriod = GPPeriod.default,
    /** Off by default (spec). */
    val includeName: Boolean = false,
    val includeAge: Boolean = false,
    /** Off by default: only if she turns it on (3C). */
    val includeHysterectomy: Boolean = false,
    /** Up to 3, her words, in her order. */
    val priorities: List<String> = emptyList(),
    /** Selected fixed labels. */
    val impactAreas: List<String> = emptyList(),
    /** Her 60-character free text (verbatim). */
    val impactOther: String = "",
    /** "Mild" / "Moderate" / "Significant". */
    val impactOverall: String? = null,
    /** Up to 3, her words. */
    val questions: List<String> = emptyList(),
    val removedSymptomNames: Set<String> = emptySet(),
    val removedMedIDs: Set<String> = emptySet(),
    val removedSections: Set<GPSummarySection> = emptySet(),
)

/**
 * One symptom's recorded counts for the summary period and the equal-length window before
 * it. `meanSeverity` and `lastLogged` only break ranking ties; nothing is scored or shown as
 * severity.
 */
data class GPSymptomStat(
    val name: String,
    val isCustom: Boolean,
    val daysThisPeriod: Int,
    val daysPreviousPeriod: Int,
    /** Mean of the severities she recorded, tie-break only. */
    val meanSeverity: Double,
    /** Her most recent log this period, tie-break only. */
    val lastLogged: Instant,
)

/** The "Symptoms I logged most often" section, ready to render. */
data class GPSymptomTable(
    val rows: List<Row>,
    val showsPreviousColumn: Boolean,
    /** Printed in place of the previous column when the earlier window is too sparse. */
    val previousUnavailableNote: String?,
    /** "Also recorded: a, b, c." for symptoms that fell outside the top rows. */
    val alsoRecorded: String?,
) {
    data class Row(
        /** Full label; the PDF truncates to 40 at render. */
        val name: String,
        /** "31 of 64 check-in days" (denominator required). */
        val thisPeriod: String,
        /** "20 of 48 check-in days"; null when the column is hidden. */
        val previousPeriod: String?,
    )

    /** True when nothing was recorded; the PDF prints the empty-state line instead. */
    val isEmpty: Boolean get() = rows.isEmpty()
}

/** Whether she recorded bleeding or spotting between periods. */
enum class GPBleeding(val label: String) { YES("Yes"), NO("No"), NOT_RECORDED("Not recorded") }

/**
 * The page-1 "Periods and cycle" block. Never labels the cycle regular, irregular, normal or
 * abnormal, and never flags bleeding; it only restates what she recorded.
 */
data class GPCycleBlock(
    /** Null renders as "not recorded". */
    val lastPeriodStart: LocalDate?,
    val periodsRecorded: Int,
    /** "28 to 34 days", or the not-enough note. */
    val cycleLengthRange: String,
    /** Most-recorded descriptor, her words. */
    val flow: String?,
    val intermenstrualBleeding: String,
    /** Her reason, only if she entered one. */
    val notApplicable: String?,
)

/**
 * Which of the three page-2 tables a medicine belongs in. Grouping only, never a judgement:
 * MHT is the hormonal treatment groups, other prescriptions the rest of the treatments, and
 * supplements are supplements.
 */
enum class GPMedCategory { MHT, OTHER_PRESCRIBED, SUPPLEMENT }

/** One medicine, reduced to the strings and dates the tables print. `dose` is her text, verbatim. */
data class GPMedInput(
    val name: String,
    /** Her words, verbatim; null renders as "not recorded". */
    val dose: String?,
    /** Schedule or timing text (other and supplement tables). */
    val frequency: String?,
    /** Dates she entered, if any. */
    val started: Instant?,
    val doseChangedAt: Instant?,
    val stoppedAt: Instant?,
    val category: GPMedCategory,
)

data class GPMedRow(
    /** Name or treatment. */
    val col1: String,
    /** Dose (verbatim) or "not recorded". */
    val col2: String,
    /** MHT: started or last changed (+ "Stopped [date]"); otherwise frequency. */
    val col3: String,
)

data class GPMedTable(
    val rows: List<GPMedRow>,
    /** "and 3 further items recorded in Keel" when the category exceeds the row cap. */
    val overflowNote: String?,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

/** An active treatment as a removable row on the review step. */
data class GPReviewMed(val id: String, val name: String, val category: GPMedCategory)

/** Which "About me" opt-ins to offer: only what she has actually recorded. */
data class GPAboutMe(val hasName: Boolean = false, val hasAge: Boolean = false, val hasHysterectomyAnswer: Boolean = false)

/**
 * One entered change to her treatment, restated for the dated list. Never linked to a
 * symptom. The template words are Keel's; the medicine name is her words.
 */
data class GPTreatmentChange(val date: Instant, val medName: String, val kind: Kind) {
    enum class Kind { DOSE_CHANGED, STOPPED }

    val description: String
        get() = when (kind) {
            Kind.DOSE_CHANGED -> "changed $medName dose"
            Kind.STOPPED -> "stopped $medName"
        }
}

/**
 * The fully resolved summary, ready to render. The preview and the PDF both read from this
 * one value so they can't drift. Every field is either retrieved from her records or typed by
 * her; nothing is inferred.
 */
data class GPSummaryDocument(
    // About me
    val name: String?,
    val age: Int?,
    /** "yes, one ovary removed (as recorded by her)"; only when she turned it on. */
    val hysterectomyLine: String? = null,
    /** "18 May to 16 August 2026". */
    val periodLabel: String,
    /** "64 of 90 days". */
    val checkInsLabel: String,

    // What I most want help with today
    val priorities: List<String>,

    // Symptoms I logged most often: kept as stats so the renderer can reduce rows to fit
    // page 1 (6 → 5 → 4) without losing data to the "Also recorded" line.
    val symptomStats: List<GPSymptomStat>,
    val checkInDaysThisPeriod: Int,
    val checkInDaysPreviousPeriod: Int,
    val previousWindowDayCount: Int,
    /** 6 normally; 5 when all three priority lines are used (spec overflow rule). */
    val defaultSymptomMaxRows: Int,

    // How this is affecting me
    val impactLine: String?,

    // Periods and cycle
    val cycle: GPCycleBlock,

    // Page 2: treatment
    val mht: GPMedTable,
    val otherMeds: GPMedTable,
    val supplements: GPMedTable,
    val treatmentChanges: List<String>,

    // Sleep, energy and mood
    val sleepLine: String,
    val energyLine: String?,
    val moodLine: String?,
    /** Null when she recorded no alcohol in the period (the row is then left out). */
    val alcoholLine: String? = null,

    // Questions I want to discuss
    val questions: List<String>,

    /** Her day when she prepared it (footer and file name). */
    val generatedOn: LocalDate,

    // Removable auto-populated sections (in by default; the review step can turn any off).
    val includeCycle: Boolean = true,
    val includeSleep: Boolean = true,
    val includeEnergy: Boolean = true,
    val includeMood: Boolean = true,
    val includeAlcohol: Boolean = true,
) {
    /** The symptom section at a chosen row cap (the renderer calls this with what fits). */
    fun symptomTable(maxRows: Int): GPSymptomTable = GPSummaryBuilder.symptomTable(
        stats = symptomStats,
        checkInDaysThisPeriod = checkInDaysThisPeriod,
        checkInDaysPreviousPeriod = checkInDaysPreviousPeriod,
        previousWindowDayCount = previousWindowDayCount,
        maxRows = maxRows,
    )
}

object GPSummaryBuilder {
    /** Like iOS `localizedCaseInsensitiveCompare`: her locale's order, case ignored. */
    private val byName: Comparator<String>
        get() = Collator.getInstance().apply { strength = Collator.SECONDARY }.let { c -> Comparator { a, b -> c.compare(a, b) } }

    /**
     * Rank by days logged, then higher mean recorded severity, then most recent log, then
     * name. Shared by the table and the review step so both order identically.
     */
    fun ranked(stats: List<GPSymptomStat>): List<GPSymptomStat> {
        val names = byName
        return stats.sortedWith { a, b ->
            when {
                a.daysThisPeriod != b.daysThisPeriod -> b.daysThisPeriod.compareTo(a.daysThisPeriod)
                a.meanSeverity != b.meanSeverity -> b.meanSeverity.compareTo(a.meanSeverity)
                a.lastLogged != b.lastLogged -> b.lastLogged.compareTo(a.lastLogged)
                else -> names.compare(a.name, b.name)
            }
        }
    }

    /**
     * The symptom section, exactly per spec: ranked; denominators always shown; the
     * previous-period column only when that window is at least half checked in; and every
     * other symptom recorded this period falls to an alphabetical "Also recorded" line. No
     * arrows, trend words or interpretation.
     */
    fun symptomTable(
        stats: List<GPSymptomStat>,
        checkInDaysThisPeriod: Int,
        checkInDaysPreviousPeriod: Int,
        previousWindowDayCount: Int,
        maxRows: Int = 6,
    ): GPSymptomTable {
        val recorded = stats.filter { it.daysThisPeriod > 0 }
        val showPrevious = previousWindowDayCount > 0 &&
            checkInDaysPreviousPeriod.toDouble() >= 0.5 * previousWindowDayCount
        val ranked = ranked(recorded)
        val top = ranked.take(maxOf(maxRows, 0))
        val rows = top.map { stat ->
            GPSymptomTable.Row(
                name = stat.name,
                thisPeriod = "${stat.daysThisPeriod} of $checkInDaysThisPeriod check-in days",
                previousPeriod = if (showPrevious) "${stat.daysPreviousPeriod} of $checkInDaysPreviousPeriod check-in days" else null,
            )
        }
        // Beyond the top rows: every other symptom she kept and recorded this period, listed
        // alphabetically, so nothing she chose to include is silently dropped.
        val overflow = ranked.drop(top.size).map { it.name }.sortedWith(byName)
        val alsoRecorded = if (overflow.isEmpty()) null else "Also recorded: ${overflow.joinToString(", ")}."
        return GPSymptomTable(
            rows = rows,
            showsPreviousColumn = showPrevious,
            previousUnavailableNote = if (showPrevious) null else GPSummaryCopy.noEarlierComparison,
            alsoRecorded = alsoRecorded,
        )
    }

    fun cycleBlock(
        periodStartsInPeriod: List<LocalDate>,
        lastRecordedStart: LocalDate?,
        mostFrequentFlow: String?,
        intermenstrualBleeding: GPBleeding,
        notApplicableReason: String?,
    ): GPCycleBlock {
        val starts = periodStartsInPeriod.sorted()
        // A range needs at least three recorded starts (two consecutive gaps).
        var rangeText = GPSummaryCopy.notEnoughPeriodsForRange
        if (starts.size >= 3) {
            val gaps = starts.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }
            val lo = gaps.min()
            val hi = gaps.max()
            rangeText = if (lo == hi) "$lo ${if (lo == 1) "day" else "days"}" else "$lo to $hi days"
        }
        return GPCycleBlock(
            lastPeriodStart = lastRecordedStart,
            periodsRecorded = starts.size,
            cycleLengthRange = rangeText,
            flow = mostFrequentFlow?.trim()?.ifEmpty { null },
            intermenstrualBleeding = intermenstrualBleeding.label,
            notApplicable = notApplicableReason?.trim()?.ifEmpty { null },
        )
    }

    // Sleep, energy and mood: three plain counts, no charts, her own entries only.

    /** "41 of 64": nights she logged disrupted sleep, out of nights with a check-in. */
    fun sleepLine(disruptedNights: Int, checkInNights: Int): String = "$disruptedNights of $checkInNights"

    /** "average 4.2 of 10, from 58 entries": the mean of what she entered. Null with none. */
    fun energyLine(mean: Double, entryCount: Int, scaleMax: Int = 10): String? {
        if (entryCount <= 0) return null
        return "average ${formatOneDecimal(mean)} of $scaleMax, from $entryCount ${if (entryCount == 1) "entry" else "entries"}"
    }

    /**
     * "recorded on 6 of 24 check-in days": the days she saved an alcohol number (zero
     * included) out of her check-in days. Null when she recorded none, so a day left blank is
     * never read as alcohol-free and the line never appears for someone who doesn't use it.
     */
    fun alcoholLine(recordedDays: Int, checkInDays: Int): String? {
        if (recordedDays <= 0 || checkInDays <= 0) return null
        return "recorded on $recordedDays of $checkInDays check-in ${if (checkInDays == 1) "day" else "days"}"
    }

    /** The three most-logged mood labels with counts, e.g. "Okay 20, Good 12, Low 5". */
    fun moodLine(topMoods: List<Pair<String, Int>>): String? {
        val top = topMoods.take(3)
        if (top.isEmpty()) return null
        return top.joinToString(", ") { "${it.first} ${it.second}" }
    }

    // Medication tables (page 2)

    /** Catalog groups that make a treatment MHT; every other treatment is "other prescribed". */
    val mhtGroupIDs: Set<String> = setOf("oestrogen", "progesterone", "combined", "testosterone")

    /**
     * One of the three med tables. Rows print her entries verbatim; caps at [maxRows], then
     * says "and [n] further items recorded in Keel" rather than dropping data.
     */
    fun medTable(meds: List<GPMedInput>, category: GPMedCategory, maxRows: Int = 10, dateStyle: (Instant) -> String): GPMedTable {
        val inCategory = meds.filter { it.category == category }
        val capped = inCategory.take(maxRows)
        val rows = capped.map { med ->
            val dose = med.dose?.ifEmpty { null } ?: GPSummaryCopy.notRecorded
            val col3 = when (category) {
                GPMedCategory.MHT -> {
                    val parts = mutableListOf<String>()
                    (med.doseChangedAt ?: med.started)?.let { parts += dateStyle(it) }
                    med.stoppedAt?.let { parts += "Stopped ${dateStyle(it)}" }
                    if (parts.isEmpty()) parts += GPSummaryCopy.notRecorded
                    parts.joinToString(" · ")
                }
                GPMedCategory.OTHER_PRESCRIBED, GPMedCategory.SUPPLEMENT -> med.frequency?.ifEmpty { null } ?: GPSummaryCopy.notRecorded
            }
            GPMedRow(med.name, dose, col3)
        }
        val extra = inCategory.size - capped.size
        val note = if (extra > 0) "and $extra further ${if (extra == 1) "item" else "items"} recorded in Keel" else null
        return GPMedTable(rows, note)
    }

    /**
     * The dated "Changes to my treatment" list: most recent first, capped, one line each, e.g.
     * "14 July 2026: changed oestradiol gel dose". Never pairs a change with a symptom.
     */
    fun treatmentChanges(changes: List<GPTreatmentChange>, maxRows: Int = 8, dateStyle: (Instant) -> String): List<String> =
        changes.sortedByDescending { it.date }.take(maxRows).map { "${dateStyle(it.date)}: ${it.description}" }

    /**
     * The compact "How this is affecting me" line, e.g. "Affecting: sleep, work or
     * concentration. Overall impact: Significant." Null when she selected nothing. Never
     * styled as a score or scale.
     */
    fun impactLine(areas: List<String>, overall: String?): String? {
        val cleaned = areas.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty() && overall == null) return null
        var line = ""
        if (cleaned.isNotEmpty()) line += "Affecting: ${cleaned.joinToString(", ")}."
        if (overall != null) {
            if (line.isNotEmpty()) line += " "
            line += "Overall impact: $overall."
        }
        return line
    }

    /**
     * Whether a symptom counts toward the "nights disrupted sleep" line: the built-in sleep
     * symptoms and any custom label she named with "sleep". A name match, never an inference.
     */
    fun isSleepDisruptionSymptom(name: String): Boolean {
        val lowered = name.lowercase()
        return "sleep" in lowered || lowered == "insomnia"
    }

    /**
     * "Bleeding or spotting between periods", decided mechanically from what she recorded. A
     * spotting day counts as between periods only when neither the day before nor after is a
     * menstruation day (adjacency, not a pattern). Yes: at least one standalone spotting day;
     * No: cycle data but no standalone spotting; Not recorded: no cycle entries at all.
     */
    fun interPeriodBleeding(menstruationDays: List<LocalDate>, spottingDays: List<LocalDate>): GPBleeding {
        if (menstruationDays.isEmpty() && spottingDays.isEmpty()) return GPBleeding.NOT_RECORDED
        val period = menstruationDays.toSet()
        val standalone = spottingDays.any { it !in period && it.minusDays(1) !in period && it.plusDays(1) !in period }
        return if (standalone) GPBleeding.YES else GPBleeding.NO
    }
}
