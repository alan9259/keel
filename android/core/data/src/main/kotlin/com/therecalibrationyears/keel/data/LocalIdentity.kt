package com.therecalibrationyears.keel.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * The stable local owner id stamped on every record (port of the local half of iOS
 * `AuthService`; Android has no accounts). Plain DataStore: uninstalling removes it along
 * with her records, so there is nothing to clean up on reinstall.
 */
class LocalIdentity(private val store: DataStore<Preferences>) {

    /** The current owner id, or "" before onboarding establishes one. */
    suspend fun ownerId(): String = store.data.first()[OWNER_ID].orEmpty()

    /** Establish (or reuse) the local identity: `local-<UUID>`, as iOS `continueLocally`. */
    suspend fun continueLocally(): String {
        store.edit { if (it[OWNER_ID].isNullOrEmpty()) it[OWNER_ID] = "local-" + UUID.randomUUID().toString().uppercase() }
        return ownerId()
    }

    /** Forget the local identity ("Delete all my data"; iOS `signOut`). */
    suspend fun signOut() { store.edit { it.remove(OWNER_ID) } }

    private companion object {
        val OWNER_ID = stringPreferencesKey("keel.ownerID")
    }
}
