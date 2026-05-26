# Keel Design Principles

Keel is a warm, non-clinical companion for women navigating perimenopause and
menopause. Tagline: "Find your even keel." This file is the single source of
truth for how the app looks, sounds, and behaves. **Read it before writing any
UI or user-facing copy, and check new work against it.**

---

## 1. The boundary (never breach)

Keel supports and informs. It never diagnoses, prescribes, or replaces a doctor.
No copy anywhere in the app may name a condition she "has", recommend a
treatment, dose or supplement, or tell her what to do about her health. When in
doubt, reflect her own data back and suggest she raise it with her GP.

This applies to the AI companion too: it stays supportive, defers to her GP, and
never gives medical advice.

## 2. Voice

Calm, warm, human, hopeful, intelligent. Like telling a trusted friend what is
going on. Never alarming, never clinical-cold, never hype. Short and plain. She
is busy, often half-awake or on the move.

## 3. Language and spelling

- Australian and New Zealand spelling throughout (personalise, colour, recognise, oestrogen).
- **Hot flushes**, never hot flashes. **GP**, not doctor's office. **Chemist** or pharmacy, not drugstore.
- **HRT** is the term used in the interface. MHT may appear once in educational content as "HRT (also called MHT)".

## 4. Wince list (never use)

- Em-dashes. Use commas, colons or full stops.
- "It's not X, it's Y" constructions.
- "Honestly" or "to be honest".
- "Midlife" or "midlife crisis". Name what is happening ("this stage", "when several things shift at once") instead.
- Inspirational-influencer or corporate filler ("bio-optimise", "your best self", "journey to wellness").
- "Track harder", "perfect your", performance framing.

---

## 5. Visual system

Defined in `Keel/DesignSystem/`. Always use the tokens, never hard-code raw
values in views.

**Palette** (`KeelColor` / `KeelTheme`). Cream and warm-brown base, sage and
terracotta accents. Terracotta is the primary accent; sage is the calm
secondary; plum is a rare tertiary accent.

| Token | Hex | Use |
|---|---|---|
| cream | `#FAF7F2` | page background |
| warmGrey | `#3C3731` | body text |
| heading | `#5C4F47` | serif headlines |
| terracotta | `#C8866B` | primary accent, buttons, active state |
| sage | `#A8B5A4` | secondary / calm accent |
| plum | `#6B5B7B` | rare tertiary accent |

Colour is resolved through `KeelTheme.resolve(themeID:isDark:)`, injected as
`\.keelTheme`. Read colours from the environment theme (`theme.background`,
`theme.accent`, `theme.card`, `theme.text`, `theme.muted`, `theme.border`) so
light/dark and the theme packs all work. Do not reference `KeelColor.*` directly
inside feature views.

**Type** (`KeelFont`). Elegant serif headlines (Cormorant) paired with a
humanist sans (DM Sans) for body and UI. Use the semantic helpers
(`KeelFont.serif(_:)`, `.sans(_:)`, `.body`, `.caption`, `.eyebrow`, etc.), which
are Dynamic-Type aware. Serif carries warmth and headlines; sans carries clarity
and everything functional.

**Spacing** (`Spacing`, 8pt scale) and **radius** (`Radius`). Cards and primary
buttons use `Radius.card` (18). Inputs use `Radius.input` (12). Chips and toggles
are pills. Standard horizontal screen padding is 20–24.

**Elevation.** Soft, warm shadows only (`keelCardShadow`, `keelFabShadow`). No
hard or cool-grey drop shadows.

## 6. Iconography and emoji

- **Chrome and decorative icons: SF Symbols.** All functional and structural
  icons use native SF Symbols, tinted from the theme.
- **Genuine emoji (moods, mood packs, social): the bundled Twemoji COLR colour
  font** via `KeelFont.emoji(_:)`. The simulator runtime lacks Apple Color Emoji,
  so this is required for emoji to render. Do not add `.foregroundStyle`
  expecting to recolour a COLR emoji; its colour comes from the font. Twemoji is
  CC-BY 4.0, attributed in About.

## 7. Interaction principles

- **A partial check-in beats none.** Never require fields. She can save mood
  alone, or add energy, diary, and symptoms if she has the time and words.
- **One mood picker.** Mood is chosen once, in the entry slide. The check-in
  detail screen never asks for it again.
- **She taps to add. Never pre-selected.** Options (symptoms, etc.) start
  unselected. Adding a custom item creates it unselected; she taps to include it.
  Custom items are added within a category, so the category is never guessed.
- **Meet her where she is.** Time-aware greetings, gentle empty states, no
  streaks, no nagging, no performance framing.
- **Her data, reflected back.** Insights and reports describe patterns in her own
  words and numbers. They never conclude, diagnose, or instruct.

## 8. Accessibility and platform

- Minimum 44pt tap targets. Respect safe areas.
- Dynamic Type: use the semantic font helpers, never fixed system sizes for text.
- VoiceOver labels on every control, especially icon-only buttons.
- Honour Reduce Motion. Keep animations short and calm (roughly 0.18–0.25s,
  ease-out). Nothing bouncy or attention-grabbing.
- Haptics (`Haptics`) on selections and completions, never as decoration.

## 9. When unsure

Choose the calmer, plainer, less clinical option. Reflect her data rather than
interpret it. If a piece of copy could read as alarming, prescriptive, or hype,
rewrite it. When a rule here conflicts with a visual convenience, the rule wins.
