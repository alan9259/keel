import SwiftUI

/// A soft, readable line chart for one imported vital over recent days: a smooth
/// curve through her data with a gentle area fill, an emphasised "now" point, and
/// quiet min/max + date labels. Calm rather than clinical, to match the app's voice.
/// Purely her own Apple Health data; no invented values.
struct VitalLineChart: View {
    @Environment(\.keelTheme) private var theme
    let points: [VitalTrend.Point]
    var color: Color
    var unit: String = ""
    /// Hide the numeric y-axis for a metric whose absolute value reads oddly out of
    /// context (overnight wrist temperature); the shape of the trend still shows.
    var showsScale: Bool = true

    private let chartHeight: CGFloat = 92
    private let vInset: CGFloat = 12

    private var sorted: [VitalTrend.Point] { points.sorted { $0.day < $1.day } }

    var body: some View {
        let values = sorted.map(\.value)
        let lo = values.min() ?? 0
        let hi = values.max() ?? 1
        return HStack(alignment: .top, spacing: 10) {
            if showsScale {
                VStack(alignment: .trailing, spacing: 0) {
                    Text(axisLabel(hi))
                    Spacer(minLength: 0)
                    Text(axisLabel(lo))
                }
                .font(KeelFont.sans(10)).foregroundStyle(theme.muted.opacity(0.75)).monospacedDigit()
                .frame(width: 26, height: chartHeight)
                .padding(.vertical, vInset)
            }
            VStack(spacing: 6) {
                chartCanvas(lo: lo, hi: hi).frame(height: chartHeight)
                if let first = sorted.first?.day, let last = sorted.last?.day, first != last {
                    HStack {
                        Text(dateLabel(first))
                        Spacer(minLength: 0)
                        Text(dateLabel(last))
                    }
                    .font(KeelFont.sans(10)).foregroundStyle(theme.muted.opacity(0.7))
                }
            }
        }
    }

    private func chartCanvas(lo: Double, hi: Double) -> some View {
        Canvas { ctx, size in
            let values = sorted.map(\.value)
            guard values.count >= 2 else { return }
            let range = hi - lo
            let h = size.height - vInset * 2
            let pts: [CGPoint] = values.indices.map { i in
                let x = size.width * CGFloat(i) / CGFloat(values.count - 1)
                let norm = range == 0 ? 0.5 : (values[i] - lo) / range
                return CGPoint(x: x, y: vInset + h * (1 - CGFloat(norm)))
            }
            let curve = Self.smoothPath(through: pts)

            // Gentle area under the curve.
            var area = curve
            area.addLine(to: CGPoint(x: size.width, y: size.height))
            area.addLine(to: CGPoint(x: 0, y: size.height))
            area.closeSubpath()
            ctx.fill(area, with: .linearGradient(
                Gradient(colors: [color.opacity(0.22), color.opacity(0.02)]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)))

            ctx.stroke(curve, with: .color(color),
                       style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))

            // The most recent reading: one calm marker, ringed in the card colour so it
            // reads as "you are here" without the clutter of a dot on every point.
            if let end = pts.last {
                ctx.fill(Path(ellipseIn: CGRect(x: end.x - 6, y: end.y - 6, width: 12, height: 12)),
                         with: .color(color.opacity(0.16)))
                let dot = Path(ellipseIn: CGRect(x: end.x - 3.5, y: end.y - 3.5, width: 7, height: 7))
                ctx.stroke(dot, with: .color(theme.card), lineWidth: 2)
                ctx.fill(dot, with: .color(color))
            }
        }
        .accessibilityHidden(true)
    }

    /// A Catmull-Rom curve, expressed as cubic Béziers, for a soft natural line through
    /// the points (no sharp zig-zags). Falls back to straight segments for 2 points.
    static func smoothPath(through pts: [CGPoint]) -> Path {
        var path = Path()
        guard let first = pts.first else { return path }
        path.move(to: first)
        guard pts.count > 2 else {
            for p in pts.dropFirst() { path.addLine(to: p) }
            return path
        }
        for i in 0..<(pts.count - 1) {
            let p0 = pts[max(i - 1, 0)]
            let p1 = pts[i]
            let p2 = pts[i + 1]
            let p3 = pts[min(i + 2, pts.count - 1)]
            let control1 = CGPoint(x: p1.x + (p2.x - p0.x) / 6, y: p1.y + (p2.y - p0.y) / 6)
            let control2 = CGPoint(x: p2.x - (p3.x - p1.x) / 6, y: p2.y - (p3.y - p1.y) / 6)
            path.addCurve(to: p2, control1: control1, control2: control2)
        }
        return path
    }

    private func axisLabel(_ value: Double) -> String {
        value == value.rounded() ? "\(Int(value))" : String(format: "%.1f", value)
    }

    private func dateLabel(_ day: Date) -> String {
        day.formatted(.dateTime.day().month(.abbreviated))
    }
}
