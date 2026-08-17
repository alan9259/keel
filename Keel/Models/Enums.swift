import Foundation

/// How the user is approaching perimenopause (onboarding pathway selection).
enum Pathway: String, CaseIterable, Codable, Identifiable {
    case natural
    case medical
    case both
    case figuring

    var id: String { rawValue }

    var title: String {
        switch self {
        case .natural: "Naturally & Self-Directed"
        case .medical: "GP / HRT / Specialist Supported"
        case .both: "A Bit of Both"
        case .figuring: "Still Figuring It Out"
        }
    }

    var detail: String {
        switch self {
        case .natural: "Managing with lifestyle, supplements, and natural approaches. No HRT, or not yet."
        case .medical: "Working with a GP or menopause specialist, with or without HRT."
        case .both: "Combining natural approaches with medical support. Most women land here."
        case .figuring: "Not sure yet, and that's fine. Keel will help you understand your options."
        }
    }
}

/// Gentle grouping for symptoms in the "more" picker. Raw values are stable and
/// stored on `Symptom.categoryRaw`, so the older names (`sleep`, `body`,
/// `cognition`, `mood`, `digestion`) stay as-is even though their labels changed.
enum SymptomCategory: String, CaseIterable, Codable, Identifiable {
    case sleep
    case energy
    case body
    case aches
    case cognition
    case mood
    case skin
    case digestion
    case intimacy

    var id: String { rawValue }

    var label: String {
        switch self {
        case .sleep: "Sleep & rest"
        case .energy: "Energy"
        case .body: "Body & temperature"
        case .aches: "Aches & joints"
        case .cognition: "Head & thinking"
        case .mood: "Mood & self"
        case .skin: "Skin, hair & eyes"
        case .digestion: "Tummy & appetite"
        case .intimacy: "Intimacy & bladder"
        }
    }

    /// Display order in the "more" picker.
    var sortOrder: Int {
        switch self {
        case .sleep: 0
        case .energy: 1
        case .body: 2
        case .aches: 3
        case .cognition: 4
        case .mood: 5
        case .skin: 6
        case .digestion: 7
        case .intimacy: 8
        }
    }

    /// Private by default: the group stays folded away until she opts in.
    var isSensitive: Bool { self == .intimacy }

    /// Soft framing shown above a sensitive group.
    var intro: String? {
        switch self {
        case .intimacy:
            "These ones are rarely talked about, and very common. Share only what feels relevant to you."
        default:
            nil
        }
    }

    // MARK: Custom categories
    //
    // A symptom's category is stored as a free `categoryRaw` string. Built-in
    // categories use the lowercase case names above; user-created categories
    // store their display name directly as the raw value. These helpers let the
    // UI group and label by raw string without caring which kind it is.

    /// Display label for any stored category raw value (built-in or custom).
    static func label(forRaw raw: String) -> String {
        SymptomCategory(rawValue: raw)?.label ?? raw
    }

    /// Sort key: built-ins keep their order; custom categories sort after them.
    static func sortOrder(forRaw raw: String) -> Int {
        SymptomCategory(rawValue: raw)?.sortOrder ?? 100
    }
}

/// A logged cycle event. Perimenopausal cycles are irregular, so this is an
/// event log rather than a fixed 28-day model.
enum CycleEntryType: String, Codable {
    case periodStart
    case periodEnd
    case flow
}

/// How heavy a period day was, mirroring Apple Health's menstrual-flow levels
/// (`HKCategoryValueVaginalBleeding`). A day with no entry is simply not a period
/// day; a `.unspecified` entry is a period day she logged without a level.
enum FlowLevel: String, Codable, CaseIterable, Identifiable {
    case spotting
    case light
    case medium
    case heavy
    case unspecified

    var id: String { rawValue }

    var label: String {
        switch self {
        case .spotting: "Spotting"
        case .light: "Light"
        case .medium: "Medium"
        case .heavy: "Heavy"
        case .unspecified: "Period"
        }
    }

    /// 1...4 weight for the timeline fill (spotting lightest, heavy darkest); a
    /// plain period reads at the solid mid weight.
    var intensity: Int {
        switch self {
        case .spotting: 1
        case .light: 2
        case .medium: 3
        case .heavy: 4
        case .unspecified: 3
        }
    }

    /// The order shown in the log sheet.
    static let loggingOrder: [FlowLevel] = [.spotting, .light, .medium, .heavy]
}

/// Where a logged record came from. Defaults to `manual` (she logged it); Apple
/// Health imports are tagged `healthKit` so we keep provenance and never
/// double-count her own entries against imported ones.
enum DataSource: String, Codable {
    case manual
    case healthKit
}

/// Estimated cycle phase (display only; acknowledges irregularity).
enum CyclePhase: String, Codable {
    case menstrual
    case follicular
    case ovulation
    case luteal
    case unknown

    var label: String {
        switch self {
        case .menstrual: "Menstrual"
        case .follicular: "Follicular"
        case .ovulation: "Ovulation"
        case .luteal: "Luteal"
        case .unknown: "Learning"
        }
    }
}

/// HRT / delivery method for a medication or supplement.
enum MedicationMethod: String, CaseIterable, Codable, Identifiable {
    case patch
    case gel
    case spray
    case tablet
    case capsule
    case vaginal
    case iud
    case cream
    case other

    var id: String { rawValue }

    var label: String {
        switch self {
        case .patch: "Patch"
        case .gel: "Gel"
        case .spray: "Spray"
        case .tablet: "Tablet (oral)"
        case .capsule: "Capsule"
        case .vaginal: "Vaginal cream, pessary or ring"
        case .iud: "IUD or IUS"
        case .cream: "Cream (topical)"
        case .other: "Other"
        }
    }
}

/// Whether an entry is a prescribed treatment (HRT and the like) or something
/// she has added to her own stack. Only ever used to group the list, never to
/// rank or judge what she takes.
enum TreatmentKind: String, CaseIterable, Codable, Identifiable {
    case treatment
    case supplement

    var id: String { rawValue }

    var label: String {
        switch self {
        case .treatment: "Treatments"
        case .supplement: "Supplements"
        }
    }

    /// Heading for the picker's one-at-a-time toggle. "Prescriptions" rather than
    /// "HRT" because not everything prescribed is HRT.
    var shortLabel: String {
        switch self {
        case .treatment: "Prescriptions"
        case .supplement: "Supplements"
        }
    }
}

/// Unit for a dose or strength. Broad on purpose: strengths vary by brand and
/// by shortage substitution, so this covers weights, volumes and whole items.
/// Nothing here is ever pre-selected for her.
enum DoseUnit: String, CaseIterable, Codable, Identifiable {
    case mg
    case mcg
    case g
    case iu
    case ml
    case percent

    var id: String { rawValue }

    var label: String {
        switch self {
        case .mg: "mg"
        case .mcg: "mcg"
        case .g: "g"
        case .iu: "IU"
        case .ml: "ml"
        case .percent: "%"
        }
    }

    func format(_ amount: Double) -> String {
        let number = amount == amount.rounded() ? String(Int(amount)) : String(amount)
        // "400mg" reads as one word; "2000 IU" doesn't.
        return self == .iu ? "\(number) \(label)" : "\(number)\(label)"
    }
}

/// When in the day she takes it.
enum TimeOfDay: String, CaseIterable, Codable, Identifiable {
    case morning
    case afternoon
    case evening

    var id: String { rawValue }

    var label: String {
        switch self {
        case .morning: "Morning"
        case .afternoon: "Afternoon"
        case .evening: "Evening"
        }
    }
}

/// How often she takes something. Free enough to cover a patch changed twice a
/// week and a tablet taken part of the month.
enum DoseFrequency: String, CaseIterable, Codable, Identifiable {
    case daily
    case twiceWeekly
    case weekly
    case cyclic
    case asDirected

    var id: String { rawValue }

    var label: String {
        switch self {
        case .daily: "Daily"
        case .twiceWeekly: "Twice weekly"
        case .weekly: "Weekly"
        case .cyclic: "Cyclic (part of the month)"
        case .asDirected: "As directed"
        }
    }

    /// Compact form for list rows.
    var shortLabel: String {
        switch self {
        case .cyclic: "Cyclic"
        default: label
        }
    }
}
