# Keel — HRT/MHT & Supplement Tracking List (Build Spec)

**Version:** v1.0 draft for review
**Owner:** Mischa · **Build:** Chunru
**Companion to:** Keel Symptom List BuildSpec.md, Keel AI System Prompt.md

**What this is:** A picker list for the Treatment & Supplement Logger, so she can record what she's taking without typing brand names from memory. **This is a logging taxonomy, not clinical guidance.** It reflects what exists on the market so she can select it, the same way a pharmacy dropdown lists what's on the shelf. It does not recommend, dose, or suggest anything. Follows the same UX pattern as the symptom list: common defaults, grouped, with **+ Add your own** always available, because brand names and shortages change faster than we can ship.

**Non-negotiable, applies here too:** Keel logs what she tells us she's taking. It never suggests a treatment, never implies one product is better than another, and never infers what she should be on. If a screen ever reads like a recommendation, it's wrong.

---

## Important context for the build (read before building)

- **HRT vs MHT:** Clinically the correct term is now MHT (menopausal hormone therapy). Women still search and say "HRT." Per the copy deck: use **HRT** as the primary in-app term, with "(also called MHT)" used once in any educational copy, and let the GP summary use both.
- **Australia is in an ongoing patch shortage** (running through 2026, extended by the government's shortage substitution instrument). Pharmacists are actively swapping brands and strengths without new scripts. This means her actual brand may change more often than usual, and the picker needs to make swapping brands trivial, not a re-entry chore. **Don't hardcode this list into the app binary.** Keep it as data you can update without an App Store release.
- **Testosterone is TGA-approved for one specific indication in Australia**, HSDD (low sexual desire with distress) in postmenopausal women, not as a general perimenopause treatment. Compounded ("bioidentical") testosterone is unregulated and not standardised. The picker should reflect this distinction rather than presenting all testosterone options as equivalent.
- **This list will date.** Brand names, PBS listings, and shortages shift regularly (three products were added to the PBS as recently as March 2026). Treat this file as a v1 seed, not a source of truth to leave unmaintained. Worth a line in the monthly research scan to flag any material MHT/PBS changes.
- Flag this feature to the TGA classification audit already on the launch calendar. Recording what she takes is standard logging. The risk sits entirely in what the AI layer does with that data afterwards, which the AI System Prompt already governs (never interpret medically, never suggest a change).

---

## HRT / MHT

### Oestrogen

**Patches (transdermal)**
- Estradot
- Estraderm MX
- Estramon *(TGA-approved import, brought in to help manage the patch shortage)*
- *+ Add your own*

**Gel (transdermal)**
- Estrogel
- Estrogel Pro
- *+ Add your own*

**Spray (transdermal)**
- *+ Add your own* (verify current AU-approved spray brands before launch; don't assume availability)

**Tablet (oral)**
- *+ Add your own* (log by her prescription; oral tablets are less commonly first-line due to clot risk, but still prescribed)

**Vaginal / local oestrogen** *(for genitourinary symptoms specifically, e.g. dryness, recurring UTIs; low systemic absorption, a distinct category from systemic MHT)*
- Vaginal cream
- Vaginal pessary
- Vaginal ring
- *+ Add your own*

### Progesterone / progestogen

- Oral micronised progesterone (e.g. Prometrium)
- Combined oestrogen/progestogen tablet (e.g. Femoston)
- Progestogen IUD (e.g. Mirena) — log as a treatment even though it's also a contraceptive device
- *+ Add your own*

### Combined patches (oestrogen + progestogen in one patch)

- *+ Add your own* (log by prescription; combined patch brands shift with the shortage)

### Testosterone

*Present this group with a short, plain note: "Testosterone is approved in Australia for one specific use, low sexual desire after menopause. Your GP will have discussed why it's part of your plan."*

- AndroFeme (the only TGA-approved testosterone product for women in Australia)
- Testogel *(a male-formulated product, sometimes prescribed off-label at a lower dose; tag as off-label if she selects it)*
- Compounded testosterone *(unregulated, not standardised; log as "compounded" rather than trying to capture a precise strength)*
- *+ Add your own*

### Application method (attach to any HRT entry)

Patch · Gel · Spray · Tablet (oral) · Vaginal cream/pessary/ring · IUD/IUS · Cream (topical, e.g. testosterone) · Other

### Frequency (attach to any HRT entry)

Daily · Twice weekly · Weekly · Cyclic (part of the month) · As directed

### What to capture per entry

Product/brand (from list or custom) → method → strength (free text, since strengths vary by brand and shortage substitution) → start date → dose-change date (for the before/after timeline) → optional note ("switched brands due to shortage," "GP increased dose," etc.)

---

## Supplements

Kept dynamic, same as the symptom list: she builds her own stack, and Keel correlates whatever she logs against whatever symptoms she's tracking. The list below is a sensible common-core default set, not an endorsement of any of them.

**Common defaults**
- Magnesium
- Vitamin D
- Omega-3 / fish oil
- Creatine
- Collagen
- Protein powder
- Multivitamin
- Iron
- Calcium
- Probiotic
- Vitamin B6 / B-complex
- Zinc

**Often mentioned for perimenopause specifically (include, since she'll look for them)**
- Black cohosh
- Evening primrose oil
- Ashwagandha
- CoQ10
- Folic acid

**Every entry**
- *+ Add your own* (free text, saved for reuse)
- Dose (free text, no defaults suggested)
- Frequency
- Start date

---

## What NOT to build into this feature

- No "recommended" or "popular" badges on any product or supplement. Ordering should be alphabetical or personalised to her own history, never editorial.
- No pre-filled dose suggestions for supplements. She types what's on her own bottle.
- No linking a symptom to a specific product in the UI copy (e.g. never "for hot flushes, try..."). That crosses from logging into recommending, which is the line Keel does not cross.
- No treatment comparison content anywhere near this screen. If she wants to understand her options, that's a GP conversation, not an in-app feature.

---

## Before this ships

- Confirm current brand availability and PBS status with a pharmacist or GP contact, or against the TGA shortage database and the AMS dose-equivalence guide, since both move during the year.
- Run this list past whoever signs off the AI System Prompt's safety section. If the treatment logger and the AI's "never diagnose, never prescribe" boundary are reviewed together, that's one clinical sign-off instead of two.
