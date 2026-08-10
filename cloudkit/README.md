# CloudKit schema: generation, deploy, and maintenance

Sync is **SwiftData's automatic CloudKit mirroring** (`cloudKitDatabase: .automatic`
in `Keel/Persistence/KeelSchema.swift`). The **source of truth is the `@Model` code**
in `Keel/Models/`; the CloudKit schema (`CD_`-prefixed record types) is *derived*
from it by `NSPersistentCloudKitContainer` at runtime. `cloudkit/schema.ckdb` is a
committed snapshot of that derived schema so Production only changes from a
reviewed file.

---

## 1. How the two environments behave

- **Development** — the schema is created **just-in-time**: a record type appears
  only when the first record of that entity is written & mirrored, and a *field*
  appears only when some record writes a non-nil value for it. Any signed build
  can extend it. It's a sandbox and can't be locked.
- **Production** — never auto-updates and can't JIT-create anything. It only
  changes on an explicit deploy, and it *rejects* records with types/fields it
  doesn't have. TestFlight / App Store users run here. This is the controlled gate.

Consequence: before deploying, **Development must hold every entity with every
field populated**, or Production will be missing pieces and real writes will fail.

---

## 2. Why signed builds (and how)

CloudKit only activates on a **signed, entitled** build. The unsigned simulator
build has no iCloud entitlement, so `.automatic` stays inert there — which also
means it **can't run CloudKit's model validation** (that's how the
"relationships must be optional" crash reached a device instead of being caught
locally). Debug is configured to sign the simulator too
(`CODE_SIGNING_ALLOWED = YES`), so a normal Xcode **Run** on the sim is entitled.

- **Signing the build ≠ syncing data.** Mirroring only happens if that simulator
  is **signed into iCloud** (Settings → Apple ID). No iCloud account → validates
  the schema at launch, stays local. Sign in → mirrors to Development.
- Automated / CI builds stay unsigned by passing `CODE_SIGNING_ALLOWED=NO` on the
  `xcodebuild` command line.
- Console tracing: filter the run log by **`KEEL_CLOUDKIT`** (from
  `CloudKitDebugProbe`, DEBUG only) to see `account=…` and `setup`/`import`/`export`
  events confirming mirroring is live.

---

## 3. First-time setup

1. Create a **management token** in the CloudKit Console (Settings → Tokens), then:
   ```sh
   ./scripts/cloudkit-schema.sh save-token   # paste at the prompt
   ```
2. Boot a simulator and **sign it into iCloud** (Settings → Apple ID). Any Apple
   ID works for Development.

---

## 4. Generate the **full** schema (all 12 record types + fields)

Exercising every feature by hand is fiddly (Insight / DailySummary / HealthSample
are hard to trigger), so there's a DEBUG action that inserts one fully-populated,
tombstoned record of **every** entity, with relationships linked:

1. Run a **signed** build on the iCloud-signed-in simulator with the launch arg
   **`-uitSeedSchema`** (Xcode: Product → Scheme → Edit Scheme → Run → Arguments;
   or `xcrun simctl launch <dev> com.keel -uitSeedSchema`). Console prints
   `KEEL_SCHEMASEED inserted one of each of 12 entities`.
2. Watch `KEEL_CLOUDKIT export succeeded` events flush (background/foreground the
   app to help). Give it a minute.
3. In the **CloudKit Console → Development**, container `iCloud.com.keel`, confirm
   all 12 `CD_*` record types exist (`CD_UserProfile`, `CD_CheckIn`, `CD_Symptom`,
   `CD_CheckInSymptom`, `CD_CycleEntry`, `CD_Medication`, `CD_MedicationLog`,
   `CD_Insight`, `CD_ActivityLog`, `CD_ChatMessage`, `CD_DailySummary`,
   `CD_HealthSample`).

The seed records are tombstoned (`deletedAt` set, `ownerID = "schema-seed"`) so
they don't show in the app; they exist only to materialise the schema. Delete
them afterwards if you like (the schema stays) — they're easy to find by
`ownerID == schema-seed`.

---

## 5. Deploy to Production

Once Development has the full schema:

```sh
./scripts/cloudkit-schema.sh export     # snapshot Development -> cloudkit/schema.ckdb
git add cloudkit/schema.ckdb            # review the diff, commit as schema-of-record
./scripts/cloudkit-schema.sh validate   # dry-run against Production
./scripts/cloudkit-schema.sh deploy      # import the committed file into Production
```

`deploy` is equivalent to the Console's "Deploy Schema Changes", but from a
reviewed, version-controlled file. `diff` shows Development vs the committed file;
`apply-dev` pushes the committed file back into Development (e.g. after a reset).

---

## 6. Maintaining the schema when models change

A `@Model` change (new field, new model, changed relationship) means the CloudKit
schema must be re-generated and re-deployed. **Every model must stay CloudKit-shaped
or the app traps on launch of a signed build:**

1. **No `@Attribute(.unique)`** — CloudKit forbids unique constraints. Keep `id`
   unique only in app logic (always set in `init`).
2. **Every attribute optional or defaulted** — e.g. `var x: Int = 0`. Use
   `Date.now` (not `.now`) for date defaults; the `@Model` macro can't resolve `.now`.
3. **Every relationship optional** — including to-many: `var links: [X]?`, never
   `[X] = []`. Read them with `?? []`.
4. **Every relationship has an inverse** — declare `inverse:` on one side (a
   unidirectional relationship is rejected).

Then:

1. Make the model change (reviewed in a PR — this is the real schema change).
2. Run a **signed** build. It validates the models (a rule violation crashes here,
   locally — that's the point) and JIT-creates the new type/fields in Development.
   Re-run `-uitSeedSchema` if the change added a model or a field the seed doesn't
   populate, so the *full* schema materialises.
3. `export` → review `git diff cloudkit/schema.ckdb` → commit.
4. `validate` → `deploy` to Production.

---

## Caveats

- **Local data resets on a schema change.** `KeelMigrationPlan` has empty stages,
  so the store rebuilds on first launch after a model change (fine for internal
  testing; add real `MigrationStage`s before public launch).
- **Built-in symptoms can duplicate across devices.** They're seeded per-device
  and CloudKit dedupes by its own record identity, not our name/id, so a second
  device on the same iCloud account receives the first device's copies. Single-device
  users are unaffected; a post-sync dedupe is an open follow-up.
- **Never commit tokens** — `save-token` stores in the keychain; `*.token` is
  gitignored. Team/container default to `UPY445K892` / `iCloud.com.keel`; override
  with `CLOUDKIT_TEAM_ID` / `CLOUDKIT_CONTAINER_ID`.
