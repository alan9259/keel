import SwiftUI

/// The Keel brand mark, v2 (Mischa's final art, `brand/keel-icon-1024.png`): a solid
/// off-white disc holding mist-tinted water settled to level, with the upright K drawn
/// through it in the tile colour, on a rosewood tile.
///
/// v1 (superseded) used a translucent disc with off-white water and a lighter K set in
/// a 66% inset. v2 keeps the same disc, waterline and K paths (100x100 guideline space)
/// but makes the disc solid, tints the water mist blue, weights the K at 11 (was 10),
/// and places the mark at 84% of the tile (8-unit margin). Verified pixel-for-pixel
/// against the PNG by `KeelMarkTests`.
///
/// Drawn parametrically so it stays crisp at any size. By default the tile is rounded
/// (22% of the side) for in-app use; pass `cornerRadius: 0` for the square app-icon
/// form (iOS masks the icon itself). Colours are the brand's own, deliberately not the
/// app theme: the mark looks the same whatever theme or Colour Mode she picks.
struct KeelMark: View {
    /// The tile, which the K is drawn in.
    var tile: Color = KeelTheme.rosewood
    /// The disc above the waterline.
    var disc: Color = KeelTheme.offWhite
    /// The water: mist blue at 22% over off-white (#CFD4D4).
    var water: Color = KeelMark.mistWater
    /// Tile corner radius in points; nil rounds 22% of the side.
    var cornerRadius: CGFloat? = nil

    static let mistWater = KeelTheme.offWhite.mix(with: KeelTheme.mistBlue, by: 0.22, in: .device)

    var body: some View {
        Canvas { ctx, size in
            let u = size.width / 100
            // The 100-unit mark sits inside the tile at 84% scale with an 8-unit margin.
            func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
                CGPoint(x: (8 + 0.84 * x) * u, y: (8 + 0.84 * y) * u)
            }

            ctx.clip(to: Path(roundedRect: CGRect(origin: .zero, size: size),
                              cornerRadius: cornerRadius ?? size.width * 0.22, style: .continuous))
            ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(tile))

            // Solid disc.
            let r = 36 * 0.84 * u
            let c = p(50, 50)
            let discPath = Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r))
            ctx.fill(discPath, with: .color(disc))

            // Water settled level, clipped to the disc.
            ctx.drawLayer { layer in
                layer.clip(to: discPath)
                var w = Path()
                w.move(to: p(6, 62))
                w.addCurve(to: p(50, 63), control1: p(22, 55), control2: p(32, 68))
                w.addCurve(to: p(96, 59), control1: p(66, 58.5), control2: p(78, 63))
                w.addLine(to: p(96, 96)); w.addLine(to: p(6, 96)); w.closeSubpath()
                layer.fill(w, with: .color(water))
            }

            // The upright K, in the tile colour.
            var k = Path()
            k.move(to: p(38, 30)); k.addLine(to: p(38, 70))                                     // spine
            k.move(to: p(64, 30)); k.addCurve(to: p(47, 47), control1: p(58, 36), control2: p(53, 41)) // upper arm
            k.move(to: p(47, 47)); k.addCurve(to: p(64, 70), control1: p(55, 53), control2: p(61, 60)) // lower leg
            ctx.stroke(k, with: .color(tile),
                       style: StrokeStyle(lineWidth: 11 * 0.84 * u, lineCap: .round, lineJoin: .round))
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
    }
}

#if DEBUG
#Preview {
    VStack(spacing: 24) {
        KeelMark().frame(width: 120, height: 120)
        KeelMark(cornerRadius: 0).frame(width: 96, height: 96)
        KeelMark().frame(width: 64, height: 64)
    }
    .padding(40)
}
#endif
