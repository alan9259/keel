import SwiftUI

/// The Keel brand mark (per brand guidelines v1.0): a disc holding water settled
/// to level, with the letter K knocked through it so the tile colour shows
/// through. The K is always upright. Geometry is 1:1 with the guideline SVG
/// (100x100 space), so this matches the app icon exactly.
///
/// Defaults render the primary rosewood tile badge; corner radius is 22% of the
/// side (matching the guideline tiles) and clipped inside the canvas so it scales
/// to any frame. iOS masks the app-icon PNG separately.
struct KeelMark: View {
    /// Brand palette (guidelines section 3). Kept local to the mark so it renders
    /// identically regardless of the app's current theme.
    static let rosewood = Color(hex: 0x8C4A45)
    static let offWhite = Color(hex: 0xF7F5F0)
    static let mistBlue = Color(hex: 0x426070)

    /// The tile/background colour, which the K knocks through to reveal.
    var tile: Color = KeelMark.rosewood
    /// The mark colour (disc + water).
    var mark: Color = KeelMark.offWhite

    var body: some View {
        Canvas { ctx, size in
            let s = size.width / 100
            func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * s, y: y * s) }

            // Round the tile (22% of the side) and clip everything to it.
            ctx.clip(to: Path(roundedRect: CGRect(origin: .zero, size: size),
                              cornerRadius: size.width * 0.22, style: .continuous))

            // Tile fill (also what the K reveals).
            ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(tile))

            // Disc, faint.
            let disc = Path(ellipseIn: CGRect(x: (50 - 36) * s, y: (50 - 36) * s, width: 72 * s, height: 72 * s))
            ctx.fill(disc, with: .color(mark.opacity(0.3)))

            // Water settled level, clipped to the disc.
            ctx.drawLayer { layer in
                layer.clip(to: disc)
                var w = Path()
                w.move(to: p(6, 62))
                w.addCurve(to: p(50, 63), control1: p(22, 55), control2: p(32, 68))
                w.addCurve(to: p(96, 59), control1: p(66, 58.5), control2: p(78, 63))
                w.addLine(to: p(96, 96)); w.addLine(to: p(6, 96)); w.closeSubpath()
                layer.fill(w, with: .color(mark))
            }

            // The upright K, knocked through in the tile colour.
            var k = Path()
            k.move(to: p(38, 30)); k.addLine(to: p(38, 70))                                    // spine
            k.move(to: p(64, 30)); k.addCurve(to: p(47, 47), control1: p(58, 36), control2: p(53, 41)) // upper arm
            k.move(to: p(47, 47)); k.addCurve(to: p(64, 70), control1: p(55, 53), control2: p(61, 60)) // lower leg
            ctx.stroke(k, with: .color(tile),
                       style: StrokeStyle(lineWidth: 10 * s, lineCap: .round, lineJoin: .round))
        }
        .aspectRatio(1, contentMode: .fit)
        .accessibilityHidden(true)
    }
}

#if DEBUG
#Preview {
    VStack(spacing: 24) {
        KeelMark().frame(width: 120, height: 120)
        KeelMark(tile: KeelMark.mistBlue).frame(width: 72, height: 72)
        KeelMark(tile: KeelMark.offWhite, mark: KeelMark.rosewood).frame(width: 60, height: 60)
    }
    .padding(40)
}
#endif
