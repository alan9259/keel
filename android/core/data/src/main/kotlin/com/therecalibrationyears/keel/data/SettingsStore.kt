package com.therecalibrationyears.keel.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

    /** Chosen mood pack id; null until she chooses (Home then uses the default pack). */
    val moodPackId: Flow<String?> = store.data.map { it[MOOD_PACK_ID] }

    /** Alcohol on the check-in: on by default, until she chooses (iOS, 4 Oct 2026). */
    val notesAlcohol: Flow<Boolean> = store.data.map { it[NOTES_ALCOHOL] ?: true }

    /** The intimacy and bladder group stays folded away until she opts in. */
    val showsSensitiveSymptoms: Flow<Boolean> = store.data.map { it[SENSITIVE_SYMPTOMS] ?: false }

    /** The built-in symptom catalog version this store was last brought up to (0 = never). */
    val symptomCatalogVersion: Flow<Int> = store.data.map { it[SYMPTOM_CATALOG_VERSION] ?: 0 }

    /** Home owes her the one-line reminders explanation (set when onboarding finishes). */
    val notificationExplainerPending: Flow<Boolean> = store.data.map { it[NOTIFICATION_EXPLAINER_PENDING] ?: false }

    suspend fun setColourModeRaw(raw: String) = store.edit { it[COLOUR_MODE] = raw }
    suspend fun setThemeId(id: String) = store.edit { it[THEME_ID] = id }
    suspend fun setHaptics(on: Boolean) = store.edit { it[HAPTICS] = on }
    suspend fun setMoodPackId(id: String) = store.edit { it[MOOD_PACK_ID] = id }
    suspend fun setNotesAlcohol(on: Boolean) = store.edit { it[NOTES_ALCOHOL] = on }
    suspend fun setShowsSensitiveSymptoms(on: Boolean) = store.edit { it[SENSITIVE_SYMPTOMS] = on }
    suspend fun setSymptomCatalogVersion(version: Int) = store.edit { it[SYMPTOM_CATALOG_VERSION] = version }
    suspend fun setNotificationExplainerPending(pending: Boolean) =
        store.edit { it[NOTIFICATION_EXPLAINER_PENDING] = pending }

    private companion object {
        val COLOUR_MODE = stringPreferencesKey("keel.colourMode")
        val THEME_ID = stringPreferencesKey("keel.themeID")
        val HAPTICS = booleanPreferencesKey("keel.haptics")
        val MOOD_PACK_ID = stringPreferencesKey("keel.moodPackID")
        val NOTES_ALCOHOL = booleanPreferencesKey("keel.notesAlcohol")
        val SENSITIVE_SYMPTOMS = booleanPreferencesKey("keel.sensitiveSymptoms")
        val SYMPTOM_CATALOG_VERSION = intPreferencesKey("keel.symptomCatalogVersion")
        val NOTIFICATION_EXPLAINER_PENDING = booleanPreferencesKey("keel.notificationExplainerPending")
    }
}
