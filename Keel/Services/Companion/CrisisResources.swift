import Foundation

/// A single support line she can reach right now.
struct SupportContact: Sendable, Identifiable {
    var id: String { name }
    let name: String
    let contact: String
    /// Short qualifier, e.g. "call or text" or who it is for.
    let note: String?
}

/// The support set for one country. Both AU and NZ ship, so nothing is ever
/// hard-coded to a single country.
struct SupportRegion: Sendable, Identifiable {
    var id: String { code }
    let code: String
    let name: String
    let emergency: String
    let contacts: [SupportContact]
}

/// Verified crisis and support contacts, kept as data so the companion prompt and
/// any future UI read one source of truth.
///
/// The safety section of the companion surfaces the set matching her region. Show
/// the local emergency number too. Contact details change, so verify every entry
/// against the official source before each release.
enum CrisisResources {
    static let australia = SupportRegion(
        code: "AU",
        name: "Australia",
        emergency: "000",
        contacts: [
            SupportContact(name: "Lifeline", contact: "13 11 14", note: "call or text, 24 hours"),
            SupportContact(name: "Beyond Blue", contact: "1300 22 4636", note: nil),
            SupportContact(name: "13YARN", contact: "13 92 76", note: "for First Nations people"),
        ]
    )

    static let newZealand = SupportRegion(
        code: "NZ",
        name: "New Zealand",
        emergency: "111",
        contacts: [
            SupportContact(name: "Need to talk?", contact: "1737", note: "free call or text"),
            SupportContact(name: "Healthline", contact: "0800 611 116", note: nil),
        ]
    )

    static let all: [SupportRegion] = [australia, newZealand]

    /// The region matching her device. For crisis lines, where she physically is
    /// matters more than her formatting region (a device set to en-NZ while she's
    /// in Australia should still get Australian numbers), so the time zone leads and
    /// the locale region is the fallback. Anything other than AU/NZ shows both, so
    /// no one is left without a number.
    static func matching(locale: Locale = .current, timeZone: TimeZone = .current) -> [SupportRegion] {
        let tz = timeZone.identifier
        if tz.hasPrefix("Australia/") { return [australia] }
        if tz == "Pacific/Auckland" || tz == "Pacific/Chatham" { return [newZealand] }
        switch locale.region?.identifier {
        case "AU": return [australia]
        case "NZ": return [newZealand]
        default: return all
        }
    }

    /// Plain-text block folded into the system prompt so the model can name the
    /// right lines for her region rather than inventing them.
    static func promptBlock(locale: Locale = .current) -> String {
        var lines = ["Support resources you may show her, matched to her region:"]
        for region in matching(locale: locale) {
            lines.append("\(region.name) (emergency: \(region.emergency)):")
            for c in region.contacts {
                if let note = c.note {
                    lines.append("  \(c.name): \(c.contact) (\(note))")
                } else {
                    lines.append("  \(c.name): \(c.contact)")
                }
            }
        }
        return lines.joined(separator: "\n")
    }
}
