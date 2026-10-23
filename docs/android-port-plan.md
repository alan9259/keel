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
> "Reminders on time" for exact alarms (D25). Open: the daily top-up (D11). **M4 is done:**
> Cycle, Looking back, Activities and Stats (see the "M4" sections); imported health data
> fills them in M7, and Activities and Stats are reached from More in M6. **M5 is done:** the
> GP Visit Summary (see "M5 GP Visit Summary"). **M6 is done** (see the four "M6" sections):
> More, Settings and "Delete all my data", Themes, Colour mode, Mood icons, Profile, About,
> Connect, Get support, Backup & Restore (with the shared backup fixtures) and the app lock. Your
> privacy is built but shows its title only until the owner's Android wording arrives (D3).
> **M7 is done** (see "M7 Health Connect"): reader, permissions, connect, launch offer, sync,
> the Health Connect screen, and imported data on Home, check-in, Cycle, Activities and Stats.
> **M8 is under way** (see "M8 accessibility and large screens"): TalkBack names, 48 dp targets,
> 200% text, reduce motion, tablets and contrast are done; the release build and Play steps
> come next.

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

### M6 More and Settings (22 Oct 2026)

Built in `feature/settings`: More (iOS `MoreView`: every section and row, the version footer,
"Share feedback" and "Request a feature" as a mailto draft in her own mail app with the iOS
"No email app set up" fallback, "Tell your friends" through the share sheet), Settings (the
three notification toggles, the master switch behind the same permission gate as Reminders,
Alcohol, Your privacy, and "Delete all my data" behind typed DELETE), Themes, Colour mode and
Mood icons. Activities, Reminders and Stats are now reached from More rather than only by debug
route. Pure ports: `FeedbackMail`, `ShareInvite` (core/domain). "Delete all my data"
(`KeelAppController.eraseAllData`, iOS order): every database row in one transaction
(`RecordEraser`), built-in symptoms re-seeded, every armed reminder and every posted
notification cleared, her preferences reset (kept: the symptom catalog version, and whether
Keel has asked Android for notification permission, which is the phone's state), the local
identity forgotten, and, Android only, the GP summary PDF left in the share cache. It runs in
the app's scope, since the Settings screen is gone as soon as her profile is. The app lock
(this milestone) and imported health data (M7) join it when they exist. Verified on the API 36
emulator, debug and release: delete from a seeded phone (184 entries, 22 medicines) left 0 rows
but the 54 built-in symptoms, 0 alarms, 0 notifications, no shared PDF, then onboarding again
as a new person; on API 28 in dark mode, the personalisation screens and the dialog above the
keyboard. Not verified: a mail draft's contents in a signed-in mail app (the emulator's Gmail
has no account; the draft is unit-tested). Title-only stand-ins until their slices: Profile,
App lock, Backup & Restore, Get Support, Connect with Us, About Keel, Your privacy (and Health
Connect, M7). The Play link in `ShareInvite` is `// FAKE:` until the listing is live (D24).

### M6 Profile, About, Connect, Get support (22 Oct 2026)

Built in `feature/settings`: Profile with My background (iOS `ProfileView`, `MyBackgroundFields`;
the pure form rules are core/domain `ProfileForm`), About (owner's text, `AboutCopy`), Connect
with Us (Instagram and Facebook glyphs single-sourced from the iOS asset catalog and drawn black
or white, never themed; links open the app or browser; email draft), and Get support
(`CrisisResources`, region by time zone then locale, both countries elsewhere; a line opens the
dialler with the number filled in, `ACTION_DIAL`, no permission). Two small Android-side
improvements, same wording: "Changes saved" scrolls into view (it appears below the button), and
a long placeholder stays on one line (as iOS) so a field doesn't change height as she types.
**Your privacy is built but not shown:** `YourPrivacyScreen` renders a `PrivacySummary` (short
version, the sideways-scrolling table with each row read by TalkBack as one sentence with its
headings, the policy and Support links), tested on sample text. `PrivacySummary.android` is null
until the owner supplies the Android wording (D3), so the route still shows the title-only
stand-in. The iOS text can't be reused: it says records "may also be included in your iPhone's
own backups" (Android backup is off, D9), names iCloud or computer backups, "Speech recognition
and supported Apple AI features" (Keel on Android has no AI; speech is on-device, D15), and
"Imported Apple Health readings" (Health Connect, M7). Verified on the API 36 emulator: Profile
filled, saved and reloaded (age 49 stored as 1977, answer, year, note), About, Connect, Get
support (Australia from the emulator's Brisbane zone); Connect and Get support on API 28 in dark
mode. Not verified: dialling on a real phone, Instagram or Facebook apps (not installed on the
emulators; the links are unit-tested).

### M6 Backup & Restore (23 Oct 2026)

The backup format (plan §1.5, which M1 had left for later) is ported in core/data `backup/`:
`BackupCodec` writes and reads the iOS `KeelBackup` JSON by hand (kotlinx's tree API, no plugin),
with the same keys, every top-level key iOS requires (`chatMessages` and `dailySummaries` as
`[]`), dates in whole seconds UTC (Swift's `.iso8601` rejects fractions), empty optionals left
out, keys sorted, whole numbers without a fraction (as Swift writes a Double), ids upper case,
and the iOS DTOs' fallbacks on reading (startDate, severity, source, isDefaultChip, the schedule
columns only beside a schedule kind, activity logs without the envelope). Medicines carry
`isTracked`, `autoLogDoses`, `stoppedAt` as extra keys (D1; iOS ignores them). `BackupService`
exports every row (removed ones too), refuses a newer format with the iOS message, and restores
in one Room transaction, keeping this phone's Health Connect state and onboarding, skipping
links to missing rows. Imported readings (`HealthSample`) now have the separate health database
the plan describes (`keel-health.db`, M7 adds the other imported tables), so an iPhone backup's
readings survive, and "Delete all my data" clears it. After a restore Android also runs the two
legacy medicine tidy-ups and lays reminders out again (iOS waits for the next launch). Screen:
`BackupRestoreScreen`, Export through Android's "Save to" picker and Restore through its file
picker (D17); one Android-only sentence, "That file isn't a valid Keel backup." (iOS appends the
system's error text), is in `android-additions.json`. **Cross-platform proof without changing
iOS:** `android/tools/ios-backup/run.sh` compiles the iOS backup structs (extracted from
`KeelBackup.swift` without their model methods) with Swift: `fixture` writes
`fixtures/backup/v1-ios-sample.keelbackup` with Swift's own `JSONEncoder` (legacy shapes, a
dangling link, a custom category, a two-slot schedule, Unicode notes, doubles 7.5/58/0.1,
Sydney days either side of the 4 Oct DST change), and `read` decodes any file with Swift's
`JSONDecoder` as iOS does. Verified on the API 36 emulator (debug and release): the iPhone file
restored through the file picker (3 check-ins, links rebuilt and the dangling one skipped,
Magnesium carried onto a schedule, the example brand removed, readings restored, 32 medicine
reminders armed, not connected, still onboarded); Export saved `Keel-backup-…-2204.keelbackup`
and Swift decoded it; export, uninstall, reinstall, restore brought everything back. On API 28 a
real 100-check-in store exported (175 kB) and Swift decoded it. Not verified: the iOS app itself
restoring an Android file (Swift's decoder reads it; the iOS restore code wasn't run), cloud
providers in the picker (Drive), and very large archives.

### M6 App lock (23 Oct 2026)

Ported from iOS `AppLockService`, `PINSecurity`, `AppLockSettingsView`, `PINSetupView`,
`LockGate`, `PINComponents`. Pure rules in core/domain (`PINHasher`, the same bytes as iOS;
`AppLockRules`: 4 digits, 5 tries then 60 s, 30 s grace); `AppLockService` in core/data
(state as a `StateFlow`, the launch screen waits for it so nothing shows unlocked; flags and the
attempt count in the main preferences under the iOS keys); the PIN's salt and hash in
`KeystorePINStore` (AES-GCM, key in the Android Keystore, its own `keel-secure` file; a blob
whose key is gone reads as no PIN). Biometrics: `SystemBiometricAuthenticator` in core/platform,
`BiometricPrompt` with `BIOMETRIC_WEAK` and the iOS "Enter PIN instead" as its button; that
button opens Keel's PIN pad directly (Android only: iOS would ask a second time). Forgot PIN:
`BIOMETRIC_WEAK or DEVICE_CREDENTIAL`, Android's own screen-lock screen. `MainActivity` is now a
`FragmentActivity`. The lock screen and PIN setup are full-screen dialogs, so they sit above any
dialog or sheet already open. Lifecycle: process start applies the grace window, process stop
marks the time. App switcher (D16, refined): while the lock is on, Android 13+ takes no
snapshot (`setRecentsScreenshotEnabled(false)`), Android 9 to 12 mark the window secure (which
also blocks screenshots of Keel), and on Android 10+ Keel draws the iOS privacy cover whenever it
stops being the top app: the switcher shows Keel's live window when opened straight from it,
which the snapshot setting alone didn't hide (seen on the API 36 emulator, then fixed). "Delete
all my data" turns the lock off. Wording (D15, owner): "biometrics" for Face ID / Touch ID and
"screen lock" for the device passcode, ten sentences in `platform-substitutions.json`. Verified
on emulators: API 36 (debug and release) set a PIN, cold launch locked, wrong PIN message, right
PIN, grace (5 s stays open, 36 s locks), the app switcher card covered, "Delete all my data"
turns it off; with an emulator screen lock and enrolled fingerprint: the prompt on arrival,
fingerprint unlock, "Enter PIN instead" to the pad, forgot PIN through the screen lock to a new
PIN. API 28: set, cold launch locked, PIN unlock, the window secure while on and not after. Not
verified: real fingerprint or face hardware, biometrics or the screen-lock prompt on Android 9
and 10 (the API 28 emulator has no fingerprint set up), OEM screen-lock screens.

### M7 Health Connect (23 to 24 Oct 2026)
Built in five slices: the import rules and separate health store (`HealthImport.kt`,
`HealthIngestor`, `HealthDatabase`); the reader (`core/platform` `HealthConnectSource`:
aggregates in 90-day pieces for totals and averages, paged records for sleep stages, HRV,
breathing, blood oxygen, temperatures and periods, skin temperature only with a baseline); the
manifest (exactly `HealthConnectReads.all`, the package query, the privacy rationale activity and
permission-usage alias, all checked by `HealthConnectManifestTests`); `KeelHealth` in `app` (port
of the health part of `AppEnvironment`: connect, launch offer, sync, disconnect, item switches,
remove imports) behind `HealthDataSource` and `HealthPermissionRequesting`, so the iOS sync and
offer tests port against fakes (the `connect-testing` library pins pre-release client versions,
so it isn't used); the Health Connect screen (`feature/settings`); and the imported data on Home's
sleep strip, the check-in (her night editable, Health Connect's read-only), Cycle (outlined, never
estimated from), Activities and Stats. Never the GP summary.

Found on the emulator and fixed: "Manage access" crashed twice, first starting from the app
context without a new task, then with `ACTION_MANAGE_HEALTH_PERMISSIONS`, which Android refuses to
apps (it now opens Health Connect's home, whose App permissions the note's wording names).
Platform behaviour worth knowing: Health Connect asks for past data (history) on a second screen;
`revokeAllPermissions` is applied only after Keel's process ends; on a fresh emulator daily totals
read empty until Health Connect's own screens have been opened once (its source list). Wording:
18 sentences in `platform-substitutions.json` and one Android addition ("Health Connect isn't
available on this phone.") wait for the owner (D12); the Update offer's message can't name a new
read as iOS's does ("periods"), so it says "Keel can now read more from Health Connect."

Verified on the API 36 emulator (debug and R8 release) with a seeded week (`uitHealthSeed`,
`uitHealthProbe`): every value read back as seeded; onboarding Continue to Health Connect's dialog,
its privacy link to Keel's screen, Allow, the import; the screen (sync now, remove, the no-data
note, Manage access, Disconnect revoking); the launch offer and refusing; Home, check-in, Cycle,
Activities and Stats showing the imports. API 28: "update required", Continue opens the Play Store.
Not verified: a real phone with real sources (a wearable, two apps writing steps for the
de-duplication, Health Connect's own app-priority choices), installing Health Connect from Play on
Android 9 to 13 (needs a Google sign-in), the 30-day limit without history on a real install,
Health Connect's rationale and permission-usage entry on Android 14+ beyond the emulator, and
large real data volumes (a year of minute-level heart rate). Once, during the emulator run, the
Flights switch was found off after scroll gestures were sent while the screen was still loading;
the switch itself works, and the cause wasn't pinned down.

### M8 accessibility and large screens (24 Oct 2026)
Checked every screen (each route, onboarding, the GP steps, the check-in, symptom picker,
medicine picker and form, Cycle's log sheet) with `android/tools/a11y_audit.py`, which reads
the accessibility tree for unnamed buttons and targets under 48 dp, then at 200% text and as a
tablet in landscape. Fixed:
- **Taps passed through layers** (a real bug, both seen on the emulator and reproduced in
  tests): a tap on the check-in's empty strip below "Save entry" opened Cycle from Home
  underneath, and a tap on the floating header's title opened the row scrolled under it.
  Compose hands a tap to whatever under the finger handles taps; both layers now claim their
  taps (`blocksTouchesBelow`), and Home is hidden from TalkBack while the check-in is up.
- TalkBack names: each text field carries its label (Last name had none), the sleep stepper's
  minus and plus ("Decrease", "Increase", iOS's stepper words), the bars on Home's 7-day strips
  and Stats ("Last night, 7.5h"; nights with nothing aren't buttons, as iOS disables them).
- 48 dp targets: Home and Cycle calendar days (the iOS shape drawn inside a 48 dp target),
  Cycle's day strip, Home's medicine rows, symptom and medicine pills, the GP summary's chips.
  Days in a 7-column grid stay narrower than 48 dp on phones under about 430 dp wide, and the
  Stats bars stay 24 dp wide (a chart); both are named for TalkBack.
- 200% text: the Home greeting wraps instead of being cut to "Good"; the Health Connect
  "Connected" badge moves under the name instead of one letter a line; Stats' period control
  widens so "Months" isn't split; Cycle's day circles grow so dates stay inside.
- Reduce motion: the recording pulse stays still with "Remove animations" on (iOS Reduce
  Motion); one-off animations already follow the system's animation scale.
- Large screens: Android 16 ignores the portrait lock on tablets and unfolded phones, so Keel
  keeps a phone-width column (600 dp) in the middle there, as the iPhone-only iOS app does on
  an iPad. Phones stay portrait.

**Contrast, for the owner (not changed: the palette is shared with iOS).** Body text and
headings read at 8.9:1 or better on every surface in every theme, and a test keeps it so. Short
of WCAG's 4.5:1 for small text: muted text on off-white 3.9:1 (placeholders 3.6:1); amber
attention text 3.6:1. In dark mode every theme keeps its light-mode accent, because `resolve`
(iOS and Android alike) puts the theme's accent over the dark palette's lightened one, which is
then never used: rosewood text on the dark background is 2.4:1 (Slate 2.7, Forest 2.1), mist
blue 2.4:1. Dusk in light mode: accent text 2.3:1 and white on its buttons 2.5:1. Forest's
secondary on off-white 1.8:1, Slate's 2.5:1. A likely fix for both apps is to use the dark
palette's lightened accent family in dark mode, and darker muted and accent shades where text
is small; it's a brand decision.

Verified on the API 36 emulator (debug): the audit before and after; the check-in and header
taps; 200% text on Home, Cycle, More, Settings, Medications, GP summary, Health Connect,
Profile, Stats, Activities and three onboarding steps (Continue stays reachable by scrolling);
a simulated tablet (1600x2560 at 320 dpi, landscape) on Home, Cycle, More, onboarding and the
GP summary; on API 28, Home and Cycle after the changes. Not verified: TalkBack's actual speech
(adb can't drive it; labels checked in the tree and in tests), a real tablet or foldable, and
Switch Access. Once, while the Mac was at a load of about 17, the emulator showed "Keel isn't
responding" mid-scroll; the system Settings app was as slow then (6.4 s to open), and it didn't
recur once the load eased.

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

### M4 Looking back (21 Oct 2026)
Built: `MonthSummary` and `Insight` (`core/domain`); the `insight` table and
`InsightRepository.refreshDerived` (five-day minimum, rewritten in one transaction), run at
launch right after the symptom catalog, as iOS; `feature/insights` with Looking back (this
month's heading, days checked in, top five symptoms by days, three newest notes, "See all
notes", pattern cards, the GP Visit Summary link, a stand-in until M5) and Your notes. All copy
is iOS-verbatim. Tests: domain +7, data +3, app +3, insights +4. Verified on the API 36
emulator with seeded entries (and "See all notes" opening Your notes), on API 28 in dark
mode, and in the release (R8) build on API 36. Not verified: TalkBack.

### M4 Activities (21 Oct 2026)
Built: `ActivityPeriod`, `ActivityAggregation`, `ActivityCatalog`, `EatingCatalog`,
`EatingLog`, `VitalTrend` (`core/domain`); `ActivityRepository.setAmount` / `setEating`
(no answers a 0, clearing deletes the row, as iOS) and `keel.disabledHealthItems`
(`core/data`); `VitalLineChart` and nine metric icons (`core/designsystem`); `ActivitiesScreen`
and `ActivitiesModel` (`feature/insights`): Day / Week / Month, how she felt, the Health
Connect tiles with the "vs your usual" arrow, roll-ups with per-day bars, Your body lately,
Eating, water, and the "You logged" counts. Imported amounts and readings are empty until M7
(tiles say "No data yet"; her own sleep, water and eating work now). Reached by debug route
until More (M6); "Connect to Health Connect" opens a title-only stand-in. Copy: five Health
Connect substitutions ("wrist" becomes "skin" temperature, plan §3.2); a past week reads
"12 Oct to 18 Oct" where iOS writes an en dash, which Android's copy rules don't allow (the
"{} to {}" shape is already iOS copy). HRV reads `hrvRmssd` (D13). Tests: domain +16, data
+4, designsystem +3, insights +13, app +1. Verified on the API 36 emulator: day view with her
check-in and sleep, water and eating written as her rows on her local day (read back from the
device database), clearing an answer deletes its row, the week counts; the chart in the debug
gallery with sample points (no real readings before M7); API 28 dark month view. Not verified:
the vitals rows and arrows on real imported data (M7), TalkBack.

### M4 Stats (22 Oct 2026)
Built: `SymptomTally`, `StatsDayCounts`, `StatsPeriod` (`core/domain`); `StatsScreen` and its
pure `StatsModel` (`feature/insights`): the "You checked in on X of the last N days" line and
the four tiles (all counted in days), the mood distribution, symptoms by days with the
vasomotor rate (paged after ten), medicines "recorded as taken", tappable sleep bars, and Body
(vitals averages and ranges, empty until M7). Reached by debug route until More (M6). Copy:
three Health Connect substitutions; iOS's "58–70" range becomes "58 to 70" (no en dashes on
Android). Tests: domain +8, insights +9. Verified on the API 36 emulator against the device
database (8 check-in days from 16 entries, 6 with no symptoms, 4 medicine days, mood shares,
a tapped night's hours), on API 28 in dark mode (3 months), and the release build starts.
The debug week seed now replaces a night instead of adding a second row when run on a later
day (a seed artefact found here; the app's own save already updated the night).

### M4 summary
All four screens are ported on her own records with the iOS wording, rules and tests. Waiting
on later milestones: imported period days, activity amounts and vitals (M7, the screens
already show and label them); the More screen that leads to Activities and Stats, and Profile
and Health Connect behind title-only stand-ins (M6); the GP Visit Summary behind Looking back
(M5). For the owner: 13 Health Connect wordings and the range wording ("12 Oct to 18 Oct",
"58 to 70") to sign off; the dark-mode accent contrast on tinted chips (about 1.9:1, iOS
identical) for M8.

### M5 GP Visit Summary (22 Oct 2026)
Built: `GPSummaryBuilder`, `GPSummaryCopy`, `GPPeriod`/`GPDateWindow`, the document types and
`Hysterectomy` (`core/domain`); `GPSummaryService` and the profile's hysterectomy and
"periods not applicable" setters (`core/data`); `feature/gpsummary`: the four-step flow
(presets and custom range with date pickers that stop at today; review with removable
symptoms, treatments and sections; details with the spec's limits and the About me opt-ins
offered only for what she recorded; preview), Back steps back and leaves from the first step,
Home asks first once she has made a choice, everything she chose survives the screen being
recreated, and each step starts at its top. `GPSummaryPdfRenderer` is a line-for-line port of
the iOS renderer (A4, page 1 fit 6 → 5 → 4 rows, page 2 spills rather than clips, footer on
every page) on `PdfDocument`; the preview draws the very bytes that are shared
(`PdfRenderer`), and sharing goes through a `FileProvider` folder in the private cache
(cleared when she makes the next one). Debug: `uitGPStep`, and `uitGPSummary`, the iOS probe's
12-week seed rendered to `files/keel-gp-summary.pdf`.

Checked against iOS: the same seed on the iOS simulator (`-uitGPSummary`) and on Android
gives the same text, line for line (only the order PDFKit extracts table cells in differs),
and the same layout once Android counts the font's line gap on the last line as iOS does.
Found and fixed on the way: (1) Android snaps text to whole canvas units, which are PDF
points here, squashing word spacing in 9.5 and 12.5 pt text: the PDF uses linear, subpixel
text. (2) Android's PDF writer gives a ligature glyph no text, so "affecting" copied and read
aloud as "a ecting": ligatures are off in the PDF. (3) **Android 9's PDF canvas reports a
broken clip** (empty off the main thread, 116 points tall on it), and `Layout.draw` skips
lines outside it, so on Android 9 the PDF had rules and grids but no text: lines are now drawn
with `drawText` (regression test on a device, fails on API 28 before the fix). (4) Each step
reused the previous step's scroll position (Details opened below its priority fields).
Remaining quirk: PDFKit extracts "What" as "W" + "hat" from Android's PDF (it positions each
glyph); every letter is there.

Metadata: Android stamps its PDF library as Producer ("Skia/PDF m66" on Android 9); it's blanked to
spaces of the same length (as iOS), with Creator, Author, Title, Subject, Keywords and the
dates if present. Tests: domain +26, data +19, feature +18, device (androidTest) 6, all green
on API 28 and 36; mapping in `android/docs/test-parity.md`. Verified on emulators: the whole
flow on API 36 (custom range, removals, typing, chips, toggles, preview, share sheet with
`GP-Visit-Summary-2026-10-22.pdf`, and Print opening the shared PDF through the grant), API 28
in dark mode (review, preview, leave dialog, Leave → Home), and the R8 release build from
Home's "Prepare for a GP visit" to the share sheet. Not verified: real devices, TalkBack,
sharing into a real mail or messaging app, Health Connect data (none in the summary by design).

Not done: `fixtures/gpsummary` (§4.2) needs the iOS test-only `SharedFixtureTests`, which
waits on the owner; the simulator comparison above stands in for it. Owner decision: D26.

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
| D26 | Two iOS GP summary quirks, read from the iOS code (not run): a blank first name is stored as "there", which iOS offers ("Include my name") and prints as "Name: there"; and the review step lists symptoms logged only in the window before the period, at "0 of N check-in days", which never print | Android treats "there" as no name (as iOS's own Profile screen does) and lists only symptoms recorded in the period. Suggest the same iOS fixes (owner) |

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
