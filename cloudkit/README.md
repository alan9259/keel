# CloudKit schema

Sync uses SwiftData's automatic CloudKit mirroring (`cloudKitDatabase: .automatic`,
see `Keel/Persistence/KeelSchema.swift`). The **source of truth** for the schema is
the `@Model` code in `Keel/Models/` — the CloudKit schema is *derived* from it.

This folder holds a committed snapshot of that derived schema (`schema.ckdb`) so
**Production only changes from a reviewed, version-controlled file**, deployed with
`../scripts/cloudkit-schema.sh`, rather than from whatever a Debug build happens to
leave in Development.

## Environments (why this exists)

- **Development** auto-creates/extends its schema on write (JIT). Any signed build
  can drift it — that's by design and can't be locked.
- **Production** never auto-updates. It only changes on an explicit deploy. It is
  the controlled gate; TestFlight / App Store users run against it.

## First-time setup

1. Create a **management token** in the CloudKit Console (Settings → Tokens), then:
   ```sh
   ./scripts/cloudkit-schema.sh save-token       # paste the token at the prompt
   ```
2. Create the Development schema by running a **signed** build that writes data
   (a signed Simulator signed into iCloud is enough — see the app's launch logs
   filtered by `KEEL_CLOUDKIT`). SwiftData mirrors the models up and creates the
   `CD_`-prefixed record types.

## Everyday flow (after a model change)

```sh
./scripts/cloudkit-schema.sh export     # snapshot Development -> cloudkit/schema.ckdb
git add cloudkit/schema.ckdb            # review the diff, commit as schema-of-record
./scripts/cloudkit-schema.sh validate   # dry-run against Production
./scripts/cloudkit-schema.sh deploy     # import the committed file into Production
```

`diff` shows how live Development differs from the committed file; `apply-dev`
pushes the committed file back into Development (e.g. after `cktool reset-schema`).

## Notes

- **Never commit the token.** `save-token` stores it in your keychain; a one-off run
  can use `CLOUDKIT_MANAGEMENT_TOKEN=… ./scripts/cloudkit-schema.sh deploy`.
- Team/container default to `UPY445K892` / `iCloud.com.keel`; override with
  `CLOUDKIT_TEAM_ID` / `CLOUDKIT_CONTAINER_ID` if they change.
