#if DEBUG
import Foundation
import CloudKit
import CoreData

/// DEBUG-only console tracing for SwiftData's automatic CloudKit mirroring, so a
/// signed run (a device, or a signed simulator signed into iCloud) shows whether
/// sync is actually happening. Logs the iCloud account status once at launch,
/// then every `NSPersistentCloudKitContainer` setup / import / export event. It
/// observes the global Core Data notification, so it needs no reference to the
/// container SwiftData owns. No-op in Release (whole file behind `#if DEBUG`).
///
/// Filter the console with `KEEL_CLOUDKIT`. What to expect:
///   • `account=available` — signed into iCloud and the container is reachable.
///   • a `setup succeeded`, then `import`/`export` events as data mirrors.
///   • nothing but the account line means mirroring isn't active (unsigned or
///     unentitled build, or `.automatic` off).
enum CloudKitDebugProbe {
    private static var token: NSObjectProtocol?

    @MainActor
    static func start(containerID: String) {
        Task {
            do {
                let status = try await CKContainer(identifier: containerID).accountStatus()
                NSLog("KEEL_CLOUDKIT account=%@ container=%@", describe(status), containerID)
            } catch {
                NSLog("KEEL_CLOUDKIT accountStatus error=%@", String(describing: error))
            }
        }

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

    private static func describe(_ status: CKAccountStatus) -> String {
        switch status {
        case .available: "available"
        case .noAccount: "noAccount (not signed into iCloud)"
        case .restricted: "restricted"
        case .couldNotDetermine: "couldNotDetermine (often: build not entitled)"
        case .temporarilyUnavailable: "temporarilyUnavailable"
        @unknown default: "unknown"
        }
    }
}
#endif
