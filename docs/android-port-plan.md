# Keel for Android: port plan (feature parity + Health Connect)

> Written 20 Oct 2026 for a later session; approved by the owner 21 Oct 2026. This file,
> `docs/android-port-plan.md`, is the source of truth. Every Android session
> starts by reading `CLAUDE.md`, `DESIGN_PRINCIPLES.md`, `android/CLAUDE.md` (created in M0) and
> this plan. Library versions were checked against official release pages on 20 Oct 2026
> (sources at the end); re-verify at scaffold time.
>
> **Status (21 Oct 2026): M0 done** (see "M0 outcome" under §5). At the owner's request the
> first real screens came next: **onboarding is built** (M2 slice) on the parts of M1 it needs
> (UserProfile table, UserRepository, SettingsStore, LocalIdentity). See "Onboarding slice"
> under §5. **Home is built** on the check-in, symptom, medicine and activity tables (see
> "Home slice"). **The check-in flow is built** (see "Check-in slice"). **M3 medications are
> built** (see "M3 medications") **and so are reminders** (see "M3 reminders"), with
> "Reminders on time" for exact alarms (D25). Open: the daily top-up (D11). **M4 is under
> way:** Cycle is built (see "M4 Cycle"); next Looking back, Activities, Stats.

## Context

Keel ships on iOS (SwiftUI + SwiftData, iOS 17+, build 59, branch `healthkit-isolation`). We
need an Android app with **exactly the same features**, using **Health Connect** in place of
Apple Health. The iOS app is the **reference implementation**: each rule below cites the iOS
file to port from. Where iOS has a quirk, §6 says whether to copy or fix it.

**Decided by the owner (20 Oct 2026)**
- **Native Kotlin + Jetpack Compose.** The iOS app stays as is; no shared code layer.
- **Code in `android/` in this repo.** It shares `DESIGN_PRINCIPLES.md`,
  `Keel/Resources/Fonts`, `Keel/Resources/treatment-catalog.json` and `brand/` (single source,
  copied by Gradle at build time, never duplicated in git).
- **minSdk 28 (Android 9).** Health Connect is built in on 14+; on 9 to 13 it is installed
  from Google Play (the Connect step handles this).
- **One shared backup format.** Android reads and writes the same versioned JSON
  `.keelbackup` as iOS; shared fixtures prove round-trips both ways.

**Binding rules carried over** (restate in `android/CLAUDE.md`): AU/NZ spelling; no em/en
dashes; medical boundary (support and inform, never diagnose or prescribe); colours only from
theme tokens (amber for errors, never red); no fake data (stand-ins tagged `// FAKE:`, debug
seeds under `src/debug` exempt); every logic change ships with unit tests; verify on an emulator
with screenshots and say plainly what was not verified; **screens shown before any permission
prompt have only "Continue"** (App Review 5.1.1(iv) rejection, applied to Android runtime
permissions too); copy verbatim from iOS except approved platform substitutions (§3.8); bump
`versionCode` before any shippable build.

---

## 1. Architecture

### 1.1 Modules (`android/`)
```
android/
  settings.gradle.kts, gradle/libs.versions.toml, build-logic/convention/
  app/                MainActivity (FragmentActivity), KeelApplication, DI graph, Navigation 3
                      NavDisplay + MainRoute (21 routes), KeelAppController (port of AppEnvironment),
                      BroadcastReceivers, manifest, src/debug/DebugHarness (intent-extra probes, seeds)
  core/domain/        kotlin-jvm, no Android deps: enums, value types (DoseSlot, DoseSchedule,
                      ReminderConfig, HealthSnapshot), every pure rule + its tests (§4.1), parity helpers
  core/copy/          kotlin-jvm: every user-facing string, verbatim, as Kotlin objects; copy tests
  core/data/          Room MainDatabase + HealthDatabase, DAOs, repositories, HealthIngestor,
                      SettingsStore (DataStore), SecureStore (Keystore), BackupService, catalog loader
  core/designsystem/  KeelTheme tokens + ThemeCatalog, fonts, components, Material Symbols drawables,
                      KeelMark, EmojiGlyph
  core/platform/      NotificationService + AlarmScheduler + reminder registry, BiometricAuthenticator,
                      SpeechRecognitionService, HealthConnectDataSource, DeviceContext, Haptics, intents
  feature/onboarding  feature/home (Dashboard, EntrySheet, CheckInModal, SymptomPicker)
  feature/cycle       feature/medications       feature/insights (Looking back, All notes, Activities, Stats)
  feature/gpsummary   feature/settings (More, Settings, Profile+background, Your privacy, About, Connect,
                      Get support, Reminders, Backup & Restore, Themes, Colour mode, Mood icons,
                      App lock, Health Connect)
fixtures/             (repo root) shared golden files read by both iOS and Android tests (§4.2)
```
`core/domain` and `core/copy` are plain JVM, so the iOS unit tests port 1:1 and run fast.

### 1.2 Libraries (pin in `libs.versions.toml`; verified 20 Oct 2026)
| Area | Choice |
|---|---|
| Build | AGP 9.4.1 (built-in Kotlin, no `kotlin-android` plugin), Gradle 9.8.2, JDK 17, Kotlin 2.4.21, KSP 2.3.12, Compose compiler plugin |
| SDK | compileSdk 37 (Compose BOM 2026.09.00's AARs require it), targetSdk 36 (Play requirement from 31 Aug 2026; edge-to-edge enforced), minSdk 28 |
| UI | Compose BOM 2026.09.00 (ui 1.12.1, material3 1.4.0 as a base, re-skinned from KeelTheme) |
| Navigation | Navigation 3 `navigation3-runtime/ui` 1.2.0 (back stack is a state list; maps to `goHome`; predictive back built in) |
| Lifecycle | lifecycle 2.11.0, activity 1.13.0, core-ktx 1.19.1 (`ProcessLifecycleOwner` replaces `scenePhase`) |
| Data | Room 2.8.5 + KSP (`exportSchema = true`), DataStore Preferences 1.2.1, kotlinx.serialization 1.12.x |
| DI | Hilt (Dagger 2.60.1, androidx.hilt 1.3.0) introduced in M1 with the first injected types; fall back to a manual `AppContainer` if Hilt + AGP 9 KSP2 misbehaves. M0 has nothing to inject. |
| Health | `androidx.health.connect:connect-client:1.1.0` (stable, not 1.2 alpha) + `connect-testing` (FakeHealthConnectClient) |
| Reminders | AlarmManager for dated one-shots; WorkManager 2.12.0 only for a daily top-up (D11) |
| Security | `androidx.biometric:biometric:1.1.0`; Android Keystore AES-256-GCM directly (security-crypto / EncryptedSharedPreferences is deprecated, don't use) |
| Other | platform `SpeechRecognizer`, `PdfDocument` + `StaticLayout`, `PdfRenderer` (preview), `core-splashscreen` |
| Tests | JUnit 4, coroutines-test, Turbine, Robolectric 4.17 (`@Config(sdk = [34])`: its SDK 35+ sandboxes need Java 21), Compose ui-test, Roborazzi (screenshots), pdfbox-android (androidTest PDF text) |

### 1.3 iOS → Android concept map
| iOS | Android |
|---|---|
| SwiftData main + "health" store (`Persistence/KeelSchema.swift`) | Room `keel-main.db` (MainDatabase) + `keel-health.db` (HealthDatabase, unreachable from any sync seam) |
| `Syncable` envelope (`Models/Syncable.swift`) | common columns `id TEXT PK` (UPPERCASE UUID), `ownerID`, `createdAt`, `updatedAt`, `deletedAt?`, `syncStatusRaw`; `touch()` / `softDelete()`; reads filter `deletedAt IS NULL` |
| `@Query` | DAO `Flow` → ViewModel `StateFlow` → `collectAsStateWithLifecycle` |
| `@Observable AppEnvironment` | `KeelAppController` (@Singleton) with the same method names (`bootstrap`, `markOnboarded`, `completeOnboarding`, `answerNotificationExplainer`, `connectHealth`, `syncHealthData(force)`, `eraseAllData`, `refreshMedicationReminders`, `markMedicationTaken`, ...) |
| `SettingsStore` (UserDefaults) | `SettingsStore` on DataStore with **identical keys and defaults** (`keel.*`), `resetToDefaults()`; `reminderConfig` as JSON with the same field names |
| `\.keelTheme` | `LocalKeelTheme`, `KeelTheme.resolve(themeId, isDark)` |
| `NavigationStack` + `MainRoute` | `@Serializable sealed interface MainRoute : NavKey`, same 21 cases, Home root, `goHome` clears to root |
| sheet / fullScreenCover / alert / confirmationDialog | `ModalBottomSheet` (EntrySheet partially expanded); full-screen `Dialog` for CheckInModal; themed `AlertDialog`; bottom-sheet option list (destructive in amber) |
| ToastView | same custom overlay (2.4 s), not `android.widget.Toast` |
| Keychain (PIN salt/hash, ownerID) | `SecureStore`: Keystore-wrapped AES-GCM blob in DataStore (keeps the `PINSecureStore` interface); ownerID in plain DataStore |
| Keychain cleanup on reinstall | not needed (uninstall wipes data with backup off, D9); replaced by a manifest test |
| LocalAuthentication | BiometricPrompt `BIOMETRIC_WEAK` (negative button "Enter PIN instead"); Forgot PIN `BIOMETRIC_WEAK or DEVICE_CREDENTIAL` (works on 28 to 29); `KeyguardManager.isDeviceSecure` |
| `scenePhase` | `ProcessLifecycleOwner` ON_START → `lock.applyForeground()`, `autoLogTodaysDoses()`, `syncHealthData()`; ON_STOP → `lock.markBackgrounded()` |
| App-switcher cover | API 33+ `setRecentsScreenshotEnabled(false)`; 28 to 32 `FLAG_SECURE`, only while the lock is on (D16) |
| Share / file importer | `ACTION_SEND` via FileProvider; SAF `CreateDocument` (export) and `OpenDocument` (import) |
| `mailto:` + "No email app set up" | `ACTION_SENDTO`, same percent-encoding (`FeedbackMail.swift`); `ActivityNotFoundException` → same dialog with "Copy email address" |
| Open iOS Settings | `ACTION_APP_NOTIFICATION_SETTINGS` / `ACTION_APPLICATION_DETAILS_SETTINGS` |
| SF Symbols | Material Symbols Rounded drawables with an SF-name mapping table; keep `Insight.iconKey` strings for backup parity |
| Haptics | `performHapticFeedback`, gated by the setting |
| DebugHarness `-uit*` args | `src/debug` intent extras (`--ez uitOnboarded true`, `--es uitRoute cycle`, seeds); mirror the flags in `Keel/App/DebugHarness.swift` |
| DeviceContext | region/locale/zone from the device; appVersion `"versionName (versionCode)"`; deviceModel `"MANUFACTURER MODEL"`; osVersion `"Android 15 (API 35)"` |

### 1.4 Data layer (port of `Keel/Models/*`, `Keel/Persistence/*`)
- **Main DB:** UserProfile (incl. `onboardingCompletedAt`, `healthConnected` ≙ `healthKitAuthorized`,
  hysterectomy, periods note, device context), CheckIn (energy 0 = not recorded, else 20..100;
  `alcoholCount` nullable), CheckInSymptom (checkInID CASCADE, symptomID SET NULL, severity 1..3,
  source), Symptom (`categoryRaw` free string, isCustom, isArchived, isDefaultChip), CycleEntry,
  Medication (all schedule columns + `doseTimesJSON`), MedicationLog (medicationID CASCADE, slot
  nullable), Insight, ActivityLog. No ChatMessage/DailySummary tables (backup writes `[]` and
  ignores them, as iOS).
- **Health DB:** HealthSample, HealthActivitySample, HealthFlowSample.
- Day fields are Instants at local start of day, computed DST-safely (`LocalDate.atStartOfDay(zone)`).
- **Migrations:** commit Room schema v1 JSON; only `Migration`/`AutoMigration`; never
  `fallbackToDestructiveMigration` (D6).
- **Repositories** (same names and semantics): User (oldest live profile, `upsertProfile` stamps
  DeviceContext, `markOnboardingCompleted`); Symptom (`syncBuiltIns` gated on
  `keel.symptomCatalogVersion` = 2 with renames + archive-not-delete; `rankedQuickChipIDs`;
  case-insensitive `findOrCreateCustom` via en_AU `Collator`); CheckIn (link reconcile, soft-delete
  links, `setAlcohol` clamps at 0); Cycle (`setFlow`, `togglePeriodDay`, `stats(400)` excluding
  periodEnd + spotting, `importedFlow`, `CycleDayMark.merge`); Medication (upsert by
  case-insensitive name, setTaken/clearTaken, `autoLogTodaysDueDoses` by slot id,
  `migrateLegacySchedules` and `stripExampleBrandsFromNames` still needed for restored iOS
  backups); Insight (`refreshDerived` only with ≥5 logged days); EatingLog tri-state;
  HealthIngestor (activity/vitals/flow upserts, both purges).
- **Catalogs:** port `SymptomCatalog.swift` data (54 built-ins, 12 default chips in order,
  renames); load `treatment-catalog.json` from assets (Gradle-copied; cached copy wins).
- `DataSource` raw values stay `"manual"` / `"healthKit"` (Kotlin constant `IMPORTED("healthKit")`).

### 1.5 Backup format contract (port of `Services/Backup/KeelBackup.swift`, `BackupService.swift`)
- Top-level keys: `version` (1), `exportedAt`, `appVersion`, `profiles`, `symptoms`, `checkIns`,
  `checkInSymptoms`, `cycleEntries`, `medications`, `medicationLogs`, `insights`, `activityLogs`,
  `chatMessages` (`[]`), `dailySummaries` (`[]`), `healthSamples`. HealthActivitySample and
  HealthFlowSample are not archived (parity, D3).
- Dates `yyyy-MM-dd'T'HH:mm:ss'Z'` UTC whole seconds (Swift's decoder rejects fractions); decode
  leniently. UUIDs written UPPERCASE everywhere (incl. slot ids inside `doseTimesJSON`,
  `MedicationLog.slot`); read case-insensitively. Omit null optionals; always write non-optional
  fields; sort keys recursively; pretty-print. Byte identity isn't required: compare canonical JSON.
- DTOs field-for-field with Swift's legacy reads and defaults (`startDate`, severity 1,
  `isDefaultChip ?? defaultOrder.contains(name)`, kind `supplement`, accent `terracotta`, schedule
  columns only when `scheduleKindRaw != nil`). `doseTimesJSON` = nested JSON string
  `[{"id":"UPPER","weekdays":[1..7],"hour":8,"minute":0}]`, decoded tolerantly.
- Restore: reject `version > 1` with the exact iOS message; keep this device's `healthConnected`
  and `onboardingCompletedAt`; wipe children before parents, insert parents first; skip dangling
  links; `markOnboarded()` after. Additive fields per D1.

### 1.6 Parity traps (package `core/domain/parity/`, each with tests)
- **Weekday numbering:** stored 1 = Sunday … 7 = Saturday (`weekdaysMask` bit 0 = Sunday);
  `iosWeekday(d) = d.dayOfWeek.value % 7 + 1`; first weekday from `WeekFields.of(locale)`.
- **Rounding:** half away from zero (`sign(x) * floor(abs(x) + 0.5)`), never `round`/`Math.round`.
  Used for 1 dp health values, `EnergyLevel.from`, `VitalTrend.average`.
- **UUIDs** uppercase wherever stored or serialised (incl. notification ids, `local-<UUID>`).
- **`DoseUnit.format`:** integral → integer; else `Double.toString()`; `String.format(Locale.ROOT, "%.1f")` for the GP energy line.
- **Locale:** fixed en_AU only where iOS fixes it (GP dates `d MMMM yyyy`, MonthSummary heading
  Gregorian, PDF filename); elsewhere the device locale. `DoseSlot.label` honours 12/24 h
  ("8:00 am" / "20:00").
- **String compare:** en_AU `Collator` (SECONDARY) for case-insensitive matches.
- **Clock:** inject `KeelClock { now(); zone() }`; tests use UTC.

### 1.7 Reminders (port of `Services/NotificationService.swift`, `NotificationCoordinator.swift`)
- Same pure API: `dayKey`, `Occurrence`, `plannedOccurrences`,
  `cycleHorizon = min(max(48 / max(n, 1), 4), 24)`, `medicationReminderText` (name only when
  `keel.medNamesInReminders`), `Copy`, `gate(status)`. Same ids: `keel.med.<UUID>.<slot8>.d<yyyyMMdd>`,
  `keel.dailyCheckIn`, `keel.hydration.<h>`, `keel.movement.<1..7|daily>`, `keel.winddown`.
- No pending-requests API on Android, so keep a device-only `scheduled_reminder` Room table
  (id, kind, medicationID, slotID, dayKey, triggerAt, title, body, autoLog), excluded from backups;
  port prefix/suffix cancels as registry queries, then `AlarmManager.cancel` (request code = stable
  hash of id).
- Declare `SCHEDULE_EXACT_ALARM` (not `USE_EXACT_ALARM`); `canScheduleExactAlarms()` →
  `setExactAndAllowWhileIdle`, else `setAndAllowWhileIdle`. Android 14+ starts new installs
  with exact alarms off, so Keel offers them (D25): "Reminders on time", once, after she saves a
  medicine with reminders (Continue only, then Android's Alarms & reminders page), and a note
  on the Reminders screen while they're off. Never during onboarding.
  Repeating lifestyle reminders schedule the next occurrence and re-arm on fire.
- Receivers: `ReminderAlarmReceiver` posts; `MedicationActionReceiver` ("Mark taken" / "Always
  mark taken", `goAsync()`, day = occurrence `dayKey`, D10); body tap = `PendingIntent.getActivity`
  that marks taken (D20; Android 12+ bans trampolines); `RescheduleReceiver` for BOOT_COMPLETED,
  TIME_SET, TIMEZONE_CHANGED, MY_PACKAGE_REPLACED, exact-alarm permission change. Auto-log
  reminders carry no actions.
- Channels: medication, daily check-in, lifestyle (channel names are new copy, owner sign-off).
- POST_NOTIFICATIONS (API 33+) only after "Gentle reminders" Continue; gate = proceed if enabled,
  request if not yet asked or rationale shown, else blocked (persist `keel.notificationPermissionAsked`);
  below 33 Continue just proceeds.
- **Bootstrap order identical to iOS:** syncBuiltIns → purge discontinued imports →
  insights.refreshDerived → migrateLegacySchedules → stripExampleBrands → lifestyle reminders →
  autoLogTodaysDoses → medication reminders → syncHealthData (self-gated).

### 1.8 Speech, app lock, PDF, theming
- **Speech** (`Services/SpeechRecognitionService.swift`): API 31+ only when
  `isOnDeviceRecognitionAvailable`, via `createOnDeviceSpeechRecognizer`; API 33+ also check the
  locale is installed (never auto-download). API 28 to 30 always unavailable ("Voice notes aren't
  available on this phone. You can type instead.") so audio never leaves the phone. RECORD_AUDIO
  only; partial results appended to the seed; `EXTRA_SEGMENTED_SESSION` on 33+.
- **App lock** (`Services/AppLockService.swift`, `PINSecurity.swift`): 4 digits; SHA-256 over
  salt+PIN × 100,000 on `Dispatchers.Default`; constant-time compare; 5 tries → 60 s lockout
  (`failedAttempts % 5`); 30 s grace; locked on cold launch; flag off + PIN present → clear PIN.
  LockGate is a full-screen Dialog above everything; other dialogs hide while locked.
- **GP PDF** (`Features/GPSummary/GPSummaryPDFRenderer.swift`): 595×842 pt, margin 46, footer 56,
  same sizes and column weights, symptom row cap 6 → 5 → 4 loop, block pagination; StaticLayout;
  Literata via `Typeface.Builder` variation settings, Poppins static. Strip metadata after
  rendering (length-preserving blanking of `/Producer /Creator /Author /Title /Subject
  /CreationDate /ModDate`; Skia writes a Producer); preview renders the same bytes with
  `PdfRenderer`. File `GP-Visit-Summary-yyyy-MM-dd.pdf` via FileProvider.
- **Theming** (`DesignSystem/*`): `KeelTheme` with the same 17 tokens; `copper`/`sageSoft` via an
  OKLab mix (SwiftUI default) asserted against iOS values; M3 colour scheme mapped from tokens
  (error = attention); 5 themes incl. Midnight `forcesDark`; light/dark/system; edge-to-edge with
  themed system bars; `sp` text; 48 dp targets. Fonts copied from `Keel/Resources/Fonts`.
  Emoji: Twemoji vector drawables (D14, resolved in M0).

---

## 2. Screens (feature parity checklist)
Port each screen from its iOS file; copy is verbatim (with §3.8 substitutions). Full screen-by-screen
inventory (sections, copy, rules) is in the iOS sources listed here.
- Shell: `App/KeelApp.swift`, `RootView.swift` (`shouldShowMain`), `MainView.swift` (21 routes,
  entry-sheet → modal orchestration, toasts, reminders explainer, health offer, Home button).
- Onboarding (`Features/Onboarding/*`): Welcome, RightPlace, CreateAccount (first name only,
  blank → "there"), Pathway, Health step (Continue only), Ready; onboarded flag in the DB.
- Home (`Features/Dashboard/DashboardView.swift`): day selector, snapshot, medicines card, GP card,
  7-day mood/energy/sleep (Energy label = most-picked level once ≥3 entries, tie → most recent;
  counts otherwise), month calendar, docked bar with one "+".
- Check-in (`Features/CheckIn/*`): mood sheet; detail (energy 1 to 5, sleep manual or read-only
  imported, diary + voice, alcohol (setting default on), symptoms quick chips + picker with 9 groups,
  custom symptoms, edit-mode rename/remove/drag, sensitive Intimacy & bladder opt-in).
- Cycle (`Features/Cycle/CycleTrackingView.swift`), Medications (`Features/Medications/*` incl.
  catalog picker and schedule form), Looking back + All notes (`Features/Patterns/PatternsView.swift`),
  GP Visit Summary 4-step flow (`Features/GPSummary/*`), More (`Features/More/MoreView.swift`),
  Settings, Profile + My background, Your privacy, About, Connect, Health settings, Activities,
  Stats, Reminders, Backup & Restore, Themes, Colour mode, Mood icons (`Features/Settings/*`),
  App lock (`Features/AppLock/*`), Get support (`Features/Support/SupportView.swift`,
  `Services/Companion/CrisisResources.swift`, `tel:` via `ACTION_DIAL`).

---

## 3. Health Connect design
Reference: `Services/HealthKitService.swift`, `HealthIngestor.swift`, `HealthSyncCatalog.swift`,
`MergedActivity.swift`, the health section of `App/AppEnvironment.swift`,
`Features/Settings/AppleHealthSettingsView.swift`, `Features/Onboarding/AppleHealthView.swift`.

### 3.1 Interface
Port `HealthSnapshot` unchanged (`sleepByDay`, `activityAmounts`, `vitals`, `menstrualFlow`,
`flowWindowStart`, `isEmpty`) and `HealthDataSource` as a Kotlin interface (`requestStatus()`,
`requestAuthorization()` via an Activity-scoped launcher, `grantedPermissions()`,
`snapshot(lastDays)`), so the sync/offer logic and its tests port unchanged with a fake.

### 3.2 Metric mapping (read-only; no WRITE_*, no background read)
| iOS id (keep) | Record | Permission `android.permission.health.` | Read → daily value |
|---|---|---|---|
| steps | StepsRecord | READ_STEPS | aggregate P1D `COUNT_TOTAL` |
| exercise | ExerciseSessionRecord | READ_EXERCISE | aggregate `EXERCISE_DURATION_TOTAL`, minutes |
| sleep | SleepSessionRecord | READ_SLEEP | raw with stages, §3.3, hours |
| heartRate | HeartRateRecord | READ_HEART_RATE | aggregate `BPM_AVG` |
| restingHeartRate | RestingHeartRateRecord | READ_RESTING_HEART_RATE | aggregate `BPM_AVG` |
| **hrvRmssd** (D13) | HeartRateVariabilityRmssdRecord | READ_HEART_RATE_VARIABILITY | raw mean, ms |
| bloodPressureSystolic / Diastolic | BloodPressureRecord | READ_BLOOD_PRESSURE | aggregate `SYSTOLIC_AVG` / `DIASTOLIC_AVG` |
| respiratoryRate | RespiratoryRateRecord | READ_RESPIRATORY_RATE | raw mean |
| oxygenSaturation | OxygenSaturationRecord | READ_OXYGEN_SATURATION | raw mean; already %, **no ×100** |
| bodyMass | WeightRecord | READ_WEIGHT | aggregate `WEIGHT_AVG` kg |
| bodyTemperature | BodyTemperatureRecord | READ_BODY_TEMPERATURE | raw mean °C |
| wristTemperature | SkinTemperatureRecord (only if `FEATURE_SKIN_TEMPERATURE`) | READ_SKIN_TEMPERATURE | baseline + mean(deltas) only when a baseline exists; never invent an absolute value |
| activeEnergy | ActiveCaloriesBurnedRecord | READ_ACTIVE_CALORIES_BURNED | aggregate `ACTIVE_CALORIES_TOTAL` kcal |
| flights | FloorsClimbedRecord | READ_FLOORS_CLIMBED | aggregate `FLOORS_CLIMBED_TOTAL` |
| distance | DistanceRecord | READ_DISTANCE | aggregate `DISTANCE_TOTAL` km (all distance) |
| periods | MenstruationFlowRecord + MenstruationPeriodRecord | READ_MENSTRUATION | raw, §3.4 |
| history | n/a | READ_HEALTH_DATA_HISTORY (if `FEATURE_READ_HEALTH_DATA_HISTORY`) | enables the 365-day window (else ~30 days before first grant) |

- Aggregates de-duplicate across apps by data-origin priority (iOS double-counts raw sums): a
  deliberate improvement (D7).
- Mechanics: `LocalDateTime` range `[today-365 00:00, now]`, `Period.ofDays(1)`; include only
  granted permissions per request (one missing permission fails the whole request); chunk into
  90-day ranges if refused; paginate raw reads; catch `SecurityException`/`IllegalStateException`
  per type and treat as empty (does not consume the throttle). Keep 1 dp half-away-from-zero
  rounding and the per-(type, day) upsert.

### 3.3 Sleep (pure port with `HealthSleepMergeTests`)
Per session: no stages → whole session is asleep; with stages → asleep = SLEEPING, LIGHT, DEEP,
REM; stages but none asleep → session span is the in-bed fallback. Key every interval by its
**session's** end day in the device zone (D8). `mergedHours` interval union per day; asleep
replaces in-bed. Read one day early and clip.

### 3.4 Menstruation
FLOW_UNKNOWN → unspecified; LIGHT/MEDIUM/HEAVY → same; day = local start of day; heaviest per day
wins (port `flowByDay`). Days covered by a MenstruationPeriodRecord without a flow record →
unspecified (D5). Spotting never imported. Reconcile deletions only when READ_MENSTRUATION is
granted and the periods item is on; `flowWindowStart = history granted ? today-365 :
max(today-365, firstMenstruationGrantSeenAt - 30d)` (persist `keel.hcFirstMenstruationGrantAt`).
**Invariant:** imported data never feeds CycleStats, PatternEngine, InsightRepository or the GP
summary; manual entries win per day (`MergedActivity`, `CycleDayMark.merge`).

### 3.5 Availability and manifest
- `getSdkStatus(ctx, "com.google.android.apps.healthdata")`: AVAILABLE → proceed;
  PROVIDER_UPDATE_REQUIRED (Android 9 to 13 without the app, or outdated) → open Play
  (`market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding`,
  package `com.android.vending`); UNAVAILABLE → status `.unknown`, connect does nothing.
  Re-check on resume of the Health screen and onboarding step.
- Manifest: `<queries>` for the HC package; one `uses-permission` per row; exported
  `HealthPermissionsRationaleActivity` (action `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`)
  and an `activity-alias` for `VIEW_PERMISSION_USAGE` + category `HEALTH_PERMISSIONS` +
  `START_VIEW_PERMISSION_USAGE` (14+). Without these HC won't show its dialog. The rationale screen
  shows Your privacy (no personal data, not behind the lock).

### 3.6 Permissions, status, offers, sync
- Requestable set = the 16 reads minus feature-unavailable ones, plus history if available.
- `HealthRequestStatus` (parity meaning "has she been asked about everything Keel reads?"):
  SDK unavailable → `.unknown`; persisted `keel.hcAskedPermissions` ⊇ requestable → `.unnecessary`;
  else `.shouldRequest`. Each contract launch adds to the asked set. A new read type in a later
  version → `.shouldRequest` → one `.reconnect` offer (existing logic).
- Connected (D4) = granted ∩ requestable is non-empty after the contract; the screen shows a live
  "access off" state from `getGrantedPermissions()`.
- Onboarding Health step: Continue only → available: contract → connected → forced sync + retries
  at 3/10/30 s → advance; update required: open Play → advance; unavailable: advance. Launch offer:
  Continue only, pure `healthOffer(...)` unchanged, 1.2 s after Home, never with the reminders
  explainer.
- HC stops showing its dialog after repeated denials: replace "Open the Health app" with "Manage
  access in Health Connect" (`ACTION_MANAGE_HEALTH_PERMISSIONS` + `EXTRA_PACKAGE_NAME` on 34+,
  client-library settings intent below; verify constants).
- Sync gating ported exactly: `shouldRunHealthSync(now, last, force, connected, 30 min)`;
  coalesce; queue forced; re-check connected after the read; `HealthSyncCatalog.filter`; only a
  non-empty snapshot stamps the throttle; triggered on ON_START and bootstrap only.
- Disconnect and Delete all my data: `healthConnected = false`, `revokeAllPermissions()`, clear
  the asked set.

### 3.7 Play Console and policy
Health apps declaration with a justification per permission tied to a feature (Activities tiles;
check-in "Sleep last night" + Home sleep strip; Cycle outlined days for menstruation; history for
365-day views). Privacy policy (`https://therecalibrationyears.com/keel-privacy`) must cover
Android and Health Connect incl. the Limited Use statement (owner publishes before submission).
Data safety: no data collected/shared (owner/legal to confirm). Encryption at rest: rely on FBE,
SQLCipher only if review insists (D18). Category Health & Fitness, no ads, target API 36, no
account. Choose `applicationId` in M0 (immutable once uploaded; suggest
`com.therecalibrationyears.keel`).

### 3.8 Copy substitutions (owner sign-off, D12)
Item ids in `keel.disabledHealthItems` stay identical. "Apple Health" → "Health Connect" in
sentences otherwise verbatim (offer titles "Connect to Health Connect?" / "Update Health Connect
access?", onboarding, Activities, Cycle legend and notes, check-in sleep badge, Stats, Health
settings, More row, remove dialog). Catalog: exercise desc "Time in exercise sessions"; distance
"Distance" / "Distance covered"; body temperature "...overnight skin temperature". "Data stays on
your device and in Health Connect." Help text "...open Health Connect, then App permissions, then
Keel..." + "Manage access in Health Connect". **Your privacy** mentions iPhone/iCloud/Apple AI/
Apple Health: the owner supplies Android legal text; release blocked until approved (D3). About
ports verbatim. Biometric/speech labels per D15.

---

## 4. Parity strategy

### 4.1 Port pure logic first, translating every XCTest
Each Kotlin test class keeps the iOS name with `// Port of KeelTests/<File>.swift`.
`android/docs/test-parity.md` maps every iOS `func test...` to its Kotlin test or "N/A: reason";
`tools/check_test_parity.py` fails CI if an iOS test lacks an entry.
| Target | Suites |
|---|---|
| `core/domain` (JVM) | DoseSchedule, DoseScheduleSummary, DoseUnit, MedicationSchedule, MedReminderPlan, ReminderWording, NotificationGate, CycleStats, CycleTimeline, PatternEngine, SymptomTally, LookingBack (+ energy label rule), ActivityAggregation, VitalTrend, VitalLineChart, StatsDayCounts, QuickChipRanking, MedicationNameCleanup, Hysterectomy, PINHasher, CrisisResources, FeedbackMail, ShareInvite, TreatmentCatalog, GPSummary, HealthSleepMerge, HealthSyncCatalog, HealthSyncGating, HealthAccessOffer (pure), RootRouting, PrivacySummary/AboutCopy, KeelMark, ThemeColour, parity helpers |
| `core/data` (Robolectric + in-memory Room) | CheckInRepository, SymptomRepository, CycleRepository(+Flow), MedicationRepository, MedicationHomeLog, Profile, Alcohol, EatingLog, OnboardingState, BackupRoundTrip, EraseAllData (run 20×), AppleHealthPeriods (→ HealthConnectPeriods), HealthSleepSource, HealthImportPurge, HealthConnectImportIntegration (fake source / FakeHealthConnectClient), HealthSyncBehaviour, HealthAccessOffer (controller), GPSummaryService, AppLockService, ReminderExplainer |
| Source/manifest scans (JVM) | PrePermissionScreen (no "Not now"/"Skip" before permission prompts), SubmissionPackA (read set = manifest permissions = catalog), BackupRulesManifest (replaces KeychainCleanup), ThemeColour fixed-colour scan |
| androidTest | GPSummaryPDFRenderer (pages, fit fallback, filename, metadata stripped), SpeechOnDevice gating, AlarmScheduler (`dumpsys alarm`), notification action receiver |

### 4.2 Shared golden fixtures (repo root `fixtures/`)
1. `fixtures/backup/v1-ios-sample.keelbackup`: every DTO; optional fields present and absent;
   legacy shapes (`startDate`, missing severity/source/isDefaultChip/alcoholCount, ActivityLog
   without envelope, `frequencyRaw` without `scheduleKindRaw`, "(e.g. …)" name,
   `basalBodyTemperature` sample); a dangling link; custom `categoryRaw`; two-slot
   `doseTimesJSON`; Unicode notes; doubles 7.5/8/0.1; start-of-day instants across a Sydney DST
   change. Tests: decode snapshot; restore → export equals `v1-expected-roundtrip.json`
   (canonical, ignoring `exportedAt`/`appVersion`); `version: 2` rejected with the exact message;
   Android writes `v1-android-export.keelbackup` (committed) and iOS restores it.
2. `fixtures/gpsummary/<case>/{input,expected}.json`: records + `GPSummaryInputs` + fixed `now`,
   UTC; expected = every resolved document string, **generated from iOS** (source of truth).
   Cases: empty; sparse previous window; three priorities (cap 5); >10 supplements; stopped MHT
   in window; spotting adjacency; hysterectomy opt-in.
3. Optional `fixtures/schedule/*.json` for `plannedOccurrences` / `isDue`.

The only iOS change is **test-only**: `KeelTests/SharedFixtureTests.swift` reading `fixtures/` via
`#filePath` (as `PrePermissionScreenTests` does), UTC in `setUp`. Confirm with the owner first
("iOS stays as is").

### 4.3 Copy kept identical
- `core/copy` Kotlin objects mirror iOS (`GPSummaryCopy`, `PrivacySummary`, `AboutCopy`,
  `NotificationCopy`, `OnboardingCopy`, ...); interpolated strings are functions. No strings.xml
  (English-only; verbatim comparison is simpler).
- *As built in M0:* no committed inventory. The tests extract string literals straight from
  `Keel/**/*.swift` (skipping comments, `#if DEBUG` branches, `#Preview`, DebugHarness) and from
  every shipping Android Kotlin file (`src/main`, `src/release`), so they can't go stale; both
  trees are Gradle inputs. Interpolations compare as `{}` on both sides.
- `CopyParityTest`: every Android literal that looks like copy (has a space or starts with a
  capitalised word) exists verbatim on iOS, or is the `android` side of an entry in
  `platform-substitutions.json`, or is listed in `android-only-literals.txt`; lines marked
  `// not-copy: <reason>` are skipped. It also writes
  `core/copy/build/reports/copy/ios-not-yet-on-android.txt` (coverage, a gate only at M8, when
  the release check also fails on any unapproved substitution).
- `CopyLintTest`: no U+2014/U+2013; wince list ("honestly", "to be honest", "midlife",
  "hot flash", ...); US spellings (color, favorite, -ize, estrogen, center, ...); no "Apple",
  "iPhone", "iCloud", "Face ID", "Touch ID", "App Store", "HealthKit", "iOS" (`lint-allowlist.txt`
  for exceptions). The GP banned-verb guard stays with `GPSummaryCopy` (M5), as on iOS.

---

## 5. Roadmap
Each milestone ends with `./gradlew check` green, emulator screenshots (API 28 and 36, light and
dark) and a short "not verified" note.

| M | Scope | iOS reference | Exit criteria |
|---|---|---|---|
| **M0** Scaffold | Gradle, catalog, convention plugins, modules, font/catalog copy tasks, DI, `KeelClock`; KeelTheme + 5 themes + dark + OKLab; typography; components; Material Symbols map; KeelMark; adaptive icon (`brand/`, background `#8C4A45`, monochrome layer) + splash; EmojiGlyph spike (D14); `core/copy` + extraction + lint/parity tests; `android/CLAUDE.md`; DebugHarness skeleton; debug component gallery; applicationId | `DesignSystem/*`, `Components/*`, `Resources/Fonts`, `brand/` | Gallery screenshots in 5 themes × light/dark; ThemeColour, KeelMark, copy lint green; R8 release build launches |
| **M1** Domain + data + backup | All of `core/domain` with ported tests; Room v1 schemas, DAOs, repositories, SettingsStore, SecureStore, symptom seeding, legacy migrations; BackupService; `fixtures/backup`; iOS `SharedFixtureTests` | `Models/*`, `Persistence/*`, `Services/{CycleStats,PatternEngine,MonthSummary,SymptomTally,ActivityAggregation,MergedActivity,VitalTrend,PINSecurity,FeedbackMail,TreatmentCatalogService}`, `Services/Backup/*`, `GPSummaryBuilder` (pure), `SettingsStore` | M1 suites pass; backup fixture passes on both platforms; schema JSON committed |
| **M2** Onboarding, Home, check-in | `KeelAppController`, routing, 6 onboarding steps (Health step advances via a `.unknown` source until M7), all 21 routes (unbuilt ones show only their title, never sample data), Dashboard, EntrySheet → CheckInModal state machine, voice, alcohol, symptoms + picker, mood packs | `Features/Onboarding/*`, `Dashboard/*`, `CheckIn/*`, `App/{RootView,MainView,KeelApp,AppEnvironment}`, `Services/SpeechRecognitionService` | RootRouting, OnboardingState, CheckInRepository, Alcohol, QuickChipRanking, SymptomRepository, SpeechOnDevice tests; Compose UI tests (create/edit/delete/change mood); screenshots of each onboarding step and Home empty/filled |
| **M3** Medications + reminders | Catalog picker, detail form + schedules, Home medicines card, AlarmScheduler + registry + receivers + channels + actions + body tap, explainer → POST_NOTIFICATIONS, Settings toggles, Reminders screen, reschedule receivers, optional daily top-up | `Features/Medications/*`, `Models/{Medication,MedicationLog,DoseSchedule}`, `MedicationRepository`, `NotificationService`, `NotificationCoordinator`, `RemindersView` | MedReminderPlan, MedicationRepository, MedicationSchedule, MedicationHomeLog, ReminderWording, ReminderExplainer, NotificationGate tests; androidTest for alarms; emulator check: fire, Mark taken, Always mark taken, body tap, reboot, Doze |
| **M4** Cycle, Looking back, Activities, Stats | Cycle screen (imported days empty until M7), Looking back + All notes + insight + GP link, Activities day/week/month, eating, water, Stats | `Cycle/*`, `Patterns/*`, `Settings/{ActivitiesView,ReportsView}`, `EatingLog`, `VitalLineChart` | CycleStats, CycleRepository*, CycleTimeline, LookingBack, PatternEngine, ActivityAggregation, StatsDayCounts, EatingLog, VitalTrend, VitalLineChart tests; screenshots |
| **M5** GP Visit Summary | 4-step flow (presets + custom; review removals; details with limits and opt-ins; preview), Home confirmation past step 1, back = previous step, renderer + metadata strip + preview + share | `Features/GPSummary/*`, `Models/Hysterectomy` | GPSummary, GPSummaryService, `fixtures/gpsummary` on both platforms; renderer androidTest; both PDF pages screenshotted |
| **M6** Settings, privacy, app lock | More, Settings (typed-DELETE dialog), Profile + background, Your privacy (table scrolls, per-row a11y text, Android wording per D3), About, Connect, Get support, Tell your friends, Backup & Restore (SAF), Themes, Colour mode, Mood icons, App lock (PIN, biometrics, forgot PIN, recents privacy), `eraseAllData` (incl. alarms/registry, notifications, lock, HC revoke, settings, owner) | `More/*`, `Settings/*`, `Support/*`, `AppLock/*`, `Services/{AppLockService,PINSecurity,ShareInvite,FeedbackMail}`, `AppEnvironment.eraseAllData` | AppLockService, PINHasher, PrivacySummary, Profile, Hysterectomy, FeedbackMail, CrisisResources, ShareInvite, EraseAllData, PrePermissionScreen tests; real-device biometric/credential on API 28/29 and 34+; export → uninstall → reinstall → import |
| **M7** Health Connect | All of §3: manifest + rationale, data source, sleep/menstruation, ingest, status, offers, Health screen (card, Sync now, Disconnect, 10 switches, manage access, remove imported data, no-data note), wiring into Activities, Cycle, check-in sleep, Home strips | §3 references | HealthSyncGating, HealthSyncBehaviour, HealthAccessOffer, HealthConnectPeriods, HealthSleepMerge, HealthSleepSource, HealthImportPurge, HealthSyncCatalog, HC import integration, SubmissionPackA with fakes; Play emulator API 34+ with Health Connect Toolbox data (stages, two step sources for dedupe, flow + period records, skin temp with/without baseline); real Android 13 device (install flow, 30-day vs history); real 14+ device with a wearable |
| **M8** Polish + release | TalkBack labels, headings; font scale 200%; 48 dp; contrast in all themes; reduce motion; large-screen resizing; R8 rules (serialization, Room) checked on a release build; versionCode bump wired; Play listing, Data safety, Health declaration, policy update, Play App Signing (upload key outside repo), internal track | `DESIGN_PRINCIPLES.md` | Internal-track install on two real devices (≤13 and 14+); manual run-through checklist signed off; all substitutions approved; `grep -rn "// FAKE:" android/` reviewed |

---

### M0 outcome (21 Oct 2026)
Built: Gradle scaffold (`app`, `core/domain`, `core/copy`, `core/designsystem`); fonts copied
from `Keel/Resources/Fonts` at build time; `KeelTheme` + 5 themes + dark + OKLab mix; type scale;
metrics; buttons, cards, inputs, segmented, symptom chip, radio card, emoji glyph, toast,
`KeelScreen` with floating header; `KeelMark`; adaptive launcher icon (disc at the brand art's
60% of the tile); system bars follow Keel's colour mode; debug harness + component gallery;
copy parity/lint tests; `android/CLAUDE.md`. Tests (all green): ThemeColourTests 5,
KeelMarkTests 3 (pixel match against `brand/keel-icon-1024.png`), TwemojiVectorsTest 3,
CopyParityTest 3, CopyLintTest 4, LiteralExtractorTests 10. Lint: 0 errors.
Verified on emulators API 36 and API 28: gallery in 5 themes × light/dark, emoji, system bars,
launcher icon, R8 + resource-shrunk release build launches with no debug code in its dex.
Deferred: monochrome (themed) launcher layer (needs brand art, owner), `core-splashscreen`
(M2 with the app shell), Material Symbols mapping table (grows with each screen; M0 has back,
home, check, plus, leaf, chevron), `KeelClock` (M1 with its first user), API 33 AVD (M3/M7).

### Onboarding slice (21 Oct 2026)
Built: `core/data` (Room `MainDatabase` v1 with `user_profile`, `UserRepository` with the iOS
operations, `SettingsStore` with iOS keys, `LocalIdentity` `local-<UUID>`, `DeviceContext`),
`core/domain` (`Pathway`, `RootRouting`, `KeelClock`, `SyncStatus`), `feature/onboarding`
(all six screens, copy verbatim; back gesture returns a step), app shell (`AppContainer`
manual DI instead of Hilt: nothing yet needs Hilt's codegen; `KeelAppController`; `KeelRoot`;
splash held until saved state loads; theme from saved settings). Tests: RootRouting 7,
Pathway 2, UserRepository 12, LocalStores 3, OnboardingFlow 7 (Robolectric Compose),
KeelAppController 6; mapping in `android/docs/test-parity.md`. Verified on emulators API 36
and 28 (fresh install → all six screens → saved profile read back from the device database →
relaunch lands in the app), dark mode, Midnight, 200% font size, and the R8 release build.
Stand-ins (`// FAKE:`): Home (shows only the mark) and the Health Connect request (Continue
moves on, nothing is read) until M7. Owner sign-off pending: the Health Connect wording on the
health step (in `platform-substitutions.json`, D12).

### Home slice (21 Oct 2026)
Built: domain rules (Mood + mood packs, EnergyLevel + the "most picked" label, severity,
Greeting, MergedActivity, CalendarGrid, iOS rounding and `%.1f` parity helpers); Room tables
`check_in`, `symptom`, `check_in_symptom`, `medication`, `medication_log`, `activity_log` (all
iOS columns, iOS delete rules); repositories for Home's reads and the medicine tick;
`feature/home` (pure `HomeModel` + `HomeScreen`: day selector, greeting, entries, medicines,
GP card, mood/energy/sleep strips with tap-to-read, month calendar, docked buttons);
Navigation 3 shell with the Home button; title-only stand-ins for Cycle, Medications,
Looking back, More, GP Visit Summary; debug seeds. Tests: domain 26, data 22, HomeModel 9.
Verified on emulators: empty and filled Home, medicine ticks and the 7-day count step by step.
Finding: on SwiftShader (software GPU) the emoji vectors made Home take 10 s to open; on a GPU
0.6 s. Emulators now always start with `-gpu host`; check emoji rendering speed on a real
low-end phone in M8. New TalkBack labels iOS lacks are in `android-additions.json` for
sign-off (iOS's Home arrows and docked buttons have no explicit VoiceOver labels and rely on
the SF Symbol's built-in name: worth checking with VoiceOver).

### Check-in slice (21 Oct 2026)
Built: symptom catalog (54 built-ins, version-gated seeding with renames and archive-never-delete,
quick-chip ranking, find-or-create ignoring case), check-in create/update (link reconcile)/delete
(tombstones), alcohol (setting on by default), manual sleep; `feature/checkin` (mood slide, entry
screen, "more" picker with groups, sensitive opt-in, add your own, edit mode rename/remove,
long-press drag between groups with edge auto-scroll); toasts; voice notes in `core/platform`
(on-device only: unavailable on Android 9 to 11 and when her language isn't installed).
Verified on emulators: create → Home → edit (Change mood) → remove, each read back from the
device database; picker opt-in, add your own, rename, drag to another group; voice permission
prompt and the unavailable message on API 28 and 36. Not verified: dictation itself (needs a real
phone with an on-device language pack). Owner sign-off pending: the microphone message (D15).

### M3 medications (21 Oct 2026)
Built: `core/domain` schedule rules (`DoseSlot`, `DoseSchedule` with due days, cycles, upcoming
dates and the summary sentence), dose units, methods, kinds, the stored-column mapping (iOS
`doseTimesJSON` shape, upper-case slot ids, weekdays 1 = Sunday), legacy migration and
brand-example cleanup rules, auto-log decision, `TreatmentDraft`, the treatment catalog model
and search, and the reminder rules (`plannedOccurrences`, `cycleHorizon`, `dayKey`, wording,
Android notification gate, `ReminderConfig`). `core/data`: full `MedicationRepository` (add as
upsert by name, update, stop/reactivate, archive, tick, auto-log, launch migrations),
`TreatmentCatalogStore` (iOS `treatment-catalog.json` copied into assets at build time; cached
copy wins), reminder settings with iOS keys. `feature/medications`: Medications screen (groups,
"No longer taking", track tick, Auto-logged badge, empty state), picker sheet (tabs, search,
"Add your own", "Add “…”"), details form (strength and unit menu, method, weekly/cycle/as
directed, per-dose times with the system time picker in her 12/24 hour setting, per-dose days,
cycle steppers, auto-log, note, still taking + optional stop date). Launch now also runs the
legacy schedule migration, the name cleanup and today's auto-log, in iOS order. Seeding the
symptom catalog is now one transaction (it was row by row, which made a launch test flaky).
Tests: domain +64, data +30, Medications 17, controller +3 (267 in all). Verified on emulators:
add from the list with two times and narrowed days → saved row read back from the device
database (iOS JSON shape) → edit → stop with a date → "No longer taking"; API 28 dark mode;
an old-style entry migrated on launch. Launch time measured against the previous commit,
interleaved: no change (release about 2.3 s on the loaded API 28 emulator either way).
Not verified: TalkBack; the date and time pickers use the system font, not Poppins (M8).

### M3 reminders (21 Oct 2026)
Built: reminder ids, everyday reminder plans and next-fire times, medication reminder plans
(`core/domain`); `AlarmReminderScheduler` with a device-only registry, one notification
channel per reminder kind named as on the iOS Reminders screen, a status icon from the mark
(`core/platform`); `KeelReminders` (iOS reminder methods), receivers for the alarm, "Mark
taken", "Always mark taken" and re-arming (boot, time, zone, update, exact-alarm permission
change), body tap marks the dose taken (D20) on the occurrence's day (D10), foreground top-up
and auto-log (`app`); "Gentle reminders" on Home with Continue only, then Android 13's prompt;
the Reminders screen (`feature/settings`, reached by debug route until Settings in M6). Android
honours the "Medication reminders" toggle (D2). Tests: domain +11, platform +6, app +25,
Reminders screen 3 (312 in all). Verified on the API 36 emulator: explanation → Android prompt
→ Allow → check-in armed; a medicine and the check-in fired at their times; "Mark taken",
"Always mark taken" and a body tap each logged the dose (read back from the device database)
and cleared the notification; 25 alarms re-armed after a reboot; a reminder delivered in forced
deep Doze; Hydration turned on lays 7 alarms. API 28: the explanation with no prompt, exact
alarms. **Finding for the owner:** on Android 14+ exact alarms aren't granted by default (Android
12 and 13 grant them), so without them Android may deliver a reminder up to an hour late
(`window=+1h`). The owner chose to offer them (D25, below). Not done: the daily top-up (D11;
reminders are topped up whenever Keel opens, as iOS); the Settings master switch and
"Medicine names in reminders" (with Settings, M6; the logic is in place and tested). Not verified: a real phone (OEM battery savers can delay
or drop alarms), the lock-screen look with names hidden, TalkBack.

### Reminders on time (21 Oct 2026, D25)
Built: `ExactTiming` rules (`core/domain`); `ExactAlarms` (`core/platform`: whether Android
allows them, and its "Alarms & reminders" page for Keel, falling back to app info);
`keel.exactTimingOffered` (Android-only setting); `KeelReminders.shouldOfferExactTiming` after a
medicine is added or edited; the dialog (Continue only, not dismissible) hosted in `MainShell`;
the Reminders screen note (tap opens the same page), re-checked when she comes back. Granting
sends `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, which `RescheduleReceiver` already
handled. Copy is Android-only (four strings in `android-additions.json`, owner sign-off).
Tests: domain +2, app +4, settings +4 (322 in all). Verified on the API 36 emulator: saving a timed medicine
(through the form, and through the debug seed) shows the dialog after the form closes; Back
doesn't dismiss it; Continue opens Keel's "Alarms & reminders" page; allowing it re-armed all
32 reminders exact (`window=0`) while that page was still open, so through the receiver; a
reminder then posted at 21:31:00.04 for 21:31; turning it off stopped Keel and Android dropped
its alarms, and the next launch re-armed all 32 inexact; the note shows then, opens the page,
and is gone on return once allowed; a second medicine after being told once shows nothing.
API 28: no dialog, no note, all exact. Release (R8) build on API 36: onboarding's "Gentle
reminders" came alone; editing a timed medicine showed the dialog and Continue opened the page.
Not verified: real phones, whose "Alarms & reminders" pages can differ from Pixel's; TalkBack.

### M4 Cycle (21 Oct 2026)
Built: `FlowLevel`, `CycleEntryType`, `CyclePhase`, `CycleStats` (on `LocalDate`s), the
timeline window, `CycleDayMark.merge`, `InsightAccent`, `PatternFinding` and `PatternEngine`
(cycle-length range only, per the no-AI rule) in `core/domain`; the `cycle_entry` table (schema
v1 edited, pre-release) and `CycleRepository` (`setFlow`, `togglePeriodDay`, `stats(400)`,
`cycleStart`, `estimatedPhase`); `feature/cycle` with `CycleModel` and the screen: timeline
centred on today with flow fills, today ring and dashed estimate, legend, history, estimated
phase, the real cycle-length insight, the month grid for past days, the Profile link (Profile is
a title-only stand-in until M6), and the log sheet. Imported period days are empty until M7 (the
screen already shows and labels them; the five "Apple Health" lines are Health Connect
substitutions for owner sign-off). Tests: domain +23, data +13, cycle +15. Verified on the API
36 emulator: seeded cycles show day 4 and the estimated window; logging a past day through the
sheet wrote a `manual` row at the start of her local day and the screen updated live (new
start, history, and the insight once the gaps varied by 7+ days). API 28: dark mode and the
sheet. Finding for M8: in dark mode the default theme keeps rosewood as the accent, so accent
text on tinted chips is about 1.9:1 (iOS identical). Not verified: TalkBack reading order.

## 6. Decisions (recommendation; "owner" = needs Mischa's sign-off before release)
| # | Topic | Recommendation |
|---|---|---|
| D1 | Backup drops Medication `isTracked/autoLogDoses/stoppedAt` | Android writes them as extra optional keys (iOS ignores unknown keys, still v1); suggest the iOS fix later |
| D2 | iOS ignores the "Medication reminders" toggle | Honour it on Android; flag as an iOS bug (owner) |
| D3 | Your privacy text mentions iPhone/iCloud/Apple AI/Apple Health | Owner supplies Android legal text; release blocked until approved (owner) |
| D4 | Health "connected" | Real grants; Disconnect and Delete all my data also `revokeAllPermissions()` |
| D5 | MenstruationPeriodRecord | Include: covered days without flow → unspecified |
| D6 | Store-open failure wipes data on iOS | Don't port; real migrations; calm error screen, never delete |
| D7 | Activity sums double-count on iOS | Use HC aggregates (dedupe) |
| D8 | iOS keys each sleep sample by its own end day | Key by session end day |
| D9 | Android Auto Backup | `allowBackup=false` + extraction rules excluding all (Keystore secrets can't restore; HC data stays on device); `.keelbackup` export is the backup path (owner; affects privacy text) |
| D10 | Day logged by a notification action | Use the occurrence `dayKey`, not delivery time |
| D11 | Reminders only topped up when the app opens | Add a daily WorkManager top-up; keep `cycleHorizon` identical |
| D12 | Health Connect copy substitutions | §3.8 table (owner) |
| D13 | HRV type differs | Store RMSSD as `hrvRmssd`; restored iOS `hrv` rows kept, not charted |
| D14 | Emoji rendering | **Resolved in M0:** the COLR font renders in colour on API 36 but blank on API 28, so `tools/twemoji_to_vectors.py` generates vector drawables for the 28 mood emoji from the iOS font; the font no longer ships on Android (CC-BY credit already in About) |
| D15 | "Face ID"/"Touch ID"/"passcode", "microphone and speech access" | "biometrics", "Use biometric unlock", "Unlock with biometrics."; "Voice notes need microphone access. You can type instead, or allow access in Settings." (owner) |
| D16 | App-switcher privacy | 33+ `setRecentsScreenshotEnabled(false)`; 28 to 32 `FLAG_SECURE` while lock is on |
| D17 | Export | SAF `CreateDocument` named `Keel-backup-yyyy-MM-dd-HHmm.keelbackup` |
| D18 | Encryption at rest | Rely on Android FBE; SQLCipher only if Play review asks (owner) |
| D19 | Custom-category symptoms fall back to Body in `grouped()` | Check what the iOS picker actually shows; if custom groups land under Body, group by `categoryRaw` |
| D20 | Tapping a medication notification marks it taken | Keep (copy says "Tap to mark it taken.") |
| D21 | Legacy OwnershipMigration / CloudKit / Sign in with Apple remnants | Don't port; pass `appleUserID` through backups only |
| D22 | Auto-log only when the app is foregrounded | Keep parity ("unless you change it") |
| D23 | Themes/packs have prices but all owned | Port as-is, no billing |
| D24 | Share link | Play URL for the applicationId, `// FAKE:` until the listing is live |
| D25 | Exact alarms on Android 14+ (off by default; reminders up to an hour late) | **Owner chose (21 Oct 2026):** offer them. "Reminders on time" once after she saves a medicine with reminders (Continue only, opens Alarms & reminders), plus a note on the Reminders screen while off. Not `USE_EXACT_ALARM` (Play reserves it for alarm and calendar apps). Copy: owner sign-off |

**Android-only UX:** system/predictive back pops routes (CheckInModal back = close without
saving, GP flow back = previous step); notification permission only on API 33+, after "Gentle
reminders" Continue; exact alarms offered once after a medicine with reminders is saved, never during onboarding (D25); edge-to-edge insets; M3 time picker
honouring 24 h; +/− steppers; tap outside clears focus.

---

## 7. Verification
| Layer | Tooling | When |
|---|---|---|
| Pure rules | JUnit on JVM, fixed UTC `KeelClock` | every change |
| Repositories, backup, controller | Robolectric + in-memory Room + Turbine; fake HealthDataSource / FakeHealthConnectClient; fake biometrics, in-memory SecureStore | every change |
| Shared fixtures | same `fixtures/` in JUnit and XCTest `SharedFixtureTests` | M1, M5, any format change |
| UI | Compose UI tests (onboarding, check-in, meds form, GP steps, delete-all); Roborazzi screenshots (5 themes × light/dark, font scale 1.0/2.0) | M2 onward |
| Emulator | AVDs API 28, 30, 33 (Play image for HC), 36 (Play); DebugHarness deep links; `adb exec-out screencap -p`; R8 release build each milestone | every milestone |
| System integration | androidTest: AlarmScheduler (`dumpsys alarm`), receivers, PDF renderer + pdfbox text, speech gating | M2, M3, M5 |
| Real devices (state plainly what wasn't checked) | notification delivery in Doze and after reboot; biometrics/credential on 28/29 and 34+; on-device speech; HC with real sources, 30-day vs history, install flow on Android 13; Play internal track | M3, M6, M7, M8 |

Commands (put in `android/CLAUDE.md`): `cd android && ./gradlew check`,
`./gradlew testDebugUnitTest`, `./gradlew connectedDebugAndroidTest`,
`./gradlew :app:assembleRelease`, `./gradlew verifyRoborazziDebug`.

## 8. First steps for the next session
1. ~~Copy this plan; install the SDK; create AVDs~~ done (API 28 and 36; add API 33 Play for M3/M7).
2. Ask the owner about D9 (backup), D3/D12/D15 (copy), the iOS test-only fixture change and the
   `applicationId`, then start M1.

## Sources (checked 20 Oct 2026)
Health Connect releases, get started, read data, aggregate data, data types, HealthConnectClient
reference, publish guide (developer.android.com/health-and-fitness); Play Health Connect policy and
health apps declaration (support.google.com/googleplay); Play target SDK requirements; Compose BOM
mapping; Room, Navigation 3, DataStore, WorkManager, Biometric, Security releases; Schedule alarms
guide; Kotlin, kotlinx.serialization, Dagger, AGP, androidx.hilt release notes.
