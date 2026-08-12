import AppKit
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

// Keel brand mark (two-tone "tt" symbol from the v1 guidelines): a rosewood disc
// holding water settled to level, with the letter K knocked through so the tile
// colour shows through. Geometry is 1:1 with the guideline SVG (100x100 viewBox).
// Output: opaque 1024x1024 PNG, no alpha (App Store icon requirement).

let size = 1024
let cs = CGColorSpaceCreateDeviceRGB()
let ctx = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8,
                    bytesPerRow: 0, space: cs,
                    bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!

func rgb(_ hex: UInt32, _ a: CGFloat = 1) -> CGColor {
    CGColor(red: CGFloat((hex >> 16) & 0xFF)/255, green: CGFloat((hex >> 8) & 0xFF)/255,
            blue: CGFloat(hex & 0xFF)/255, alpha: a)
}
let rosewood: UInt32 = 0x8C4A45   // brand/primary tile
let offwhite: UInt32 = 0xF7F5F0   // surface/base mark

// Map SVG (100x100, y-down) into the y-up bitmap.
let s = CGFloat(size) / 100.0
ctx.translateBy(x: 0, y: CGFloat(size))
ctx.scaleBy(x: s, y: -s)

func P(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x, y: y) }

// 1) Tile: full opaque rosewood square (iOS applies the rounded mask).
ctx.setFillColor(rgb(rosewood))
ctx.fill(CGRect(x: 0, y: 0, width: 100, height: 100))

// 2) Disc: circle (50,50) r36, off-white at 30%.
let discRect = CGRect(x: 50 - 36, y: 50 - 36, width: 72, height: 72)
ctx.setFillColor(rgb(offwhite, 0.3))
ctx.fillEllipse(in: discRect)

// 3) Water: settled-level fill, clipped to the disc, off-white.
ctx.saveGState()
ctx.addEllipse(in: discRect); ctx.clip()
let water = CGMutablePath()
water.move(to: P(6, 62))
water.addCurve(to: P(50, 63), control1: P(22, 55), control2: P(32, 68))
water.addCurve(to: P(96, 59), control1: P(66, 58.5), control2: P(78, 63))
water.addLine(to: P(96, 96))
water.addLine(to: P(6, 96))
water.closeSubpath()
ctx.addPath(water); ctx.setFillColor(rgb(offwhite)); ctx.fillPath()
ctx.restoreGState()

// 4) The K, knocked through in the tile colour (rosewood), always upright.
let k = CGMutablePath()
k.move(to: P(38, 30)); k.addLine(to: P(38, 70))                                   // spine
k.move(to: P(64, 30)); k.addCurve(to: P(47, 47), control1: P(58, 36), control2: P(53, 41)) // upper arm
k.move(to: P(47, 47)); k.addCurve(to: P(64, 70), control1: P(55, 53), control2: P(61, 60)) // lower leg
ctx.addPath(k)
ctx.setStrokeColor(rgb(rosewood))
ctx.setLineWidth(10)
ctx.setLineCap(.round)
ctx.setLineJoin(.round)
ctx.strokePath()

guard let img = ctx.makeImage() else { fatalError("no image") }
let out = URL(fileURLWithPath: CommandLine.arguments[1])
let dst = CGImageDestinationCreateWithURL(out as CFURL, UTType.png.identifier as CFString, 1, nil)!
CGImageDestinationAddImage(dst, img, nil)
CGImageDestinationFinalize(dst)
print("wrote \(out.path)")
