package com.therecalibrationyears.keel.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Her settings (port of iOS `SettingsStore`, Keel/App/SettingsStore.swift), with the same
 * keys and defaults. Settings are added here as their screens are ported.
 */
class SettingsStore(private val store: DataStore<Preferences>) {

    /** Raw `ColourMode` value; null until she chooses (the app then follows the system). */
    val colourModeRaw: Flow<String?> = store.data.map { it[COLOUR_MODE] }

    /** Chosen theme id; null until she chooses (the app then uses the default theme). */
    val themeId: Flow<String?> = store.data.map { it[THEME_ID] }

    val haptics: Flow<Boolean> = store.data.map { it[HAPTICS] ?: true }

    /** Home owes her the one-line reminders explanation (set when onboarding finishes). */
    val notificationExplainerPending: Flow<Boolean> = store.data.map { it[NOTIFICATION_EXPLAINER_PENDING] ?: false }

    suspend fun setColourModeRaw(raw: String) = store.edit { it[COLOUR_MODE] = raw }
    suspend fun setThemeId(id: String) = store.edit { it[THEME_ID] = id }
    suspend fun setHaptics(on: Boolean) = store.edit { it[HAPTICS] = on }
    suspend fun setNotificationExplainerPending(pending: Boolean) =
        store.edit { it[NOTIFICATION_EXPLAINER_PENDING] = pending }

    private companion object {
        val COLOUR_MODE = stringPreferencesKey("keel.colourMode")
        val THEME_ID = stringPreferencesKey("keel.themeID")
        val HAPTICS = booleanPreferencesKey("keel.haptics")
        val NOTIFICATION_EXPLAINER_PENDING = booleanPreferencesKey("keel.notificationExplainerPending")
    }
}
