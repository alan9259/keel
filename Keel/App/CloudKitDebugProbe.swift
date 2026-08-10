#if DEBUG
import Foundation
import CoreData

/// DEBUG-only console tracing for SwiftData's automatic CloudKit mirroring, so a
/// signed run (a device, or a signed simulator signed into iCloud) shows whether
/// sync is actually happening. It observes `NSPersistentCloudKitContainer`'s
/// setup / import / export events — a global Core Data notification that needs no
/// CloudKit container reference. No-op in Release (whole file behind `#if DEBUG`).
///
/// It deliberately does **not** touch `CKContainer`: initialising a container the
/// app isn't entitled to (e.g. an unsigned simulator build) is a hard crash via
/// CloudKit's `_os_crash`, which can't be caught — so an account-status check
/// would crash every unsigned build on launch. The mirroring events, plus the
/// system's `NSCloudKitMirroringDelegate` logs, already show whether sync works.
///
/// Filter the console with `KEEL_CLOUDKIT`. On a signed + iCloud run you'll see a
/// `setup succeeded`, then `import`/`export` events as data mirrors. Nothing but
/// the "observing" line means mirroring isn't active (unsigned/unentitled build,
/// or no iCloud account).
enum CloudKitDebugProbe {
    private static var token: NSObjectProtocol?

    @MainActor
    static func start() {
        guard token == nil else { return }
        token = NotificationCenter.default.addObserver(
            forName: NSPersistentCloudKitContainer.eventChangedNotification,
            object: nil, queue: .main
        ) { note in
            guard let event = note.userInfo?[NSPersistentCloudKitContainer.eventNotificationUserInfoKey]
                    as? NSPersistentCloudKitContainer.Event else { return }
            let type: String
            switch event.type {
            case .setup: type = "setup"
            case .import: type = "import"
            case .export: type = "export"
            @unknown default: type = "unknown"
            }
            let phase = event.endDate == nil ? "started" : (event.succeeded ? "succeeded" : "failed")
            if let error = event.error {
                NSLog("KEEL_CLOUDKIT %@ %@ error=%@", type, phase, String(describing: error))
            } else {
                NSLog("KEEL_CLOUDKIT %@ %@", type, phase)
            }
        }
        NSLog("KEEL_CLOUDKIT observing mirroring events (DEBUG)")
    }
}
#endif
