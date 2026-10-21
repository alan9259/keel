package com.therecalibrationyears.keel.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Port of iOS `CycleEntryType` (raw values kept identical). */
enum class CycleEntryType(val raw: String) {
    PERIOD_START("periodStart"), PERIOD_END("periodEnd"), FLOW("flow");

    companion object {
        /** An unknown raw value reads as `flow`, as on iOS. */
        fun fromRaw(raw: String?): CycleEntryType = entries.firstOrNull { it.raw == raw } ?: FLOW
    }
}

/**
 * How heavy a period day was (port of iOS `FlowLevel`, the Apple Health levels). A day with
 * no entry is simply not a period day; `UNSPECIFIED` is a period day logged without a level.
 */
enum class FlowLevel(val raw: String, val label: String, val intensity: Int) {
    SPOTTING("spotting", "Spotting", 1),
    LIGHT("light", "Light", 2),
    MEDIUM("medium", "Medium", 3),
    HEAVY("heavy", "Heavy", 4),
    UNSPECIFIED("unspecified", "Period", 3);

    companion object {
        /** No stored level (or an unknown one) reads as a plain period day. */
        fun fromRaw(raw: String?): FlowLevel = entries.firstOrNull { it.raw == raw } ?: UNSPECIFIED

        /** The order shown in the log sheet. */
        val loggingOrder = listOf(SPOTTING, LIGHT, MEDIUM, HEAVY)
    }
}

/** Estimated cycle phase (port of iOS `CyclePhase`; display only, acknowledges irregularity). */
enum class CyclePhase(val raw: String, val label: String) {
    MENSTRUAL("menstrual", "Menstrual"),
    FOLLICULAR("follicular", "Follicular"),
    OVULATION("ovulation", "Ovulation"),
    LUTEAL("luteal", "Luteal"),
    UNKNOWN("unknown", "Learning"),
}

/**
 * Cycle arithmetic over her own logged period starts (port of iOS `CycleStats`): cycle
 * lengths, a typical range, the day of the current cycle, and a gently gated estimate of the
 * next period. Days are `LocalDate`s in her zone, so nothing depends on the wall clock.
 *
 * The estimate is deliberately conservative. In perimenopause cycles are often irregular, so
 * it is only ever a window, and only when the recent cycles are consistent enough to mean
 * something. Every figure is derived from her data.
 */
data class CycleStats(
    /** Ascending, de-duplicated period-start days. */
    val starts: List<LocalDate>,
) {
    /** Length in days between each consecutive pair of starts, oldest first. */
    val cycleLengths: List<Int> get() = starts.zipWithNext { a, b -> days(a, b) }

    /** The most recent cycle lengths (up to [RECENT_WINDOW]), newest last. */
    val recentLengths: List<Int> get() = cycleLengths.takeLast(RECENT_WINDOW)

    val lastStart: LocalDate? get() = starts.lastOrNull()

    /** 1-based day of the current cycle on [date], or null before the last start. */
    fun cycleDay(date: LocalDate): Int? {
        val last = lastStart ?: return null
        val d = days(last, date)
        return if (d >= 0) d + 1 else null
    }

    /** The span her recent cycles have run (e.g. 24..31), or null with fewer than two. */
    val typicalRange: IntRange?
        get() {
            val recent = recentLengths
            if (recent.size < 2) return null
            return recent.min()..recent.max()
        }

    /** The last few cycles, only if they're consistent enough to forecast from. */
    private val estimateBasis: List<Int>?
        get() {
            val recent = cycleLengths.takeLast(ESTIMATE_BASIS_COUNT)
            if (recent.size != ESTIMATE_BASIS_COUNT || recent.max() - recent.min() > MAX_SPREAD_DAYS) return null
            return recent
        }

    /** Whether a next-period estimate is trustworthy enough to show at all. */
    val canEstimate: Boolean get() = estimateBasis != null

    /** The window her next period is likely to fall in, or null when there's no consistency. */
    fun estimatedWindow(): ClosedRange<LocalDate>? {
        val basis = estimateBasis ?: return null
        val last = lastStart ?: return null
        return last.plusDays(basis.min().toLong())..last.plusDays(basis.max().toLong())
    }

    /**
     * The start of the cycle [date] falls in: the most recent start on or before it. Starts
     * exclude isolated spotting, so a stray spotting day or a backfilled older cycle can't
     * move this anchor.
     */
    fun currentCycleStart(date: LocalDate): LocalDate? = starts.lastOrNull { it <= date }

    /** Median of her recent cycle lengths (whole days, as iOS), or null before a full cycle. */
    val medianRecentLength: Int?
        get() {
            val sorted = recentLengths.sorted()
            if (sorted.isEmpty()) return null
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2 else sorted[mid]
        }

    /**
     * A gentle estimate of where she is in her cycle, anchored to the current cycle start and
     * scaled to her own length once there are a few cycles. A guide, not a prediction: it
     * stays `UNKNOWN` before a start and once she's well past a plausible cycle.
     */
    fun phase(date: LocalDate): CyclePhase {
        val start = currentCycleStart(date) ?: return CyclePhase.UNKNOWN
        val day = days(start, date)
        if (day < 0) return CyclePhase.UNKNOWN
        val med = medianRecentLength
        if (cycleLengths.size >= ESTIMATE_BASIS_COUNT && med != null && med >= 20) {
            val ovulation = maxOf(6, med - 14) // the luteal phase runs ~14 days to the next period
            return when {
                day < 5 -> CyclePhase.MENSTRUAL
                day < ovulation - 1 -> CyclePhase.FOLLICULAR
                day < ovulation + 2 -> CyclePhase.OVULATION
                day < med + 8 -> CyclePhase.LUTEAL
                else -> CyclePhase.UNKNOWN
            }
        }
        return when {
            day < 5 -> CyclePhase.MENSTRUAL
            day < 13 -> CyclePhase.FOLLICULAR
            day < 16 -> CyclePhase.OVULATION
            day < 40 -> CyclePhase.LUTEAL
            else -> CyclePhase.UNKNOWN
        }
    }

    companion object {
        /** Only a handful of recent cycles inform "typical"; older ones matter less. */
        const val RECENT_WINDOW = 6
        /** Cycles the estimate is based on. */
        const val ESTIMATE_BASIS_COUNT = 3
        /** Wider than this and the recent cycles are too variable to estimate from. */
        const val MAX_SPREAD_DAYS = 7

        /** A start is a period day whose previous day was not a period day. */
        fun periodStarts(days: Collection<LocalDate>): List<LocalDate> {
            val set = days.toSet()
            return set.filter { it.minusDays(1) !in set }.sorted()
        }

        /**
         * Stats from her logged cycle days (iOS `CycleRepository.stats`): real menstruation
         * only, so a period-end marker or a stray spotting day never starts a "cycle".
         */
        fun fromEntries(entries: Collection<Pair<LocalDate, CycleDay>>): CycleStats =
            CycleStats(periodStarts(entries.filter { (_, d) -> d.countsAsMenstruation }.map { it.first }))

        private fun days(from: LocalDate, to: LocalDate): Int = ChronoUnit.DAYS.between(from, to).toInt()
    }
}

/** A logged cycle entry's type and level, as the rules need them. */
data class CycleDay(val type: CycleEntryType, val level: FlowLevel) {
    /** Real menstruation: not a period-end marker, not spotting. */
    val countsAsMenstruation: Boolean get() = type != CycleEntryType.PERIOD_END && level != FlowLevel.SPOTTING
}

/** One day on the Cycle screen: the level shown, and whether it was imported (iOS `CycleDayMark`). */
data class CycleDayMark(val level: FlowLevel, val imported: Boolean) {
    companion object {
        /** Her own days and imported ones, one mark per day; where both have the day, hers wins. */
        fun merge(manual: Map<LocalDate, FlowLevel>, imported: Map<LocalDate, FlowLevel>): Map<LocalDate, CycleDayMark> =
            imported.mapValues { CycleDayMark(it.value, imported = true) } + manual.mapValues { CycleDayMark(it.value, imported = false) }
    }
}

/** The Cycle screen's timeline (iOS `CycleTrackingView` helpers). */
object CycleTimeline {
    /**
     * The timeline's days. With data it runs from the current cycle's start to just past
     * today or the estimated next period; with none, it's the last week ending today, so the
     * empty top pane still shows real days to log the first period into.
     */
    fun railBounds(lastStart: LocalDate?, estimateEnd: LocalDate?, today: LocalDate): ClosedRange<LocalDate> {
        if (lastStart == null) return today.minusDays(6)..today
        val maxEnd = maxOf(today, estimateEnd ?: today.plusDays(3))
        return lastStart..maxEnd.plusDays(1)
    }

    /** Every day in a range, in order. */
    fun days(range: ClosedRange<LocalDate>): List<LocalDate> =
        generateSequence(range.start) { it.plusDays(1) }.takeWhile { it <= range.endInclusive }.toList()
}

/** Port of iOS `InsightAccent` (raw values kept for backups). */
enum class InsightAccent(val raw: String) { TERRACOTTA("terracotta"), SAGE("sage"), WARM_GREY("warmGrey") }

/**
 * One grounded observation about her own data (port of iOS `PatternFinding`). Each finding
 * describes ONE measure: Keel never links two measures in a sentence (that reads as a claim
 * about cause), so V1 has only the cycle-length detector.
 */
data class PatternFinding(
    val kind: Kind,
    val title: String,
    val detail: String,
    val timeframe: String,
    /** The iOS SF Symbol name, kept as the icon key (backups store it on `Insight`). */
    val icon: String,
    val accent: InsightAccent,
) {
    enum class Kind(val raw: String) { CYCLE_VARIABILITY("cycleVariability") }
}

/**
 * Single-measure observations from her own logs (port of iOS `PatternEngine`): her real range
 * of cycle lengths. Build it with [build] from the last [WINDOW] days.
 */
data class PatternEngine(
    /** Days she checked in on (any time that day). */
    val checkInDays: List<LocalDate>,
    /** First day of each logged period run, ascending. */
    val periodStarts: List<LocalDate>,
) {
    fun findings(): List<PatternFinding> = listOfNotNull(cycleVariability())

    /** Distinct days she has checked in on. */
    val loggedDayCount: Int get() = checkInDays.toSet().size

    private fun cycleVariability(): PatternFinding? {
        if (periodStarts.size < 3) return null
        // Ignore obvious mis-logs so the range stays meaningful.
        val intervals = periodStarts.sorted().zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }.filter { it in 15..90 }
        if (intervals.size < 2) return null
        val lo = intervals.min()
        val hi = intervals.max()
        // A persistent 7+ day swing is a recognised early sign of the transition; report her real range.
        if (hi - lo < 7) return null
        return PatternFinding(
            kind = PatternFinding.Kind.CYCLE_VARIABILITY,
            title = "Your cycle length",
            detail = "Your recent cycles ranged from about $lo to $hi days apart. That's the kind of detail that can be useful to bring to your GP.",
            // The cycles measured are the gaps between starts, not the starts themselves.
            timeframe = "Across your last ${intervals.size} logged cycles",
            icon = "arrow.left.and.right",
            accent = InsightAccent.SAGE,
        )
    }

    companion object {
        /** Cycle variability needs several months, hence 120 days. */
        const val WINDOW = 120

        /**
         * From the last [window] days of her check-ins and cycle entries (iOS `build`). Same
         * rule as the cycle stats: real menstruation only.
         */
        fun build(checkInDays: List<LocalDate>, cycleDays: List<Pair<LocalDate, CycleDay>>, today: LocalDate, window: Int = WINDOW): PatternEngine {
            val floor = today.minusDays((window - 1).toLong())
            return PatternEngine(
                checkInDays = checkInDays.filter { it >= floor },
                periodStarts = CycleStats.periodStarts(cycleDays.filter { (day, d) -> day >= floor && d.countsAsMenstruation }.map { it.first }),
            )
        }
    }
}
