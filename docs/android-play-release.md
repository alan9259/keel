# Keel for Android: releasing on Google Play

Written 24 Oct 2026 (M8) for the owner. Everything here that describes Keel comes from the
Android code as built; anything that is a legal or business answer is marked **owner** and is
not filled in for you. Play's rules were checked on 24 Oct 2026 (sources at the end); check
them again before submitting, as they change.

## 1. Decisions before the first upload

| | What | Notes |
|---|---|---|
| **owner** | Developer account type | An organisation account (TRY Keel Pty Ltd) needs a D-U-N-S number. A *personal* account created after 13 Nov 2023 must run a closed test with at least 12 testers opted in for 14 days in a row before it can publish to production; organisation accounts are exempt. |
| **owner** | Application ID | `com.therecalibrationyears.keel`. Permanent once uploaded. |
| **owner** | Upload key | Create it yourself and keep it (and its passwords) outside the repo, backed up; see section 2. Enrol in Play App Signing so Google holds the key that signs for phones. |
| **owner** | Privacy policy | The web policy (`https://therecalibrationyears.com/keel-privacy`) must cover Android and Health Connect, including Health Connect's Limited Use requirements, and must be the same policy Keel shows from Health Connect's privacy link (Keel's "Your privacy" screen links to it). Blocks release (plan D3). |
| **owner** | Wording | The Android wording waiting in `android/core/copy/src/test/resources/platform-substitutions.json` and `android-additions.json` (`approvedByOwner: false`), and decisions D2, D9, D12, D15, D18, D26 in the plan. |
| **owner** | Contrast | The colour shortfalls listed in the plan ("M8 accessibility and large screens"), shared with iOS. |

## 2. Building the bundle

1. Create the upload key once (it asks for passwords; keep them in your password manager):
   ```sh
   mkdir -p ~/.keel
   keytool -genkeypair -v -keystore ~/.keel/keel-upload.jks -alias keel-upload \
     -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Describe it in `~/.keel/upload.properties` (outside the repo; or point the
   `KEEL_UPLOAD_PROPERTIES` environment variable at another file):
   ```
   storeFile=/Users/<you>/.keel/keel-upload.jks
   storePassword=...
   keyAlias=keel-upload
   keyPassword=...
   ```
3. Raise `versionCode` in `android/app/build.gradle.kts` (each upload needs a higher one;
   `versionName` is what people see), commit it, then from `android/`:
   ```sh
   ./gradlew check :app:bundleRelease
   ```
   The bundle is `android/app/build/outputs/bundle/release/app-release.aab`, with R8's mapping
   file inside it (Play uses it to read crash reports). Without the upload key the bundle task
   stops with a message rather than producing a debug-signed bundle.

Checked on the current build (24 Oct 2026): a bundle signed with a throwaway key builds (8.0 MB)
and verifies; the APK targets API 36 (Play's requirement for new apps and updates from 31 Aug
2026); its two native libraries (from AndroidX) are aligned for 16 KB memory pages, and the APK
passes `zipalign -c -P 16`.

## 3. App content answers, from the code

**Permissions the release build asks for:** Health Connect reads (listed below),
`RECORD_AUDIO` (voice notes, transcribed on the phone only), `POST_NOTIFICATIONS`,
`SCHEDULE_EXACT_ALARM` and `RECEIVE_BOOT_COMPLETED` (reminders), `USE_BIOMETRIC` (app lock).
**It has no `INTERNET` permission**: Keel itself can't send anything off the phone. Data leaves
only when she chooses to, through another app: sharing the GP summary PDF or a backup file, or
writing an email in her own mail app.

**Data safety** (**owner and legal** decide the answers; facts to answer from): no analytics,
advertising or crash-reporting libraries; no account or server; records are stored in the app's
private storage on the phone; Android's own cloud backup is turned off for Keel (plan D9); she
can delete everything in Settings, "Delete all my data", which also removes Keel's Health
Connect access.

**Health apps form** (App content): features to tick, **owner** to confirm: Period tracking,
Sleep, Activity and fitness, Medication (reminders and the medicines log). Keel reads only, never
writes. A justification for each data type Keel reads, with where she sees it:

| Health Connect data | Where Keel shows it |
|---|---|
| Steps, Exercise | Activities: daily, weekly and monthly totals ("Steps", "Exercise"). |
| Sleep | The check-in's "Sleep last night" (read-only), Home's 7-day sleep strip, Stats, Activities. |
| Active calories burned, Distance, Floors climbed | Activities tiles ("Active energy", "Distance", "Flights"). |
| Heart rate, Respiratory rate, Oxygen saturation | Activities tiles ("Heart rate", "Respiratory rate", "Blood oxygen"). |
| Resting heart rate, Heart rate variability, Weight | Stats ("Body") and Activities ("Your body lately"): trends over the month. |
| Blood pressure | Stats ("Body"): average over the month. |
| Body temperature, Skin temperature | Activities: the "Body temperature" tile, and "Overnight skin temperature" under "Your body lately". |
| Menstruation | Cycle: imported period days are outlined beside the ones she logs; they're never used for estimates or the GP summary. |
| Past data (history) | Activities, Stats and Cycle look back up to a year; without it Health Connect gives about 30 days. |

Each item can be switched off on Keel's Health Connect screen. Content rating, target audience
(adults), ads (none) and the store listing (title, descriptions, screenshots, the 512 px icon and
1024 x 500 feature graphic) are **owner** answers and artwork.

## 4. Internal testing, then production

1. Upload the bundle to the **Internal testing** track and add testers by email.
2. Install from Play on at least two real phones: Android 13 or earlier (Health Connect comes
   from Play there) and Android 14 or later, ideally one with a watch writing to Health Connect.
3. Run the checklist in section 5 on both and note anything that differs.
4. A personal account then needs the 14-day closed test (section 1) before production.

## 5. Run-through on real phones

What the emulators couldn't prove (see the plan's "not verified" notes):
- [ ] Install from Play; open; onboarding to Home; the Health step's Continue opens Health
      Connect (or installs it from Play on Android 13 or earlier).
- [ ] Health Connect with real data: connect, see steps and sleep arrive, switch an item off,
      Disconnect, reconnect; Health Connect's own privacy link opens Keel's privacy screen.
- [ ] A check-in with voice: speech is transcribed on the phone (or the "type instead" note shows).
- [ ] A medicine with reminders: the reminder arrives on time with the phone locked and idle,
      "Mark taken" works, a tap on the reminder marks it taken, and reminders survive a restart.
- [ ] App lock: PIN, fingerprint or face, "Forgot PIN" with the phone's screen lock; Keel's
      card is blank in the app switcher while the lock is on.
- [ ] GP summary: generate and share the PDF to email or Files; it opens and reads correctly.
- [ ] Backup: export, delete the app, reinstall, restore; and restore an iPhone backup.
- [ ] "Delete all my data" leaves a fresh Keel and removes Health Connect access.
- [ ] TalkBack on: move through Home, the check-in and Settings and listen for anything unnamed.
- [ ] Largest text and display size: nothing important is cut off.
- [ ] Dark mode and each theme.

## Sources (checked 24 Oct 2026)
- Play closed testing for new personal accounts: https://support.google.com/googleplay/android-developer/answer/14151465
- Target API level requirements: https://developer.android.com/google/play/requirements/target-sdk
- Declaring Health Connect access: https://developer.android.com/health-and-fitness/guides/health-connect/publish/declare-access
