# Keel

iOS app (SwiftUI + SwiftData) for women navigating perimenopause and menopause.
Tagline: "Find your even keel."

## ⚠️ Read this first, every time

**Before writing or changing any UI or user-facing copy, read
[`DESIGN_PRINCIPLES.md`](./DESIGN_PRINCIPLES.md) and check your work against it.**
It is the source of truth for voice, copy, language (Australian/New Zealand
spelling; "hot flushes" not "hot flashes"; no em-dashes), the medical boundary
(support and inform, never diagnose or prescribe), the visual system (colour,
type, spacing tokens), and interaction principles (partial check-in beats none;
one mood picker; she taps to add, never pre-selected). This is a binding
requirement, not a suggestion.

## No fake data. If you must fake, tag it.

Never fabricate data, stats, or content and show it as real — prefer an honest
empty state ("nothing logged yet") over invented numbers. A hardcoded fallback
that stands in for missing real data is a **bug**. If you genuinely must add a
mock/stub or a placeholder value, mark it with one consistent, greppable comment
on the introducing line: `// FAKE: <what it stands in for> — remove before <cond>`.
Always use this exact `// FAKE:` tag so `grep -rn "// FAKE:"` finds every stand-in.
(`#if DEBUG` DebugHarness seeds/probes are exempt — they don't ship.)

## Verify your work — don't just reason

Confirm the actual root cause before claiming a fix (trace the real code path;
reproduce the failure where you can). After **any** change, verify it empirically
before saying it works: build it, then run it in the **simulator** and confirm the
behaviour — a screenshot for UI, or a small `#if DEBUG` launch-arg probe
(`DebugHarness`) that drives the real code and prints/asserts the result. Prefer a
**Release** simulator build (`-configuration Release`) to catch optimizer/entitlement
-only failures Debug hides. Be explicit about what you did **not** verify: some
paths only work on a real signed device (Sign in with Apple, notification
delivery/taps, HealthKit, CloudKit, entitlement-gated features) — say so plainly.

## Cover new code with unit tests

Every change to logic gets unit tests in the **`KeelTests`** target, added in the
same change — not deferred. Run them and make them pass before saying a task is
done:

```
xcodebuild test -project Keel.xcodeproj -scheme Keel \
  -destination 'id=<booted-sim-udid>' -derivedDataPath build CODE_SIGNING_ALLOWED=NO
```

- **What to test:** the deterministic core especially — schedule/date rules,
  computed values, repository reads/writes, parsing, any branchy decision. Cover
  the happy path **and** the edges (empty, boundary, "already done", not-due,
  past/future, timezone). Every bug you fix gets a test that fails before the fix
  and passes after (a regression test), named for the bug.
- **Make it testable:** if logic is trapped inside a view, a `@MainActor` type, or
  interleaved with side effects (notifications, network), **extract the decision
  into a pure, `nonisolated` function** and test that — as `NotificationService`'s
  `plannedOccurrences` was split out from `rescheduleMedication`. Inject `now`/
  `Calendar` (use a fixed UTC `Calendar` in tests) so nothing depends on the wall
  clock or machine timezone. Use an **in-memory** store for SwiftData tests
  (`KeelSchema.makeContainer(inMemory: true)`).
- Tests complement the `DebugHarness` launch-arg probes (which exercise the real
  app on the simulator); they don't replace them. Prefer a fast unit test when the
  logic can be isolated. These bugs are why: unit tests caught two real auto-log
  defects that manual probes had missed.

## Always bump the build number for a build

When you produce a build meant to run on a device or go to TestFlight/distribution
(an archive or a signed device build), **bump `CURRENT_PROJECT_VERSION` first,
without being asked** — increment the integer in `Keel.xcodeproj/project.pbxproj`
(it appears in **both** the Debug and Release configs; keep them equal) and commit
it. Every shippable build must carry a fresh, higher build number.
(Iterative simulator compile-checks during development don't each need a bump.)

## Architecture (brief)

- SwiftUI (iOS 17+), `@Observable`, SwiftData as local source of truth.
- Backend-agnostic: sync goes through a `SyncProvider` protocol (CloudKit now,
  Supabase later) so migration is a drop-in swap. No CloudKit/Supabase types
  above the repository layer.
- Design tokens live in `Keel/DesignSystem/`. Read colours from the injected
  `\.keelTheme`, not `KeelColor.*` directly, so themes and dark mode work.
