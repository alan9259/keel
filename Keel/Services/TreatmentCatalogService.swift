import Foundation
import OSLog

/// The picker list for treatments and supplements.
///
/// Deliberately **data, not code**: brand names, PBS listings and shortage
/// substitutions move faster than app releases, so the list ships as
/// `treatment-catalog.json` and is read at runtime. A newer copy cached in
/// Application Support wins over the bundled one, which is the seam a remote
/// refresh drops into (see `refresh(from:)`), no App Store release required.
///
/// This is a logging taxonomy. Nothing here recommends, doses, or ranks.
@MainActor
@Observable
final class TreatmentCatalogService {
    private(set) var catalog: TreatmentCatalog = .empty
    private let logger = Logger(subsystem: "com.therecalibrationyears.keel", category: "catalog")

    private static let fileName = "treatment-catalog.json"

    init() {
        catalog = Self.loadCached() ?? Self.loadBundled() ?? .empty
    }

    func groups(for kind: TreatmentKind) -> [TreatmentCatalog.Group] {
        catalog.groups.filter { $0.kind == kind }
    }

    /// Fetch a newer catalog and cache it. Nothing calls this yet: it exists so
    /// the list can be updated without shipping a build once there's somewhere to
    /// serve it from. A malformed or older payload is ignored.
    func refresh(from url: URL, using session: URLSession = .shared) async {
        do {
            let (data, _) = try await session.data(from: url)
            let fetched = try JSONDecoder().decode(TreatmentCatalog.self, from: data)
            guard fetched.version > catalog.version else {
                logger.info("Catalog v\(fetched.version) is not newer than v\(self.catalog.version); ignoring.")
                return
            }
            try data.write(to: Self.cacheURL, options: .atomic)
            catalog = fetched
            logger.info("Catalog updated to v\(fetched.version).")
        } catch {
            logger.error("Catalog refresh failed: \(error.localizedDescription)")
        }
    }

    // MARK: Loading

    private static var cacheURL: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return base.appendingPathComponent(fileName)
    }

    private static func loadCached() -> TreatmentCatalog? {
        guard let data = try? Data(contentsOf: cacheURL) else { return nil }
        return try? JSONDecoder().decode(TreatmentCatalog.self, from: data)
    }

    private static func loadBundled() -> TreatmentCatalog? {
        guard let url = Bundle.main.url(forResource: "treatment-catalog", withExtension: "json"),
              let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(TreatmentCatalog.self, from: data)
    }
}

/// Decoded shape of `treatment-catalog.json`.
struct TreatmentCatalog: Codable {
    var version: Int
    var groups: [Group]

    static let empty = TreatmentCatalog(version: 0, groups: [])

    struct Group: Codable, Identifiable {
        var id: String
        var kind: TreatmentKind
        /// Heading that several groups can share, e.g. "Oestrogen".
        var section: String
        var title: String
        /// Plain framing shown above the group, e.g. the testosterone note.
        var note: String?
        var defaultMethod: MedicationMethod?
        var items: [Item]

        /// Alphabetical, never editorial: no product leads because we put it there.
        var sortedItems: [Item] {
            items.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        }
    }

    struct Item: Codable, Identifiable, Hashable {
        var name: String
        /// Overrides the group's method, e.g. an IUD inside the progestogen group.
        var method: MedicationMethod?
        /// Prescribed outside its approved use.
        var offLabel: Bool?
        /// Compounded preparation: not standardised, so strength isn't captured.
        var compounded: Bool?

        var id: String { name }
        var isOffLabel: Bool { offLabel ?? false }
        var isCompounded: Bool { compounded ?? false }
    }
}
