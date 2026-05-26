import SwiftUI

/// Minimal area sparkline. `values` are normalized 0…1 with `nil` for missing
/// days; missing points are skipped and the line connects what exists.
struct Sparkline: View {
    let values: [Double?]
    let color: Color

    var body: some View {
        GeometryReader { geo in
            let pts = points(in: geo.size)
            ZStack {
                if pts.count >= 2 {
                    Path { path in
                        path.move(to: CGPoint(x: pts.first!.x, y: geo.size.height))
                        pts.forEach { path.addLine(to: $0) }
                        path.addLine(to: CGPoint(x: pts.last!.x, y: geo.size.height))
                        path.closeSubpath()
                    }
                    .fill(LinearGradient(
                        colors: [color.opacity(0.35), color.opacity(0.02)],
                        startPoint: .top, endPoint: .bottom
                    ))
                    Path { path in
                        path.move(to: pts.first!)
                        pts.dropFirst().forEach { path.addLine(to: $0) }
                    }
                    .stroke(color, style: StrokeStyle(lineWidth: 2, lineJoin: .round))
                } else if let only = pts.first {
                    Circle().fill(color).frame(width: 4, height: 4)
                        .position(only)
                }
            }
        }
    }

    private func points(in size: CGSize) -> [CGPoint] {
        let n = values.count
        var out: [CGPoint] = []
        for (i, value) in values.enumerated() {
            guard let value else { continue }
            let x = n > 1 ? size.width * CGFloat(i) / CGFloat(n - 1) : size.width / 2
            let y = size.height * (1 - CGFloat(min(max(value, 0), 1)))
            out.append(CGPoint(x: x, y: y))
        }
        return out
    }
}
