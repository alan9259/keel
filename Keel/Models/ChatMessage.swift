import Foundation
import SwiftData

enum ChatRole: String, Codable {
    case user
    case assistant
}

/// A single message in the AI companion conversation.
@Model
final class ChatMessage: Syncable {
    var id: UUID = UUID()
    var roleRaw: String = ""
    var text: String = ""
    var createdAt: Date = Date.now

    // Syncable
    var ownerID: String = ""
    var updatedAt: Date = Date.now
    var deletedAt: Date?
    var syncStatusRaw: String = SyncStatus.pendingUpload.rawValue

    init(
        id: UUID = UUID(),
        role: ChatRole,
        text: String,
        ownerID: String,
        createdAt: Date = Date.now,
        updatedAt: Date = Date.now,
        deletedAt: Date? = nil,
        syncStatus: SyncStatus = .pendingUpload
    ) {
        self.id = id
        self.roleRaw = role.rawValue
        self.text = text
        self.createdAt = createdAt
        self.ownerID = ownerID
        self.updatedAt = updatedAt
        self.deletedAt = deletedAt
        self.syncStatusRaw = syncStatus.rawValue
    }

    var role: ChatRole {
        get { ChatRole(rawValue: roleRaw) ?? .assistant }
        set { roleRaw = newValue.rawValue }
    }
}
