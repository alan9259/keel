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
| ProfileTests: ageAndBirthYearAreInverse, derivedAgeUsesStoredBirthYear | | with the Profile screen (M6) |
| ProfileTests: reassignOwnership* | | N/A: no account upgrade on Android (plan D21) |
| LookingBackTests: labelNeedsThreeEntries, mostlyIsTheMostFrequentLevelNotAnAverage, tieGoesToTheMostRecentlyPickedLevel | core/domain `EnergySummaryTests` | ported |
| LookingBackTests: the rest (MonthSummary) | | with Looking back (M4) |
| MedicationHomeLogTests: appearsWhenTrackedOrAutoLogged | core/data `HomeDataTests.appearsInHomeLogWhenTrackedOrAutoLogged` | ported |
| CheckInRepositoryTests: createWithSymptoms, createTrimsBlankNotesToNil, updateReconcilesSymptoms_addKeepRemove, deleteSoftDeletesAndTombstonesLinks | core/data `CheckInDataTests` | ported |
| CheckInRepositoryTests: recentIsNewestFirstAndLimited, trackingDayCountCountsDistinctDays | | with the screens that use them (M4) |
| SymptomRepositoryTests (6) | core/data `CheckInDataTests` | ported (+ rename/archive reconcile, archive/rename/move) |
| QuickChipRankingTests (5 pure + 2 repository) | core/domain `QuickChipRankingTests`, core/data `CheckInDataTests` | ported |
| AlcoholTests: newEntryHasNoAlcoholRecorded, zeroIsARealAnswer…, editingTheRestOfAnEntryKeepsItsAlcohol, theSettingIsOnByDefault… | core/data `CheckInDataTests` | ported |
| AlcoholTests: GP summary line and backup tests | | with GP summary (M5) and backup (M1 backup) |
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
| ReminderExplainerTests (3) | | with the reminders explanation (M3 reminders stage) |
