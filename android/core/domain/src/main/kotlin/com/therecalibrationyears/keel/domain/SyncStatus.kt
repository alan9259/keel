package com.therecalibrationyears.keel.domain

/** Upload state for a record's local changes (port of iOS `SyncStatus`; raw values are stored). */
enum class SyncStatus(val raw: String) {
    PENDING_UPLOAD("pendingUpload"),
    SYNCED("synced"),
}
