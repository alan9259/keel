import SwiftUI

/// A tiny line chart for a short series of values: a soft area fill, the line, and
/// an emphasised latest point. Purely decorative context for a number beside it,
/// so it carries no axes or labels.
struct Sparkline: View {
    let values: [Double]
    var color: Color

    var body: some View {
        Canvas { ctx, size in
            guard values.count >= 2 else { return }
            let minV = values.min() ?? 0
            let maxV = values.max() ?? 0
            let range = maxV - minV
            let inset: CGFloat = 3
            let h = size.height - inset * 2

            func point(_ i: Int) -> CGPoint {
                let x = size.width * CGFloat(i) / CGFloat(values.count - 1)
                let norm = range == 0 ? 0.5 : (values[i] - minV) / range
                return CGPoint(x: x, y: inset + h * (1 - CGFloat(norm)))
            }

            var line = Path()
            line.move(to: point(0))
            for i in 1..<values.count { line.addLine(to: point(i)) }

            var area = line
            area.addLine(to: CGPoint(x: size.width, y: size.height))
            area.addLine(to: CGPoint(x: 0, y: size.height))
            area.closeSubpath()
            ctx.fill(area, with: .linearGradient(
                Gradient(colors: [color.opacity(0.18), color.opacity(0)]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)))

            ctx.stroke(line, with: .color(color),
                       style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))

            let end = point(values.count - 1)
            ctx.fill(Path(ellipseIn: CGRect(x: end.x - 2.5, y: end.y - 2.5, width: 5, height: 5)),
                     with: .color(color))
        }
        .accessibilityHidden(true)
    }
}
