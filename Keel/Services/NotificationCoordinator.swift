import Foundation
import UserNotifications

/// Handles medication reminders once they're delivered: marking the dose taken
/// when she taps the notification or its "Mark taken" button, and turning on
/// auto-logging for "Always mark taken". Set as the notification centre's
/// delegate at launch. Kept thin — the writes go through `AppEnvironment`.
///
/// Uses the completion-handler delegate methods (not the async ones) and finishes
/// on the MAIN thread: the system runs its post-response snapshot / state-
/// restoration update right after the completion handler, and that UIKit work must
/// be on the main thread. The async variant delivered completion on a background
/// thread, which made UIKit assert and crash when a notification action was tapped.
final class NotificationCoordinator: NSObject, UNUserNotificationCenterDelegate {
    weak var env: AppEnvironment?

    /// Show reminders as a banner even while the app is open.
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .sound])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        // Pull out Sendable values up front (the userInfo dictionary isn't Sendable).
        let info = response.notification.request.content.userInfo
        let idString = info["medicationID"] as? String
        let slotRaw = info["slot"] as? String
        let day = response.notification.date
        let action = response.actionIdentifier

        // Do the work AND finish on the main thread (see the type note above).
        DispatchQueue.main.async { [weak self] in
            MainActor.assumeIsolated {
                if let idString, let id = UUID(uuidString: idString) {
                    let slot = slotRaw.flatMap { $0.isEmpty ? nil : $0 }
                    switch action {
                    case NotificationService.alwaysTakenActionID:
                        self?.env?.enableMedicationAutoLog(medicationID: id, slot: slot, on: day)
                    case NotificationService.markTakenActionID, UNNotificationDefaultActionIdentifier:
                        self?.env?.markMedicationTaken(medicationID: id, slot: slot, on: day)
                    default:
                        break // dismissed
                    }
                }
            }
            completionHandler()
        }
    }
}
