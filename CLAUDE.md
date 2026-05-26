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

## Architecture (brief)

- SwiftUI (iOS 17+), `@Observable`, SwiftData as local source of truth.
- Backend-agnostic: sync goes through a `SyncProvider` protocol (CloudKit now,
  Supabase later) so migration is a drop-in swap. No CloudKit/Supabase types
  above the repository layer.
- Design tokens live in `Keel/DesignSystem/`. Read colours from the injected
  `\.keelTheme`, not `KeelColor.*` directly, so themes and dark mode work.
