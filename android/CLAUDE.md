# Keel for Android

Native Kotlin + Jetpack Compose port of the iOS app, with feature parity and Health
Connect in place of Apple Health. **Read first, every session:** the repo-root
`CLAUDE.md`, `DESIGN_PRINCIPLES.md`, and the port plan `docs/android-port-plan.md`
(milestones M0 to M8, decisions D1 to D24). The iOS app is the reference
implementation: port behaviour, copy and tests from the iOS file the plan cites.

## Binding rules (same as iOS)

- **Copy:** Australian/New Zealand spelling, "hot flushes", no em or en dashes, nothing
  from the wince list. Every user-facing string is the iOS wording verbatim; an Android
  wording (for example "Health Connect" for "Apple Health") goes in
  `core/copy/src/test/resources/platform-substitutions.json` with `approvedByOwner: false`
  until Mischa signs it off. `CopyParityTest` and `CopyLintTest` enforce this.
- **Medical boundary:** support and inform, never diagnose or prescribe.
- **Colours** only from `LocalKeelTheme.current` (never `Color(0x...)` outside the design
  system palette files; `ThemeColourTests` scans for it). Amber for errors, never red.
- **No fake data.** Honest empty states. Any stand-in is tagged `// FAKE: <what> — remove
  before <condition>`; `src/debug` seeds and probes are exempt.
- **Before any permission prompt** (notifications, microphone, Health Connect) the screen
  has only "Continue": no Skip or Not now (App Review 5.1.1(iv), applied here too).
- **Every logic change ships with JUnit tests** in the same change. Port the matching
  XCTest and keep its name with a `Port of KeelTests/<File>.swift` comment.
- **Verify on an emulator** with screenshots before saying something works, and say
  plainly what was not verified (real-device-only: notification delivery in Doze,
  biometrics, on-device speech, Health Connect with real sources, Play install).
- **Bump `versionCode`** in `app/build.gradle.kts` before any build meant for a device or
  Play, and commit it.
- Commit or push only when asked.

## Toolchain (no Homebrew; all in user folders)

```sh
export JAVA_HOME=$(ls -d ~/Library/Developer/KeelAndroid/jdk-17*)/Contents/Home
export ANDROID_HOME=~/Library/Android/sdk
```

- JDK 17 (Temurin), Gradle via `./gradlew` (9.8.2), AGP 9.4.1 (built-in Kotlin 2.4.21).
- compileSdk 37 (Compose BOM 2026.09.00 requires it), targetSdk 36, minSdk 28.
- Emulators: `keel_api36` (Pixel 8, Play) and `keel_api28` (Pixel 3, Play). Start with
  `$ANDROID_HOME/emulator/emulator -avd keel_api36 -gpu host -no-snapshot-save -no-audio &`.
  **Always `-gpu host`:** without it the API 36 image can fall back to SwiftShader (software
  rendering; check `adb shell dumpsys SurfaceFlinger | grep GLES`), which made Home take 10 s
  to open with its emoji vectors (0.6 s on a GPU). Don't time anything on SwiftShader, or
  right after boot, or while the Mac is heavily loaded (`uptime`).

## Commands (run from `android/`)

```sh
./gradlew check                 # all unit tests (debug + release) and Android Lint
./gradlew testDebugUnitTest     # unit tests only
./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease  # R8 + resource shrinking, debug-signed locally
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb exec-out screencap -p > shot.png
```

## Modules

- `core/domain` (JVM): pure rules and value types (`Pathway`, `RootRouting`, `KeelClock`,
  `SyncStatus`), ported with their tests.
- `core/data` (Android library): Room `MainDatabase` (`user_profile` so far),
  repositories (`UserRepository`), `SettingsStore` and `LocalIdentity` on one DataStore
  file, `DeviceContext`. Same keys, raw values and rules as iOS.
- `core/copy` (JVM): the copy tests, which read the iOS Swift sources and all shipping
  Kotlin directly (no snapshot to go stale). Copy lives where iOS keeps it: inline in the
  screen, or in a copy object where iOS has one (`GPSummaryCopy` etc.).
- `core/designsystem` (Android library): `KeelTheme` tokens + `ThemeCatalog`, OKLab
  colour mix, fonts (copied at build time from `Keel/Resources/Fonts`), metrics, the
  components, `KeelMark`, and the Twemoji vectors.
- `feature/onboarding`: the six onboarding screens behind an `OnboardingActions`
  interface (the module never touches storage).
- `feature/home`: Home (`HomeScreen`) and its pure rules (`HomeModel`, unit-tested), fed a
  `HomeData` snapshot by the app; actions go back through `HomeActions`.
- `feature/checkin`: the mood slide (`EntrySheetContent`), the entry screen (`CheckInScreen`),
  the "more" picker (`SymptomPickerSheet`) and voice button, behind `CheckInActions`.
- `feature/medications`: Medications screen, catalog picker and details form, with their
  pure rules (`TreatmentForm`, `TreatmentPicker`), behind `MedicationsActions`.
- `feature/settings`: settings screens (so far Reminders) and `rememberNotificationAccess`,
  the notification permission gate shared with Home's "Gentle reminders".
- `core/platform`: device services: `SpeechRecognitionService` (on-device only),
  `AlarmReminderScheduler` (alarms plus a device-only registry of armed ids, since Android
  can't list pending alarms), `ReminderChannels`, the reminder status icon.
- `app`: `AppContainer` (manual DI, built once in `KeelApplication`), `KeelAppController`
  (port of iOS `AppEnvironment`, same method names), `KeelRoot` (onboarding vs app),
  `MainActivity` (splash held until her saved state loads), `src/debug` harness and gallery,
  `src/release` no-op harness. `KeelReminders` lays out and answers reminders (port of the
  iOS reminder methods); the receivers (`ReminderReceivers.kt`) post them, handle "Mark taken"
  / "Always mark taken", and re-arm after a reboot, clock or zone change, or an update.
- Later: more `feature/*` modules (see the plan, §1.1).

## Debug harness (`app/src/debug`, never in release)

Intent extras, the counterpart of the iOS `-uit*` launch arguments:

```sh
adb shell am start -n com.therecalibrationyears.keel/.MainActivity \
  --es uitScreen gallery --es uitTheme slate --ez uitDark true --ez uitScrollToBottom true
```

`uitTheme`: earthy, slate, forest, dusk, midnight. `uitDark`: true/false (omit for the
system setting). `uitScreen gallery` shows every design-system component.
`--ez uitOnboarded true` skips onboarding; `--ez uitForceOnboarding true` shows it even
when onboarded; `--es uitOnboardStep pathway` starts at a step (welcome, right_place,
create_account, pathway, health, ready). For a truly fresh start:
`adb shell pm clear com.therecalibrationyears.keel`.

## Things that are easy to get wrong

- **Copy parity:** a literal that looks like copy (has a space or starts with a capital
  word) must match an iOS string exactly; keep iOS's splits (don't join two iOS strings
  into one) and don't write copy as a raw `"""` string. A literal that isn't user-facing
  (a log tag, an exception message) goes on a line marked `// not-copy: <reason>`.
- **Emoji:** Android 9 to 12 draw the Twemoji colour font blank, so emoji come from
  generated vector drawables. After changing a mood pack on iOS, re-run
  `python3 tools/twemoji_to_vectors.py` (needs `pip install fonttools`);
  `TwemojiVectorsTest` fails until you do.
- **System bars** follow Keel's colour mode, not the system's (`SystemBarsFollowKeel` in
  `MainActivity`); edge-to-edge is always on, so screens pad with `WindowInsets.safeDrawing`
  (`KeelScreen` does this).
- **Robolectric** tests use `@Config(sdk = [34])`: SDK 35+ sandboxes need Java 21. Compose UI
  tests also need a phone-sized screen (`qualifiers = "w412dp-h915dp-xxhdpi"`); the default
  320x470dp screen leaves buttons off-screen and clicks silently miss.
- **Room** schema JSON lives in `core/data/schemas` and is committed. Version 1 stays editable
  until the first Play upload (reinstall the debug app after changing it); after that every
  change is a `Migration`, never a destructive reset (plan D6).
- **Emulator typing:** Gboard on the API 36 image treats `adb` taps as a stylus and opens a
  "Try out your stylus" sheet that swallows typed text. Turn it off once per emulator:
  `adb shell settings put secure stylus_handwriting_enabled 0`.
- **Stand-ins:** `grep -rn "// FAKE:" android/` lists them (currently: the title-only feature
  screens in `MainShell.kt`, and the Health Connect request in `KeelAppController.kt`).
- **Drag and drop from a bottom sheet:** start it (`View.startDragAndDrop`) with the view from
  *inside* the sheet's content (the sheet is its own window; the activity's view fails with
  "from window is not being touched"), and from the chip's own long press (Compose's default
  drag start never sees a touch the chip's tap handling has claimed). See `SymptomDrag.kt`.
- **Robolectric** never goes idle with a dialog over an open `ModalBottomSheet`; test that
  combination on the emulator instead.
- **uiautomator on API 28** can return a stale tree for Compose overlays after scrolling; drive
  those screens by coordinates from screenshots, or test on API 36.
- **Debug seeds:** `--ez uitSeedMed true --ez uitSeedWeek true --ez uitSeedCheckIn true`
  (and `uitReset`, `uitSeedEnergyMix`) fill Home for screenshots; `--es uitRoute more` opens a
  screen. They live in `app/src/debug` and never ship.
- **Copy for new wording:** a user-facing string with no iOS original (e.g. a TalkBack label
  iOS lacks) goes in `core/copy/src/test/resources/android-additions.json` for owner sign-off.
- **`MainActivity` is `singleTop`** (so a reminder tap reaches the open screen). A debug
  `am start` to a running app goes to `onNewIntent` and skips the harness: use `am start -S`.
- **Reminders on an emulator:** `--ez uitReminderExplainer true` queues "Gentle reminders";
  `--ez uitRemindSoon true` adds a medicine due in 2 minutes and the check-in in 3. Check
  armed alarms with `adb shell dumpsys alarm | grep -A2 "com.therecalibrationyears.keel}"`,
  posted ones with `dumpsys notification --noredact`. Doze: `dumpsys battery unplug` +
  `dumpsys deviceidle force-idle` (undo with `unforce` and `battery reset`). After `adb reboot`
  the boot broadcast can take a minute or more to reach `RescheduleReceiver`.
- **Exact alarms:** Android 13+ doesn't grant them by default, and Keel never asks (plan
  §1.7), so reminders are inexact there: Android may deliver one up to an hour after its time
  (`window=+1h` in `dumpsys alarm`). Android 9 to 12 deliver on time. Owner decision pending.
- **Dates on screen** use `HomeFormats`-style skeletons (`DateFormat.getBestDateTimePattern`)
  so they follow her locale like iOS `.dateTime` formats; one-decimal numbers use
  `formatOneDecimal` (iOS `%.1f` rounds ties to even).
- Tests that read files outside their module (iOS sources, `brand/`) declare them as Gradle
  inputs so a change there re-runs them.
- The `applicationId` `com.therecalibrationyears.keel` is permanent once uploaded to Play;
  confirm with the owner before the first upload.
