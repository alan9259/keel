import Foundation

/// One thing she can note about her eating today, logged as a plain yes/no. Nothing
/// here is scored, counted, or labelled good or bad; it exists so Keel can notice
/// what tends to go with how she feels.
struct EatingItem: Identifiable, Equatable {
    let id: String      // stable log key, e.g. "eat.alcohol"
    let label: String   // "Alcohol"
}

/// The eating panel's items, split into gentle nourishment cues and the handful of
/// well-evidenced symptom triggers (alcohol, caffeine, spicy food). All optional,
/// nothing pre-selected. The `id`s are the `ActivityLog.activityID`s she logs under.
enum EatingCatalog {
    /// Positive, perimenopause-relevant nourishment she can tick.
    static let nourishment: [EatingItem] = [
        EatingItem(id: "eat.protein", label: "Protein"),
        EatingItem(id: "eat.veg", label: "Fruit & veg"),
        EatingItem(id: "eat.calcium", label: "Calcium-rich food"),
        EatingItem(id: "eat.wholegrains", label: "Wholegrains & fibre"),
    ]

    /// Things that can nudge hot flushes, sleep and palpitations for some women. Kept
    /// neutral ("notice", never "avoid").
    static let triggers: [EatingItem] = [
        EatingItem(id: "eat.alcohol", label: "Alcohol"),
        EatingItem(id: "eat.caffeine", label: "Caffeine"),
        EatingItem(id: "eat.spicy", label: "Spicy food"),
    ]

    static var all: [EatingItem] { nourishment + triggers }
}
