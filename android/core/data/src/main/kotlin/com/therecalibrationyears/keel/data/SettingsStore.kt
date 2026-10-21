package com.therecalibrationyears.keel.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.therecalibrationyears.keel.domain.ReminderConfig
import kotlinx.serialization.json.Json
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

    /** The master switch for reminders (Settings, and the reminders explanation). */
    val pushNotifications: Flow<Boolean> = store.data.map { it[PUSH_NOTIFICATIONS] ?: true }

    /** Which reminders are on: `dailyCheckIn`, `medication`, `hydration`, `movement`, `windDown`. */
    val enabledReminderIds: Flow<Set<String>> = store.data.map { it[REMINDERS] ?: DEFAULT_REMINDERS }

    /** Timing for the everyday reminders; iOS defaults until she changes them. */
    val reminderConfig: Flow<ReminderConfig> = store.data.map { prefs ->
        prefs[REMINDER_CONFIG]?.let { runCatching { json.decodeFromString(ReminderConfig.serializer(), it) }.getOrNull() }
            ?: ReminderConfig()
    }

    /** Reminders show on the lock screen, so medicine names stay off until she chooses. */
    val medicineNamesInReminders: Flow<Boolean> = store.data.map { it[MED_NAMES_IN_REMINDERS] ?: false }

    /**
     * Android only: whether Keel has shown the notification permission prompt, so a later
     * refusal can be told apart from "never asked" (the system API can't).
     */
    val notificationPermissionAsked: Flow<Boolean> = store.data.map { it[NOTIFICATION_PERMISSION_ASKED] ?: false }

    /** Android only: she has been told, once, how to get reminders on time (plan D25). */
    val exactTimingOffered: Flow<Boolean> = store.data.map { it[EXACT_TIMING_OFFERED] ?: false }

    suspend fun setColourModeRaw(raw: String) = store.edit { it[COLOUR_MODE] = raw }
    suspend fun setThemeId(id: String) = store.edit { it[THEME_ID] = id }
    suspend fun setHaptics(on: Boolean) = store.edit { it[HAPTICS] = on }
    suspend fun setMoodPackId(id: String) = store.edit { it[MOOD_PACK_ID] = id }
    suspend fun setNotesAlcohol(on: Boolean) = store.edit { it[NOTES_ALCOHOL] = on }
    suspend fun setShowsSensitiveSymptoms(on: Boolean) = store.edit { it[SENSITIVE_SYMPTOMS] = on }
    suspend fun setSymptomCatalogVersion(version: Int) = store.edit { it[SYMPTOM_CATALOG_VERSION] = version }
    suspend fun setNotificationExplainerPending(pending: Boolean) =
        store.edit { it[NOTIFICATION_EXPLAINER_PENDING] = pending }

    suspend fun setPushNotifications(on: Boolean) = store.edit { it[PUSH_NOTIFICATIONS] = on }
    suspend fun setReminderEnabled(id: String, on: Boolean) = store.edit {
        val current = it[REMINDERS] ?: DEFAULT_REMINDERS
        it[REMINDERS] = if (on) current + id else current - id
    }
    suspend fun setReminderConfig(config: ReminderConfig) =
        store.edit { it[REMINDER_CONFIG] = json.encodeToString(ReminderConfig.serializer(), config) }
    suspend fun setMedicineNamesInReminders(on: Boolean) = store.edit { it[MED_NAMES_IN_REMINDERS] = on }
    suspend fun setNotificationPermissionAsked(asked: Boolean) = store.edit { it[NOTIFICATION_PERMISSION_ASKED] = asked }
    suspend fun setExactTimingOffered(offered: Boolean) = store.edit { it[EXACT_TIMING_OFFERED] = offered }

    companion object {
        /** iOS: the daily check-in and medication reminders start on. */
        val DEFAULT_REMINDERS = setOf("dailyCheckIn", "medication")
        private val json = Json { ignoreUnknownKeys = true }

        private val COLOUR_MODE = stringPreferencesKey("keel.colourMode")
        private val THEME_ID = stringPreferencesKey("keel.themeID")
        private val HAPTICS = booleanPreferencesKey("keel.haptics")
        private val MOOD_PACK_ID = stringPreferencesKey("keel.moodPackID")
        private val NOTES_ALCOHOL = booleanPreferencesKey("keel.notesAlcohol")
        private val SENSITIVE_SYMPTOMS = booleanPreferencesKey("keel.sensitiveSymptoms")
        private val SYMPTOM_CATALOG_VERSION = intPreferencesKey("keel.symptomCatalogVersion")
        private val NOTIFICATION_EXPLAINER_PENDING = booleanPreferencesKey("keel.notificationExplainerPending")
        private val PUSH_NOTIFICATIONS = booleanPreferencesKey("keel.pushNotifications")
        private val REMINDERS = stringSetPreferencesKey("keel.reminders")
        private val REMINDER_CONFIG = stringPreferencesKey("keel.reminderConfig")
        private val MED_NAMES_IN_REMINDERS = booleanPreferencesKey("keel.medNamesInReminders")
        private val NOTIFICATION_PERMISSION_ASKED = booleanPreferencesKey("keel.notificationPermissionAsked")
        private val EXACT_TIMING_OFFERED = booleanPreferencesKey("keel.exactTimingOffered")
    }
}
