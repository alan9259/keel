# Keel

*Perimenopause, made clearer.* An iOS app for noticing patterns across mood, energy, symptoms, cycle, and medications — built from the Figma design in `KEEL_DESIGN_SPEC.md`.

Native **SwiftUI**, iOS 17+. Data lives in **CloudKit** today, behind an abstraction designed so migrating to **Supabase** later is a drop-in swap.

---

## Stack

| Concern | Choice |
|---|---|
| UI | SwiftUI + `@Observable` (Observation) |
| Local store | SwiftData (source of truth) |
| Cloud sync (now) | CloudKit private DB, via `CloudKitSyncProvider` |
| Cloud (later) | Supabase, via a future `SupabaseSyncProvider` (same protocol) |
| Auth | Sign in with Apple (`AuthenticationServices`) |
| Health | HealthKit |
| Voice notes | Speech (`SFSpeechRecognizer`) + AVFoundation |
| Reminders | UserNotifications |
| Project format | Hand-written `.xcodeproj` using Xcode 16+ synchronized folder groups |

No third-party dependencies — everything is a system framework, so the project builds offline with zero package resolution.

## Architecture — why the backend is swappable

```
SwiftUI Views
   │  observe
@Observable stores / AppEnvironment        ← no CloudKit/Supabase types above this line
   │  call
Repository protocols  (UserRepository, CheckInRepository, SymptomRepository, …)
   ├── Local:  SwiftData  (source of truth)
   └── Remote: SyncEngine → SyncProvider (protocol)
                              ├── CloudKitSyncProvider   (now)
                              └── SupabaseSyncProvider   (later — one new file + swap)
```

Every model conforms to `Syncable` (`id: UUID`, `ownerID`, `createdAt/updatedAt`, `deletedAt` tombstone, `syncStatus`) and maps 1:1 to a `RemoteRecord` DTO — the intersection of what a `CKRecord` and a Postgres row can both hold. The `SyncEngine` only ever talks to the `SyncProvider` protocol, so nothing above it knows which backend is live.

### Data model

`profiles`, `check_ins`, `symptoms`, `check_in_symptoms` (join), `cycle_entries`, `medications`, `medication_logs`, `insights`, `daily_summaries`.

`insights` and `daily_summaries` are **derived locally** from her own data on each launch (never uploaded; `syncStatus == .synced`). They still round-trip through the backup archive so a restore keeps the historical record.

Symptoms are a **catalog**: `Symptom` is a reusable record; a `CheckIn` links to symptoms **many-to-many** via `CheckInSymptom`. A check-in can be saved with zero symptoms.

Treatments and supplements share the `Medication` entity, split by `kind` (treatment or supplement) and carrying strength, method, schedule, date added, dose-change date, and a note.

Schedules are a `DoseSchedule` value type (`Keel/Models/DoseSchedule.swift`), stored on `Medication` as parts so they map onto a plain row. Three shapes: a **weekly** pattern (a set of weekdays, all seven being "every day"), a **cycle** (total length plus a trailing pause, anchored to a day 1, which is how cyclic HRT is written), or **as directed**. A schedule is a list of `DoseSlot`: each one owns **its own weekdays** plus an optional time, stored JSON-encoded on the medication. Days belong to the dose rather than the schedule so adding an evening dose can't silently inherit the morning one's days. "8am and 8pm daily, plus 10:30am at weekends" is three slots; a slot with no time is a day pattern that raises no reminder, which is what a patch changed on set days wants. The schedule's `weekdays` is derived as the union.

Each due slot is ticked separately: `MedicationLog.slot` holds the `DoseSlot` id, so a morning taken and an evening missed are distinguishable.

`NotificationService` uses one repeating weekly trigger per time per weekday; a cycle's pause can't be expressed as a repeating trigger, so its active days are scheduled individually up to a horizon and topped up on launch from `AppEnvironment.bootstrap()`, budgeted against the 64-notification iOS ceiling. The picker list is **data, not code**: `Keel/Resources/treatment-catalog.json`, read at runtime by `TreatmentCatalogService`, with a cached copy in Application Support taking precedence so `refresh(from:)` can update the list without an App Store release. Brand names and PBS listings move during the year; update the JSON, not a Swift file.

The symptom catalog lives in `SymptomCatalog` (built from `docs/keel-symptom-list-build-spec.md`) and is reconciled into the store on launch, once per `SymptomCatalog.version`. The check-in shows only the twelve default chips; the rest sit behind "More", grouped, in `SymptomPickerSheet`, where a custom symptom can be added to any group. The intimacy and bladder group is opt-in. Once there are 14 days of check-ins, the default chips re-order by how often she logs them.

### Apple Health

Read-only, and **merged-and-tagged** rather than kept apart. `HealthKitService` requests a curated-broad, perimenopause-relevant set (sleep, activity, vitals, body temperatures, menstrual flow, and Health's own Symptoms category including hot flushes and night sweats; sensitive types like sexual activity are deliberately excluded) and returns a plain `HealthSnapshot`. `HealthIngestor` merges it idempotently: activity → `ActivityLog`, menstrual flow → `CycleEntry`, and symptoms → `CheckInSymptom` on an **existing** check-in (never fabricating one, which would invent a mood she didn't choose). Everything with no natural home — vitals, workload, and orphan symptoms on days she didn't check in — is archived as a `HealthSample` so nothing she granted us is lost. Every imported row is tagged `source = .healthKit` and deduped by its natural key, so re-running each launch adds nothing. Real reads need the HealthKit entitlement on a signed device; the merge logic is verifiable on the Simulator with a synthetic snapshot via `-uitHealthImport`.

## The companion agent

The AI companion is an **agent with two engines and its own tools**, behind the
same `ChatService` seam the chat UI already used, so `ChatView` is unchanged.

- **Primary: Apple Intelligence** (`AppleIntelligenceEngine`, `Keel/Services/Companion/`).
  On-device Foundation Models, free and private, on eligible devices running iOS
  26+. Fully behind `#if canImport(FoundationModels)` + an availability check, so
  the iOS 17 deployment target still builds.
- **Fallback: Google Gemini** (`GeminiChatEngine`, `gemini-2.5-flash`) over its
  streaming REST API, used when Apple Intelligence is unavailable or errors before
  producing a token. A local `MockChatService` is the final resort so the chat
  always answers offline. `CompanionChatService` owns the ordering and fallback.
- **Rate limiting**: `GeminiRateLimiter` (an `actor`) enforces Gemini's free-tier
  budget (`GeminiFreeTier`, defaults for `gemini-2.5-flash`: verify at build). RPM
  is an in-memory window; RPD is persisted so it survives relaunch and resets at
  local midnight. This is a client courtesy; real enforcement belongs on the
  proxy. A Gemini key must never ship: point `KEEL_GEMINI_BASE_URL` at a proxy
  that holds the key and leave the app's key nil (`KEEL_GEMINI_API_KEY` is
  dev-only).

**Tools** are defined once, engine-agnostically, in `CompanionToolbox` and run
over `CompanionDataService`, a reusable read/analysis layer that lifts the
windowed aggregation (energy, sleep, top symptoms, adherence, GP report) out of
the dashboard and reports views. Gemini consumes the specs as function
declarations; the Apple engine wraps each in a Foundation Models `Tool` that
delegates to the same code, so query logic is single-sourced. Read tools reflect
her data back (`get_recent_checkins`, `get_symptom_trends`, `get_sleep_and_energy`,
`get_medications`, `get_cycle_summary`, `get_tracking_overview`, `build_gp_report`).
"External data" is the model's own general knowledge, not the web: there is no
browsing tool.

**Writes are confirmed, never silent.** `propose_log_symptom` and
`propose_log_checkin` don't mutate; they queue an `AgentProposal` on
`CompanionProposals`, which `ChatView` renders as a confirm/dismiss card. The
write reuses the normal repositories only when she taps confirm.

The system prompt (`KeelChatPrompt`, warm AU/NZ voice, a hard non-diagnostic
boundary, pattern-honesty rules, and a safety section) is the canonical prompt for
Gemini, with a `compact` variant for the on-device model's smaller context. Both
fold in region-matched crisis lines from `CrisisResources` (AU and NZ), so the
model never invents a support number. Verify those numbers before each release.

The data/tool layer is verifiable without an LLM: launch with
`-uitCompanionTools` (prints each read tool's JSON), `-uitCompanionProposal`
(drafts then confirms a write), or `-uitGeminiLimiter` (drives the limiter past
its budget).

### Patterns

`PatternEngine` (`Keel/Services/PatternEngine.swift`) is the single source of
pattern derivation, shared by the Patterns cards and the daily reflection. It
reads her own check-ins, sleep, and cycle logs and runs four perimenopause-aware
detectors: **sleep → next-day energy**, **premenstrual/late-luteal clustering**
of symptoms or low mood, **cycle-length variability** (a persistent 7+ day swing,
a recognised early-transition sign), and a **recurring symptom** (hot flushes and
the rest). Every figure is a real count or range from her logs; nothing is
invented, and each finding is framed as something to notice, never a diagnosis.

On the first app open of each calendar day, `DailySummaryService` builds those
findings, has **Apple Intelligence** narrate them into a warm reflection (a plain
deterministic version is written and used as the fallback when on-device AI isn't
available), and stores one `DailySummary` per day so past reflections are kept.
The narration is fed **qualitative** facts and forbidden from stating numbers, so
it can never contradict the exact figures shown on the cards below it. Verify with
`-uitDailySummary` (seeds data that trips all four detectors, generates, and
prints the findings, source, and text).

## Running it

```sh
open Keel.xcodeproj      # Xcode 16+ (built/verified on Xcode 26)
# Select an iPhone simulator and Run, or:
xcodebuild -scheme Keel -destination 'platform=iOS Simulator,name=iPhone 17' build
```

The Simulator build is unsigned (`CODE_SIGNING_ALLOWED = NO`) and uses a `NoopSyncProvider` — data persists locally via SwiftData but doesn't sync. Sign in with Apple falls back to a local identity in the Simulator.

## Enabling CloudKit, Sign in with Apple & HealthKit (needs a paid Apple Developer team)

1. Open the project, select the **Keel** target → **Signing & Capabilities**, set your Team.
2. Add capabilities: **iCloud → CloudKit** (container `iCloud.com.therecalibrationyears.keel`), **Sign in with Apple**, **HealthKit**, **Push Notifications**, **Background Modes → Remote notifications**.
3. Set `CODE_SIGN_ENTITLEMENTS = Config/Keel.entitlements` in build settings.
4. In the CloudKit dashboard, mark `updatedAt` queryable + sortable and `recordName` queryable for each record type in `RecordType`.

`AppEnvironment.makeProvider()` already returns `CloudKitSyncProvider` on device.

## Migrating to Supabase later

1. Create Postgres tables mirroring the entities above (UUID PKs, `owner_id`, timestamps, `deleted_at`); add RLS `owner_id = auth.uid()`.
2. Add `supabase-swift`; implement `SupabaseSyncProvider: SyncProvider` (map `RemoteRecord` ↔ rows — the `RemoteMapping` layer already defines every field).
3. Swap the provider in `AppEnvironment.makeProvider()`. No repository/view/model changes.
4. Auth: exchange the Apple ID token via `supabase.auth.signInWithIdToken(.apple)` — `ownerID` already equals the stable Apple user id.
5. One-time backfill: read local SwiftData and push through the new provider.

## Notes

- **Fonts**: the design calls for Cormorant + DM Sans. Until the `.ttf` files are dropped into `Keel/Resources/Fonts/` and listed in `Config/Info.plist` `UIAppFonts`, `KeelFont` falls back to the system serif / sans faces automatically (preserving the serif-vs-sans contrast).
- **Colors** are defined in code (`KeelColor`); promote to Asset-Catalog color sets to add dark mode later.
