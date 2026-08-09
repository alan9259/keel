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

## Architecture (brief)

- SwiftUI (iOS 17+), `@Observable`, SwiftData as local source of truth.
- Backend-agnostic: sync goes through a `SyncProvider` protocol (CloudKit now,
  Supabase later) so migration is a drop-in swap. No CloudKit/Supabase types
  above the repository layer.
- Design tokens live in `Keel/DesignSystem/`. Read colours from the injected
  `\.keelTheme`, not `KeelColor.*` directly, so themes and dark mode work.
