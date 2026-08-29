# Brand mark source art (guidelines v1)

The Keel mark: a disc holding water settled to level, with an upright K knocked
through so the tile colour shows. Geometry is a 100x100 space.

- `keel-appicon-1024.png` — the app icon (rosewood tile). Emplaced at
  `Keel/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon.png`.
- `keel-icon-rosewood.svg` / `keel-icon-charcoal.svg` / `keel-icon-offwhite-mono.svg`
  — full icon tiles in each colourway.
- `keel-mark-on-rosewood.svg` — the bare mark (no tile), for placing on rosewood.

These are the source of truth. The in-app mark is drawn parametrically by
`Keel/DesignSystem/KeelMark.swift` (Canvas), 1:1 with this geometry, so it scales
and themes without rasterising. The launch-screen tile
(`Keel/Resources/Assets.xcassets/LaunchMark.imageset`) is rendered from the same
geometry.
