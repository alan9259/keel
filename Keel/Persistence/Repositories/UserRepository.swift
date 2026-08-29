import Foundation
import SwiftData

@MainActor
protocol UserRepositoring {
    func currentProfile() -> UserProfile?
    @discardableResult
    func upsertProfile(firstName: String, email: String?, appleUserID: String?) -> UserProfile
    @discardableResult
    func updateBasicInfo(firstName: String, lastName: String?, birthYear: Int?, mobile: String?, email: String?) -> UserProfile
    func setPathway(_ pathway: Pathway)
    func setHealthKitAuthorized(_ authorized: Bool)
    func setPeriodsNotApplicableReason(_ reason: String?)
}

@MainActor
struct UserRepository: UserRepositoring {
    let context: ModelContext
    let ownerID: OwnerIDProvider

    func currentProfile() -> UserProfile? {
        var descriptor = FetchDescriptor<UserProfile>(
            predicate: #Predicate { $0.deletedAt == nil }
        )
        descriptor.fetchLimit = 1
        return try? context.fetch(descriptor).first
    }

    @discardableResult
    func upsertProfile(firstName: String, email: String?, appleUserID: String?) -> UserProfile {
        if let existing = currentProfile() {
            existing.firstName = firstName
            if let email { existing.email = email }
            if let appleUserID { existing.appleUserID = appleUserID }
            stampContext(existing)
            existing.touch()
            save()
            return existing
        }
        let profile = UserProfile(
            firstName: firstName,
            email: email,
            appleUserID: appleUserID,
            ownerID: ownerID()
        )
        stampContext(profile)
        context.insert(profile)
        save()
        return profile
    }

    /// Save the basic details she edits on the Profile screen. Optional fields are
    /// written exactly as given (nil clears them), so the form is the source of truth;
    /// a blank first name is ignored so she's never left unnamed. Creates a profile if
    /// somehow none exists yet (every onboarding path makes one, so this is defensive).
    @discardableResult
    func updateBasicInfo(firstName: String, lastName: String?, birthYear: Int?, mobile: String?, email: String?) -> UserProfile {
        let trimmedName = firstName.trimmingCharacters(in: .whitespaces)
        let profile = currentProfile() ?? {
            let created = UserProfile(firstName: trimmedName.nilIfEmpty ?? "there", ownerID: ownerID())
            context.insert(created)
            return created
        }()
        if let name = trimmedName.nilIfEmpty { profile.firstName = name }
        profile.lastName = lastName
        profile.birthYear = birthYear
        profile.mobile = mobile
        profile.email = email
        stampContext(profile)
        profile.touch()
        save()
        return profile
    }

    /// Her note for when periods no longer apply (blank clears it). Stored trimmed,
    /// exactly as she entered it, for the GP summary's cycle block.
    func setPeriodsNotApplicableReason(_ reason: String?) {
        guard let profile = currentProfile() else { return }
        profile.periodsNotApplicableReason = reason?.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
        profile.touch()
        save()
    }

    /// Record the current non-identifying environment context on the profile, so
    /// it persists and syncs. Refreshed on every upsert (OS/app/timezone change).
    private func stampContext(_ profile: UserProfile) {
        profile.region = DeviceContext.region
        profile.localeID = DeviceContext.localeID
        profile.timeZoneID = DeviceContext.timeZoneID
        profile.appVersion = DeviceContext.appVersion
        profile.deviceModel = DeviceContext.deviceModel
        profile.osVersion = DeviceContext.osVersion
    }

    func setPathway(_ pathway: Pathway) {
        guard let profile = currentProfile() else { return }
        profile.pathway = pathway
        profile.touch()
        save()
    }

    func setHealthKitAuthorized(_ authorized: Bool) {
        guard let profile = currentProfile() else { return }
        profile.healthKitAuthorized = authorized
        profile.touch()
        save()
    }

    private func save() { try? context.save() }
}
