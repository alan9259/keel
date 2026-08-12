import SwiftUI
import UIKit

/// Typography for Keel — the brand faces from the v1 guidelines: **Literata**
/// (serif) for the wordmark, screen titles, section headings and editorial copy,
/// paired with **Poppins** (sans) for buttons, labels, navigation, body and
/// captions. Both are SIL OFL, bundled under `Keel/Resources/Fonts/` and
/// registered in `Info.plist` `UIAppFonts`. If a face somehow fails to load the
/// helpers fall back to the matching system design, so nothing goes blank.
enum KeelFont {
    // Literata ships as a variable font (default instance "Literata-Regular");
    // SwiftUI's `.weight()` interpolates the weight axis.
    private static let serifName = "Literata-Regular"
    private static let serifAvailable = UIFont(name: serifName, size: 12) != nil

    // Poppins ships as static instances, so pick the file that matches the
    // requested weight rather than synthesising bold (which looks wrong).
    private static func poppinsName(for weight: Font.Weight) -> String? {
        let name: String
        switch weight {
        case .ultraLight, .thin, .light: name = "Poppins-Light"
        case .medium, .semibold, .bold, .heavy, .black: name = "Poppins-Medium"
        default: name = "Poppins-Regular"
        }
        return UIFont(name: name, size: 12) != nil ? name : nil
    }

    static func serif(_ size: CGFloat, weight: Font.Weight = .semibold, relativeTo style: Font.TextStyle = .body) -> Font {
        if serifAvailable {
            return .custom(serifName, size: size, relativeTo: style).weight(weight)
        }
        return .system(size: size, weight: weight, design: .serif)
    }

    static func sans(_ size: CGFloat, weight: Font.Weight = .regular, relativeTo style: Font.TextStyle = .body) -> Font {
        if let name = poppinsName(for: weight) {
            return .custom(name, size: size, relativeTo: style)
        }
        return .system(size: size, weight: weight, design: .default)
    }

    // Type scale (KEEL_DESIGN_SPEC.md)
    static var displayLarge: Font { serif(56, weight: .semibold, relativeTo: .largeTitle) }
    static var h1: Font { serif(34, weight: .semibold, relativeTo: .largeTitle) }
    static var h2: Font { serif(28, weight: .semibold, relativeTo: .title) }
    static var h3: Font { serif(22, weight: .medium, relativeTo: .title2) }
    static var bodyLarge: Font { sans(17, weight: .regular, relativeTo: .body) }
    static var body: Font { sans(15, weight: .regular, relativeTo: .subheadline) }
    static var caption: Font { sans(13, weight: .regular, relativeTo: .footnote) }
    static var button: Font { sans(17, weight: .semibold, relativeTo: .body) }
    static var eyebrow: Font { sans(11, weight: .semibold, relativeTo: .caption2) }

    // Bundled color-emoji font (Twemoji, COLR). Used for genuine emoji (moods)
    // so they render even where the system Apple Color Emoji font is absent.
    private static let emojiFontName: String? = {
        for candidate in ["Twemoji Mozilla", "TwemojiMozilla"] where UIFont(name: candidate, size: 12) != nil {
            return candidate
        }
        return nil
    }()

    static func emoji(_ size: CGFloat) -> Font {
        if let name = emojiFontName { return .custom(name, fixedSize: size) }
        return .system(size: size)
    }
}

extension View {
    /// Consistent onboarding header, one size across every step, scaling down on
    /// small screens so a longer title never clips. Centred on illustration
    /// "moment" screens, `.leading` on content/form screens so it aligns with the
    /// body beneath it.
    func onboardingTitle(_ alignment: TextAlignment = .center) -> some View {
        self.font(KeelFont.serif(28, weight: .semibold))
            .foregroundStyle(KeelColor.heading)
            .multilineTextAlignment(alignment)
            .frame(maxWidth: .infinity, alignment: alignment == .leading ? Alignment.leading : .center)
            .lineLimit(3)
            .minimumScaleFactor(0.8)
            .fixedSize(horizontal: false, vertical: true)
    }

    /// Consistent onboarding lead/subtitle, comfortable line height. Colour is
    /// left to the caller.
    func onboardingSubtitle(_ alignment: TextAlignment = .center) -> some View {
        self.font(KeelFont.bodyLarge)
            .multilineTextAlignment(alignment)
            .frame(maxWidth: .infinity, alignment: alignment == .leading ? Alignment.leading : .center)
            .lineSpacing(4)
            .fixedSize(horizontal: false, vertical: true)
    }

    /// Uppercase eyebrow label styling (letter-spaced, muted).
    func keelEyebrow() -> some View {
        self.font(KeelFont.eyebrow)
            .textCase(.uppercase)
            .tracking(1.5)
            .foregroundStyle(KeelColor.mutedForeground)
    }
}
