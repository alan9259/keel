import XCTest
import SwiftData
@testable import Keel

/// The daily reflection must never reassure or normalise a symptom (a tester flagged
/// "there's no reason to worry... perfectly normal" for headaches). The AI narration
/// is filtered, and any such text already stored is retired on refresh.
@MainActor
final class ReflectionSafetyTests: XCTestCase {

    func testOffersReassuranceCatchesNormalisingLanguage() {
        let unsafe = [
            "Headaches have become your most frequent symptom lately, and there's no reason to worry about that. They're perfectly normal.",
            "This is completely normal and nothing to be concerned about.",
            "Rest assured, hot flushes like these are harmless.",
            "You're fine, there is no need to worry.",
            "Do not be alarmed, this is a benign change.",
        ]
        for text in unsafe {
            XCTAssertTrue(DailySummaryService.offersReassurance(text), text)
        }
    }

    func testOffersReassuranceAllowsGroundedReflections() {
        // These are the real deterministic detail lines the detectors produce.
        let safe = [
            "On the nights you slept less, your energy the next day was often lower. You might like to keep an eye on it, or mention it to your GP.",
            "You've noted headache on several of your recent days. That's the kind of thing worth keeping an eye on, or bringing to your GP.",
            "Your cycles have varied more in length lately, becoming less predictable. It's useful to note, and helpful for your GP to hear.",
            "Your symptoms have tended to turn up more in the days before your period. Worth noticing, and worth a mention to your GP if it's wearing on you.",
        ]
        for text in safe {
            XCTAssertFalse(DailySummaryService.offersReassurance(text), text)
        }
    }

    func testRefreshPurgesAStoredReassuringReflection() async {
        let context = TestStore.makeContext()
        let service = DailySummaryService(context: context, ownerID: TestStore.ownerID)

        func insert(day: Int, signature: String, text: String) {
            context.insert(DailySummary(
                day: Date.now.startOfDay.adding(days: day),
                text: text, source: .ai, signalsJSON: signature,
                generatedAt: .now, ownerID: "o", syncStatus: .synced))
        }
        insert(day: -2, signature: "[\"unsafe\"]",
               text: "Headaches have become your most frequent symptom lately, and there's no reason to worry about that. They're perfectly normal.")
        insert(day: -1, signature: "[\"safe\"]",
               text: "You've noted headache on several of your recent days. That's worth keeping an eye on, or bringing to your GP.")
        try? context.save()

        await service.refreshIfNeeded()  // no check-ins, so this only runs the cleanups

        let kept = service.history()
        XCTAssertEqual(kept.count, 1)
        XCTAssertFalse(kept.contains { $0.signalsJSON == "[\"unsafe\"]" }, "reassuring reflection should be purged")
        XCTAssertTrue(kept.contains { $0.signalsJSON == "[\"safe\"]" }, "grounded reflection should remain")
    }
}
