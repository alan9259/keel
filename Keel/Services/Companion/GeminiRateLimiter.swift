import Foundation

/// Gemini free-tier request budget. The numbers change, so keep them here and
/// verify against Google's current rate-limit page at build time. Defaults are
/// for `gemini-2.5-flash` on the free tier.
struct GeminiFreeTier: Sendable {
    var requestsPerMinute: Int
    var requestsPerDay: Int

    /// gemini-2.5-flash, free tier. Verify before each release.
    static let flash25 = GeminiFreeTier(requestsPerMinute: 10, requestsPerDay: 250)
}

/// Client-side courtesy limiter that keeps the companion inside Gemini's free-tier
/// budget. On-device Apple Intelligence carries most traffic, so this mainly bites
/// on devices without it, where every reply goes to Gemini.
///
/// This is a courtesy, not a guarantee: real enforcement belongs on the server
/// proxy that holds the key. The per-minute window is in memory; the per-day count
/// is persisted so it survives a relaunch and resets at local midnight.
actor GeminiRateLimiter {
    private let tier: GeminiFreeTier
    private let defaults: UserDefaults
    private var minuteWindow: [Date] = []

    private let dayKey = "keel.gemini.rpd.day"
    private let countKey = "keel.gemini.rpd.count"

    init(tier: GeminiFreeTier = .flash25, defaults: UserDefaults = .standard) {
        self.tier = tier
        self.defaults = defaults
    }

    /// Reserve one request, or throw `ChatError.rateLimited` with how long to wait.
    /// Every network round-trip (including a tool-loop continuation) reserves one.
    func reserve(now: Date = .now) throws {
        minuteWindow.removeAll { now.timeIntervalSince($0) >= 60 }
        if minuteWindow.count >= tier.requestsPerMinute {
            let oldest = minuteWindow.min() ?? now
            throw ChatError.rateLimited(retryAfter: max(60 - now.timeIntervalSince(oldest), 1))
        }

        let today = Self.dayStamp(now)
        var count = dayCount(today: today)
        if count >= tier.requestsPerDay {
            throw ChatError.rateLimited(retryAfter: Self.secondsUntilMidnight(from: now))
        }

        minuteWindow.append(now)
        count += 1
        defaults.set(today, forKey: dayKey)
        defaults.set(count, forKey: countKey)
    }

    /// Remaining requests today, for diagnostics/tests.
    func remainingToday(now: Date = .now) -> Int {
        max(tier.requestsPerDay - dayCount(today: Self.dayStamp(now)), 0)
    }

    private func dayCount(today: String) -> Int {
        guard defaults.string(forKey: dayKey) == today else { return 0 }
        return defaults.integer(forKey: countKey)
    }

    private static func dayStamp(_ date: Date) -> String {
        let c = Calendar.current.dateComponents([.year, .month, .day], from: date)
        return "\(c.year ?? 0)-\(c.month ?? 0)-\(c.day ?? 0)"
    }

    private static func secondsUntilMidnight(from date: Date) -> TimeInterval {
        let cal = Calendar.current
        guard let next = cal.nextDate(after: date, matching: DateComponents(hour: 0, minute: 0, second: 0),
                                      matchingPolicy: .nextTime) else { return 3600 }
        return max(next.timeIntervalSince(date), 1)
    }
}
