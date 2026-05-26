import Foundation
import UserNotifications

/// Handles medication reminders once they're delivered: marking the dose taken
/// when she taps the notification or its "Mark taken" button, and turning on
/// auto-logging for "Always mark taken". Set as the notification centre's
/// delegate at launch. Kept thin — the writes go through `AppEnvironment`.
final class NotificationCoordinator: NSObject, UNUserNotificationCenterDelegate {
    weak var env: AppEnvironment?

    /// Show reminders as a banner even while the app is open, so a dose that comes
    /// due mid-session isn't silent.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification) async
        -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        guard let idString = info["medicationID"] as? String,
              let id = UUID(uuidString: idString) else { return }
        // "" means the whole-day (no set time) dose.
        let slot = (info["slot"] as? String).flatMap { $0.isEmpty ? nil : $0 }
        // The dose belongs to the day the reminder actually fired.
        let day = response.notification.date
        let action = response.actionIdentifier
        // Capture the (@MainActor, Sendable) environment, not the coordinator, so
        // the main-actor hop below carries only Sendable values.
        let env = self.env

        await MainActor.run {
            switch action {
            case NotificationService.alwaysTakenActionID:
                env?.enableMedicationAutoLog(medicationID: id, slot: slot, on: day)
            case NotificationService.markTakenActionID, UNNotificationDefaultActionIdentifier:
                env?.markMedicationTaken(medicationID: id, slot: slot, on: day)
            default:
                break // dismissed
            }
        }
    }
}
