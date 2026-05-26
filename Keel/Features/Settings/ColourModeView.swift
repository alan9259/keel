import SwiftUI

struct ColourModeView: View {
    @Environment(AppEnvironment.self) private var env
    @Environment(\.keelTheme) private var theme
    @Environment(\.colorScheme) private var systemScheme
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        @Bindable var settings = env.settings
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                ScreenHeader(title: "Colour Mode", titleSize: 28,
                             subtitle: "Currently showing: \(resolvedLabel)") { dismiss() }
                    .padding(.bottom, 8)

                ForEach(ColourMode.allCases) { mode in
                    Button {
                        withAnimation(.easeInOut(duration: 0.25)) { settings.colourMode = mode }
                        Haptics.selection()
                    } label: {
                        HStack(spacing: 14) {
                            Image(systemName: mode.symbol).font(.system(size: 19))
                                .foregroundStyle(settings.colourMode == mode ? theme.accent : theme.muted)
                                .frame(width: 44, height: 44)
                                .background(theme.inputBackground)
                                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            VStack(alignment: .leading, spacing: 3) {
                                Text(mode.label).font(KeelFont.bodyLarge).foregroundStyle(theme.text)
                                Text(mode.detail).font(KeelFont.body).foregroundStyle(theme.muted)
                            }
                            Spacer()
                            if settings.colourMode == mode {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundStyle(theme.background)
                                    .frame(width: 24, height: 24)
                                    .background(theme.accent).clipShape(Circle())
                            }
                        }
                        .padding(16)
                        .background(theme.card)
                        .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous)
                            .stroke(settings.colourMode == mode ? theme.accent : theme.border,
                                    lineWidth: settings.colourMode == mode ? 2 : 1))
                    }
                    .buttonStyle(.plain)
                }

                Text("Your preference is saved and applied immediately across the whole app.")
                    .font(KeelFont.caption).foregroundStyle(theme.muted)
                    .frame(maxWidth: .infinity).multilineTextAlignment(.center)
                    .padding(.top, 8)
            }
            .padding(.horizontal, 24).padding(.vertical, 12)
        }
        .background(theme.background.ignoresSafeArea())
        .keelFeatureScreen()
    }

    private var resolvedLabel: String {
        env.settings.isDark(systemDark: systemScheme == .dark) ? "Dark" : "Light"
    }
}
