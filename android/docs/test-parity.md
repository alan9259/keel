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
