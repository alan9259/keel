# iOS → Android test parity

Every iOS test (`KeelTests/*.swift`) maps to its Kotlin port or says why it has none. Kotlin
test classes keep the iOS name. Suites not listed yet are ported with their milestone
(plan §4.1).

| iOS test | Android | Status |
|---|---|---|
| ThemeColourTests | core/designsystem `ThemeColourTests` | ported (M0) |
| KeelMarkTests: markMatchesTheV2BrandArt, markColoursAtKnownPoints | core/designsystem `KeelMarkTests` | ported (M0) |
| KeelMarkTests: launchMarkAssetIsV2 | `KeelMarkTests.launcherIconIsV2` | adapted: Android has no LaunchMark asset; checks the adaptive icon |
| RootRoutingTests (7) | core/domain `RootRoutingTests` | ported |
| OnboardingStateTests: finishingOnboardingPersistsAcrossLaunches, aProfileAloneIsNotOnboarded | app `KeelAppControllerTests` | ported |
| OnboardingStateTests: markingAgainKeepsTheFirstDate, markingWithNoProfileCreatesOne | core/data `UserRepositoryTests` | ported |
| OnboardingStateTests: emptyDatabaseIsNotOnboardedEvenWithTheLegacyFlag, upgradingUserWithAProfileStaysOnboarded, profileWithoutTheLegacyFlagIsNotCarriedOver | `KeelAppControllerTests.aNewInstallIsNotOnboarded` | N/A: Android never had the Keychain/UserDefaults flag; only the fresh-install case applies |
| OnboardingStateTests: deleteAllMyDataReturnsToOnboarding | | with "Delete all my data" (M6) |
| OnboardingStateTests: restoreKeepsThisPhonesOnboardedRecord, restoreOfAnArchiveWithoutAProfileStaysOnboarded | | with backup and restore (M1 backup / M6 screen) |
| ProfileTests: updateBasicInfoRoundTripsAndClears, updateBasicInfoCreatesAProfileIfNoneExists | core/data `UserRepositoryTests` | ported |
| ProfileTests: ageAndBirthYearAreInverse, derivedAgeUsesStoredBirthYear | core/domain `ProfileFormTests` | ported on the pure form (+ placeholder name loads blank, trimming, implausible ages, background year) |
| ProfileTests: reassignOwnership* | | N/A: no account upgrade on Android (plan D21) |
| LookingBackTests: labelNeedsThreeEntries, mostlyIsTheMostFrequentLevelNotAnAverage, tieGoesToTheMostRecentlyPickedLevel | core/domain `EnergySummaryTests` | ported |
| LookingBackTests: countsDaysSymptomsAndNotesForThisMonthOnly, emptyMonthHasHonestWording, oneDayIsSingular, topSymptomsCappedAtFive, monthBoundaryFirstAndLastDay | core/domain `LookingBackTests` | ported (+ the month follows her zone) |
| LookingBackTests: otherPhoneCalendarsStillMeanTheGregorianMonth | `LookingBackTests.theHeadingIsTheEnglishGregorianMonth` | adapted: `java.time` is always Gregorian; checks the heading stays English under a Hebrew-calendar locale |
| LookingBackTests: currentReadsThisMonthFromTheStore | app `KeelAppControllerTests.lookingBackReadsThisMonthsLiveCheckIns` | ported (+ symptoms by name, "See all notes" covers every month) |
| LookingBackTests: generatedTextIsDeletedAndNotRestored | | N/A: Android never stored generated text; the backup half comes with backup (M1 backup) |
| MedicationHomeLogTests: appearsWhenTrackedOrAutoLogged | core/data `HomeDataTests.appearsInHomeLogWhenTrackedOrAutoLogged` | ported |
| CheckInRepositoryTests: createWithSymptoms, createTrimsBlankNotesToNil, updateReconcilesSymptoms_addKeepRemove, deleteSoftDeletesAndTombstonesLinks | core/data `CheckInDataTests` | ported |
| CheckInRepositoryTests: recentIsNewestFirstAndLimited, trackingDayCountCountsDistinctDays | | with the screens that use them (M4) |
| SymptomRepositoryTests (6) | core/data `CheckInDataTests` | ported (+ rename/archive reconcile, archive/rename/move) |
| QuickChipRankingTests (5 pure + 2 repository) | core/domain `QuickChipRankingTests`, core/data `CheckInDataTests` | ported |
| AlcoholTests: newEntryHasNoAlcoholRecorded, zeroIsARealAnswer…, editingTheRestOfAnEntryKeepsItsAlcohol, theSettingIsOnByDefault… | core/data `CheckInDataTests` | ported |
| AlcoholTests: lineFormatMatchesTheSymptomRows | core/domain `GPAlcoholLineTests` | ported (M5) |
| AlcoholTests: onlyDaysWithASavedNumberCountAndBlankIsNeverZero, noLineWhenSheNeverRecordedAlcohol, sheCanLeaveTheAlcoholRowOff | core/data `AlcoholSummaryTests` | ported (M5) |
| AlcoholTests: backupRoundTripsAlcoholAndOldBackupsStillLoad | | with backup (M6) |
| SensitiveGroupCopyTests: intimacyIntroWording | core/domain `SymptomCatalogTests` | ported |
| SpeechOnDeviceTests (3) | core/platform `SpeechOnDeviceTests` | ported (+ Android 12/13 rules, language match) |
| DoseScheduleTests (8) | core/domain `DoseScheduleTests` | ported (+ no-active-days, no-anchor, nextDue edges) |
| DoseScheduleSummaryTests (8) | core/domain `DoseScheduleSummaryTests` | ported; fixed test words also pin the whole sentence (narrower dose clause, week order, cycle times) |
| DoseUnitTests (3) | core/domain `DoseUnitTests` | ported (+ "%" and decimals) |
| NotificationGateTests (5) | core/domain `NotificationGateTests` | adapted: Android has no provisional/ephemeral; covers never asked, refused once (system still asks), refused twice (Settings), allowed, and turned off below Android 13 |
| ReminderWordingTests: medicineNameHiddenByDefault, medicineNameShownWhenSheChoosesIt, bodiesAreHonestAboutLogging, everydayRemindersMakeNoHealthClaims | core/domain `ReminderWordingTests` | ported |
| ReminderWordingTests: theSettingIsOffByDefault | core/data `ReminderSettingsTests.medicineNamesInRemindersIsOffByDefault` | ported |
| MedReminderPlanTests (11) | core/domain `MedReminderPlanTests` | ported (+ Sydney clocks-forward day) |
| MedicationScheduleTests (6) | core/domain `MedicationScheduleTests` | ported against the stored columns (+ iOS JSON shape, tolerant reads, days moved onto the dose, rebuilt from older columns) |
| MedicationNameCleanupTests: stripsOnlyATrailingExample | core/domain `MedicationNameCleanupTests` | ported |
| MedicationNameCleanupTests: cleanupRenamesSavedEntriesAndIsIdempotent | core/data `MedicationRepositoryTests` | ported |
| MedicationRepositoryTests (11) | core/data `MedicationRepositoryTests` | ported with a fixed clock (+ upsert by name, stored text, start date kept, archive, legacy migration, form read-back) |
| TreatmentCatalogTests (5) | core/data `TreatmentCatalogTests` | ported (+ bundled copy is the iOS file, cached copy wins, alphabetical order) |
| ReminderExplainerTests: finishingOnboardingQueuesTheExplanationNotThePrompt | app `KeelAppControllerTests` | ported |
| ReminderExplainerTests: notNowTurnsRemindersOffWithoutAsking, continueLeavesRemindersOn | app `KeelRemindersTests` | ported (the explanation has only Continue; "not now" stays tested as the record step) |
| (Android) reminder scheduling, ids, everyday plans | core/domain `ReminderRulesTests`, core/platform `ReminderSchedulerTests`, app `KeelRemindersTests`, `ReminderPosterTests`, feature/settings `RemindersScreenTests` | new: alarm service, registry, receivers and channels have no iOS counterpart |
| (Android) exact alarms (D25) | core/domain `ReminderRulesTests`, app `KeelRemindersTests`, `KeelAppControllerTests`, feature/settings `ExactTimingTests` | new: iOS delivers on time, so has no counterpart |
| CycleStatsTests (11) | core/domain `CycleStatsTests` | ported on `LocalDate`s (+ phase boundaries on one start, spotting and period-end never start a cycle, iOS raw values) |
| CycleTimelineTests (3) | core/domain `CycleTimelineTests` | ported |
| CycleRepositoryTests (7) | core/data `CycleRepositoryTests` | ported with a fixed clock |
| CycleRepositoryFlowTests: setFlowUpsertsAndClears, togglePeriodDayLogsUnspecified, statsFromLoggedStarts, spottingDayDoesNotReanchorThePhase, catchingUpTheLatestPeriodReanchorsThePhase | core/data `CycleRepositoryTests` | ported (+ one entry per day, days follow her zone) |
| CycleRepositoryFlowTests: healthFlowImportsApartFromHerCycleEntries | | with Health Connect (M7) |
| AppleHealthPeriodsTests: herOwnEntryWinsAndTheDayIsShownOnce | core/domain `CycleTimelineTests.herOwnEntryWinsAndTheDayIsShownOnce` | ported |
| AppleHealthPeriodsTests: the rest | | with Health Connect (M7) |
| PatternEngineTests (4) | core/domain `PatternEngineTests` | ported on the pure engine (the store-building tests feed it cycle days directly) (+ 120-day window) |
| (Android) Cycle screen | feature/cycle `CycleModelTests`, `CycleScreenTests` | new: the iOS view's derived values are untested there |
| (Android) Looking back cards | core/data `InsightRepositoryTests` | new: iOS has no `InsightRepository` test (five-day minimum, rewritten each launch, spotting store path) |
| (Android) Looking back and Your notes | feature/insights `LookingBackScreenTests` | new |
| ActivityAggregationTests (10) | core/domain `ActivityAggregationTests` | ported on `LocalDate`s with Monday first (+ Sunday-first week, month back from the 31st) |
| VitalTrendTests (4) | core/domain `VitalTrendTests` | ported (+ a .5 average rounds away from zero, as Swift) |
| VitalLineChartTests (3) | core/designsystem `VitalLineChartTests` | ported on the pure curve builder (`VitalChartPath`) |
| EatingLogTests (2) | core/data `EatingLogTests` (+ core/domain `EatingStateTests`) | ported on the activity repository (+ water counting, the Health Connect items setting) |
| (Android) Activities screen | feature/insights `ActivitiesModelTests`, `ActivitiesScreenTests`, app `KeelAppControllerTests.activitiesWritesComeBackLiveAndNothingIsImportedYet` | new: the iOS view's rules (shown tiles, trend arrow, counts, labels, number format) are untested there |
| SymptomTallyTests (4) | core/domain `SymptomTallyTests` | ported (+ blank names ignored) |
| StatsDayCountsTests (3) | core/domain `StatsDayCountsTests` | ported |
| (Android) Stats screen | feature/insights `StatsModelTests`, `StatsScreenTests` | new: the iOS view's numbers and sentences are untested there |
| GPSummaryTests (18) | core/domain `GPSummaryTests` | ported on `LocalDate`s and `Instant`s |
| (Android) GP period windows | core/domain `GPPeriodTests` | new: the preset and custom windows iOS covers only through the service |
| GPSummaryServiceTests: resolvesCountsSymptomsCycleAndMeds, autoSectionsIncludedByDefaultAndRemovable, stoppedTreatmentsShowInMHTAndChangesButNotOtherTables, periodsNotApplicableReasonFlowsIntoCycleBlock, energyAverageSkipsCheckInsWithoutAnEnergyPick, stopOutsideWindowIsNotShown, nameAndAgeOffByDefaultAndPrioritiesDropSymptomRow, emptyStoreGivesHonestEmptyStates | core/data `GPSummaryServiceTests` | ported (+ the exact last period start and period label, which iOS left loose because its cycle repository used the machine calendar) |
| GPSummaryServiceTests: pdfFileNameIsDateBasedNotHex | feature/gpsummary `GPSummaryMetadataTests.fileNameIsTheDateSheMadeIt`, androidTest `GPSummaryPdfRendererTests.fileNameIsTheDateSheMadeIt` | ported (exact name) |
| GPSummaryServiceTests: importedAppleHealthPeriodsAreNotInTheSummary | | with Health Connect (M7): Android has no imported periods yet |
| (Android) GP summary service | core/data `GPSummaryServiceTests`: ageAndOptInsComeFromHerProfile, thePlaceholderNameIsNeverOfferedOrPrinted (D26), thePreviousColumnCountsTheWindowJustBefore, aScheduledMedicineReadsAsItsScheduleInHerWords | new |
| HysterectomyTests: theSixOptionsMatchTheSpec, summaryLineMatchesHerAnswer, yearMustBePlausible | core/domain `HysterectomyTests` (+ raw values match iOS) | ported |
| HysterectomyTests: recordsTheAnswerAndKeepsAYearOnlyForYes, offByDefaultOnTheSummary, includedOnlyWhenSheTurnsItOn, ratherNotSayIsNeverOfferedOrPrinted | core/data `HysterectomyTests` | ported |
| HysterectomyTests: backupKeepsHerBackgroundAndDetails, olderBackupsWithoutTheseFieldsStillLoad | core/data `HysterectomyBackupTests` | ported |
| GPSummaryLeaveTests (HealthSyncBehaviourTests.swift) | feature/gpsummary `GPSummaryDraftTests.homeConfirmsBeforeDiscardingADraftPastTheFirstStep` | ported |
| GPSummaryPDFRendererTests (4) | feature/gpsummary androidTest `GPSummaryPdfRendererTests` | ported on a device (PdfDocument is native) (+ text drawn to the foot of the page off the main thread, the Android 9 regression) |
| (Android) GP summary flow | feature/gpsummary `GPSummaryDraftTests`, `GPSummaryScreenTests`, `GPSummaryMetadataTests` | new: the flow's rules, saved state, the review list (D26), each step starting at its top, metadata blanking on bytes |
| FeedbackMailTests (6) | core/domain `FeedbackMailTests` | ported (+ non-ASCII encoded as UTF-8, the stricter form of the iOS rule) |
| ShareInviteTests (3) | core/domain `ShareInviteTests` | ported; the link is the Google Play listing (plan D24) |
| EraseAllDataTests: deletesEverythingAndResetsTheRest | app `KeelAppControllerTests.deletesEverythingAndResetsTheRest` | ported (+ every row counted, soft-deleted ones included; posted notifications and the shared GP summary cleared); the app lock and imported health data join it when they exist on Android |
| (Android) EraseAllData run repeatedly | app `KeelAppControllerTests.deletingTwentyTimesLeavesNothingBehind` | new: iOS ran its test repeatedly by hand after a profile survived |
| (Android) More, Settings, Themes, Colour mode, Mood icons | feature/settings `SettingsScreensTests` | new: the iOS views are untested there (rows, mail draft and no-mail fallback, share, toggles, typed DELETE) |
| CrisisResourcesTests (7) | core/domain `CrisisResourcesTests` | ported (+ the footer wording and the digits dialled) |
| PrivacySummaryTests: fullPolicyLinkIsTheCompanySite | core/domain `CrisisResourcesTests.thePolicyLinkIsTheCompanySite` | ported |
| PrivacySummaryTests: the rest | | with the owner's Android wording (plan D3); the screen is tested on sample text in feature/settings `InfoScreensTests` |
| AboutCopyTests (4), SocialLinkTests (1) | core/domain `AboutCopyTests` | ported |
| (Android) Profile, About, Connect, Get support, Your privacy | feature/settings `InfoScreensTests`, app `KeelAppControllerTests.profileSavesHerDetailsAndBackgroundTogether`, core/designsystem `SocialGlyphTests` | new: the iOS views are untested there (dialling, links, saving her background, the confirmation's two seconds, table rows read as sentences, glyphs single-sourced from the iOS assets) |
| BackupRoundTripTests (3) | core/data `BackupRoundTripTests` | ported (+ a lossless round trip field for field, onboarding kept, a newer format refused with the iOS message, non-backups turned away, dangling links skipped) |
| (Android) Backup file format | core/data `BackupFormatTests` | new: whole-second UTC dates, every top-level key iOS requires, empty optionals left out, sorted keys, whole numbers without a fraction, the D1 medicine extras |
| (Android) Shared backup fixtures | core/data `IosBackupFixtureTests` | new: restores `fixtures/backup/v1-ios-sample.keelbackup` (written by Swift's `JSONEncoder` from the iOS structs, `android/tools/ios-backup`) with its legacy shapes, and checks the committed `v1-android-export.keelbackup` is what Android writes; the iOS side (`SharedFixtureTests`) waits on the owner |
| (Android) Restore in the app, Backup & Restore screen | app `KeelAppControllerTests.restoringAnIphoneBackupKeepsHerHereAndReArmsReminders`, `somethingElseIsNotRestored`; feature/settings `BackupRestoreScreenTests` | new: the iOS view is untested there (both file pickers, confirm, cancel, a non-backup, a newer format) |
| PINHasherTests (6) | core/domain `PINHasherTests` | ported (+ the same construction as iOS: SHA-256 over salt + PIN, then each digest) |
| AppLockServiceTests: shouldLockOnlyAfterGraceElapses | core/domain `AppLockRulesTests` | ported on the pure rules (+ the lockout on every fifth failure, seconds rounded up, only ASCII digits) |
| AppLockServiceTests: the rest (19) | core/data `AppLockServiceTests` | ported with an in-memory PIN store and a fake authenticator (+ a lockout survives a relaunch, nothing locks while off, Android's prompt choosing the PIN) |
| AppLockServiceTests: biometryLabels | | N/A: Android names no particular biometric (plan D15) |
| EraseAllDataTests: the app lock part | app `KeelAppControllerTests.deletesEverythingAndResetsTheRest` | ported (lock off, PIN gone, off after a relaunch) |
| (Android) Keystore PIN store | core/data androidTest `KeystorePINStoreTests` | new: round trip, nothing in the clear on disk, the key removed with the PIN, a blob without its key reads as no PIN (checked on API 28 and 36) |
| (Android) App lock, PIN setup, lock screen | feature/settings `AppLockScreensTests` | new: the iOS views are untested there (turning it on twice, a mismatch, the biometric toggle, the right and wrong PIN, the lockout, biometrics first, the PIN from Android's prompt, both forgot-PIN paths) |
| HealthSleepMergeTests (8) | core/domain `HealthSleepMergeTests` | ported |
| (Android) Sleep sessions to nights | core/domain `HealthSleepSessionTests` | new: Health Connect sessions and stages (no stages, asleep stages, in-bed fallback, D8 session end day in her zone) |
| AppleHealthPeriodsTests: flowMappingDropsNoneAndKeepsTheHeaviestPerDay, snapshotWithOnlyFlowIsNotEmpty, switchingPeriodsOffStopsImport | core/domain `HealthFlowTests` | ported (+ period records fill days without flow, D5; the read window without history) |
| AppleHealthPeriodsTests: import, deleted days, window, estimate, removal | core/data `HealthConnectPeriodsTests` | ported on the ingestor (the Delete-all part is in app `KeelAppControllerTests`) |
| HealthSyncCatalogTests (7) | core/domain `HealthSyncCatalogTests`, core/data `HealthImportTests.aSwitchedOffItemIsNotImportedButOthersAre` | ported |
| HealthSyncGatingTests: the throttle (7) | core/domain `HealthSyncRulesTests` | ported |
| HealthAccessOfferTests: offerDecision | core/domain `HealthSyncRulesTests.offerDecision` | ported (+ Android's asked-permissions status) |
| HealthSleepSourceTests (2), HealthKitImportIntegrationTests (3) | core/data `HealthImportTests` | ported (+ revised values, zero amounts) |
| HealthImportPurgeTests (2), SubmissionPackATests.previouslyImportedUnusedTypesArePurged | core/data `HealthImportPurgeTests` | ported |
| SubmissionPackATests: readsOnlyTheTypesKeelShows, syncControlsOnlyCoverReadTypes | core/platform `HealthConnectManifestTests` | ported (HRV is `hrvRmssd`, D13; + the manifest declares exactly the reads, no write or background access, and finds Health Connect on Android 9 to 13) |
| (Android) Health Connect stages and flow levels | core/platform `HealthConnectMappingTests` | new: which stages count as asleep or in bed, unknown flow is a period day with no level |
| HealthSyncBehaviourTests (7) | app `HealthSyncBehaviourTests` | ported against a fake Health Connect (`HealthTestbed.kt`) |
| HealthAccessOfferTests: the rest (11) | app `HealthAccessOfferTests` (+ `KeelAppControllerTests.healthOfferWaitsForOnboarding`) | ported; "asked" is Keel's own record of what it asked Health Connect (plan §3.6) |
| HealthSyncGatingTests: setHealthKitAuthorizedPersists, setHealthKitAuthorizedCreatesProfileWhenMissing | core/data `UserRepositoryTests.setHealthConnectedPersists`, `setHealthConnectedCreatesAProfileRatherThanDropIt` | ported |
| HealthSyncGatingTests: connectAppleHealthPersistsTheFlag | app `HealthConnectFlowTests.connectPersistsTheFlagAndImports` | ported (a real grant, not the Simulator's assumed one) |
| (Android) Connecting to Health Connect | app `HealthConnectFlowTests` | new: what's asked, connected when anything is allowed (D4), refused counts as asked, the Play Store on Android 9 to 13, no screen to ask from, retries after an empty first read, the periods window with and without history, Disconnect revokes (D4), item switches, removing imports |
| (Android) Opening Health Connect | core/platform `HealthConnectPermissionsTests` | new: regressions for two "Manage access" crashes (no new task from the app; the system-only per-app page), the Play Store link, no request without an activity |
| EraseAllDataTests: Health Connect | app `KeelAppControllerTests.deletesEverythingAndResetsTheRest` | Android only: access revoked and the asked set cleared (D4) |
