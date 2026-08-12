import XCTest
@testable import Keel

/// Region matching for the Get Support screen and the companion's safety prompt.
/// Where she physically is (time zone) must win over her formatting locale, so an
/// en-NZ phone used in Australia still shows Australian crisis numbers. Anything
/// outside AU/NZ shows both, so no one is left without a number. (This answers the
/// "will people in NZ see NZ info?" question with a test.)
final class CrisisResourcesTests: XCTestCase {

    private func match(_ localeID: String, _ tzID: String) -> [SupportRegion] {
        CrisisResources.matching(locale: Locale(identifier: localeID),
                                 timeZone: TimeZone(identifier: tzID)!)
    }

    func testAustralianTimeZoneShowsAustralia() {
        let r = match("en_AU", "Australia/Melbourne")
        XCTAssertEqual(r.map(\.code), ["AU"])
        XCTAssertEqual(r.first?.emergency, "000")
    }

    func testNewZealandTimeZoneShowsNewZealand() {
        let r = match("en_NZ", "Pacific/Auckland")
        XCTAssertEqual(r.map(\.code), ["NZ"])
        XCTAssertEqual(r.first?.emergency, "111")
    }

    func testChathamTimeZoneShowsNewZealand() {
        XCTAssertEqual(match("en_NZ", "Pacific/Chatham").map(\.code), ["NZ"])
    }

    func testTimeZoneWinsOverLocale() {
        // en-NZ phone physically in Australia → Australian numbers (tz leads).
        XCTAssertEqual(match("en_NZ", "Australia/Sydney").map(\.code), ["AU"])
        // en-AU phone in New Zealand → New Zealand numbers.
        XCTAssertEqual(match("en_AU", "Pacific/Auckland").map(\.code), ["NZ"])
    }

    func testLocaleFallbackWhenTimeZoneIsNeither() {
        // Overseas time zone, AU locale → still AU via the locale fallback.
        XCTAssertEqual(match("en_AU", "Europe/London").map(\.code), ["AU"])
        XCTAssertEqual(match("en_NZ", "America/New_York").map(\.code), ["NZ"])
    }

    func testUnknownRegionShowsBoth() {
        let r = match("en_GB", "Europe/London")
        XCTAssertEqual(r.map(\.code), ["AU", "NZ"])
    }

    func testEveryContactHasANoteAndNumber() {
        for region in CrisisResources.all {
            XCTAssertFalse(region.emergency.isEmpty)
            for c in region.contacts {
                XCTAssertFalse(c.contact.isEmpty, "\(region.code) \(c.name)")
                XCTAssertNotNil(c.note, "\(region.code) \(c.name) should describe itself")
            }
        }
    }
}
