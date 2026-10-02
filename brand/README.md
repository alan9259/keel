# Brand mark source art

**Current: v2** (Mischa's final art, October 2026).

- `keel-icon-1024.png` — the v2 app icon and the source of truth for the mark: a solid
  off-white disc on a rosewood tile, mist-tinted water (#CFD4D4) settled to level, and an
  upright rosewood K. Emplaced at `Keel/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png`.

In the app:

- `Keel/DesignSystem/KeelMark.swift` draws the mark parametrically (Canvas) so it scales
  without rasterising. It uses the 100x100 guideline geometry (same disc, waterline and K
  paths as v1), placed at 84% of the tile with an 8-unit margin, K stroke 11.
  `KeelTests/KeelMarkTests` renders it at 1024 and checks it pixel-for-pixel against
  `keel-icon-1024.png`.
- The launch-screen tile (`Keel/Resources/Assets.xcassets/LaunchMark.imageset`) is the v2
  PNG scaled to 200/400/600 with rounded (22%) transparent corners.

**Superseded: v1** (August 2026) — kept for reference only, do not use:
`keel-appicon-1024.png` and the `keel-icon-*.svg` / `keel-mark-on-rosewood.svg` colourways
(translucent disc, off-white water, K stroke 10, mark set in a 66% inset).
