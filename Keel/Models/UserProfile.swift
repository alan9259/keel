import Foundation
import SwiftData

@Model
final class UserProfile: Syncable {
    @Attribute(.unique) var id: UUID
    var firstName: String
    var email: String?
    /// Stable Sign in with Apple user identifier — this is also the `ownerID`.
    var appleUserID: String?
    var pathwayRaw: String?
    var healthKitAuthorized: Bool
    var trackingStartDate: Date

    // Non-identifying environment context (see `DeviceContext`). Recorded so we
    // can tailor content and help support; never used to identify the person.
    var region: String? = nil
    var localeID: String? = nil
    var timeZoneID: String? = nil
    var appVersion: String? = nil
    var deviceModel: String? = nil
    var osVersion: String? = nil

    // Syncable
    var ownerID: String
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?
    var syncStatusRaw: String

    init(
        id: UUID = UUID(),
        firstName: String,
        email: String? = nil,
        appleUserID: String? = nil,
        pathway: Pathway? = nil,
        healthKitAuthorized: Bool = false,
        trackingStartDate: Date = .now,
        ownerID: String,
        createdAt: Date = .now,
        updatedAt: Date = .now,
        deletedAt: Date? = nil,
        syncStatus: SyncStatus = .pendingUpload
    ) {
        self.id = id
        self.firstName = firstName
        self.email = email
        self.appleUserID = appleUserID
        self.pathwayRaw = pathway?.rawValue
        self.healthKitAuthorized = healthKitAuthorized
        self.trackingStartDate = trackingStartDate
        self.ownerID = ownerID
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.syncStatusRaw = syncStatus.rawValue
    }

    var pathway: Pathway? {
        get { pathwayRaw.flatMap(Pathway.init(rawValue:)) }
        set { pathwayRaw = newValue?.rawValue }
    }
}
