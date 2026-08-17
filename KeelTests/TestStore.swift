import Foundation
import SwiftData
@testable import Keel

/// Shared helpers for repository tests: a fresh in-memory SwiftData context and a
/// fixed UTC Gregorian calendar so date logic never depends on the machine's
/// timezone or the current day.
@MainActor
enum TestStore {
    /// Containers are retained for the test process: a `ModelContext` whose
    /// `ModelContainer` has been deallocated crashes on access, so `makeContext`
    /// must keep the container alive (a few in-memory stores per run is fine).
    private static var retained: [ModelContainer] = []

    static func makeContext() -> ModelContext {
        let container = KeelSchema.makeContainer(inMemory: true)
        retained.append(container)
        return container.mainContext
    }

    static let ownerID: OwnerIDProvider = { "test-owner" }

    nonisolated static var utcCalendar: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }
}
