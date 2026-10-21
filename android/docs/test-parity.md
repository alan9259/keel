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
| HysterectomyTests: backupKeepsHerBackgroundAndDetails, olderBackupsWithoutTheseFieldsStillLoad | | with backup (M6) |
| GPSummaryLeaveTests (HealthSyncBehaviourTests.swift) | feature/gpsummary `GPSummaryDraftTests.homeConfirmsBeforeDiscardingADraftPastTheFirstStep` | ported |
| GPSummaryPDFRendererTests (4) | feature/gpsummary androidTest `GPSummaryPdfRendererTests` | ported on a device (PdfDocument is native) (+ text drawn to the foot of the page off the main thread, the Android 9 regression) |
| (Android) GP summary flow | feature/gpsummary `GPSummaryDraftTests`, `GPSummaryScreenTests`, `GPSummaryMetadataTests` | new: the flow's rules, saved state, the review list (D26), each step starting at its top, metadata blanking on bytes |
