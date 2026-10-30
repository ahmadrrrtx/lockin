# LOCKIN — Brand Specification

## 1. Mark: the Lockin hourglass

An original geometric hourglass drawn on a 108 × 108 grid (all coordinates below).

```
Top cap      rounded rect  x 34 → 74, y 27 → 33.5, radius 3
Bottom cap   rounded rect  x 34 → 74, y 74.5 → 81, radius 3
Upper glass  M37,33.5  C37,45  47,51.5  54,54  C61,51.5  71,45  71,33.5  Z
Lower glass  M37,74.5  C37,63  47,57    54,54  C61,57    71,63  71,74.5  Z
Sand mound   M45.5,70  C48,63.5  60,63.5  62.5,70  Z   (accent fill only)
```

Properties:

- Perfect vertical symmetry about x = 54 and horizontal symmetry about y = 54.
- Glass meets both caps; the pinch is a single shared point at (54, 54).
- Maximum corner radius from centre ≈ 33.6 < 36 → fully inside the adaptive-icon
  visible circle (recognisable in any launcher mask).
- Renders correctly as: full-colour fills, all-ink (light), all-bone (dark),
  pure-white silhouette (monochrome/themed icon, notification icon).

Colour roles:

| Use | Light | Dark |
|---|---|---|
| Glass + caps | `#14151A` (ink) | `#F2F2EF` (bone) |
| Sand mound | `#B7691A` | `#E8A34B` |
| Launcher background (constant) | `#16171C` | `#16171C` |
| Launcher mark (constant) | `#F1EEE7` + sand `#E8A34B` | same |
| Monochrome / themed layer | `#FFFFFF` (mark only, no mound) | same |

## 2. Wordmark

`LOCKIN` — uppercase, medium-bold weight, +0.22 em letter-spacing, rendered in the
system sans (Roboto). First choice in the Home header next to the 34 dp mark; the
mark alone is the launcher and notification identity.

## 3. Colour system (semantic tokens)

Light:
`background #FAFAF8` · `surface #FFFFFF` · `surfaceAlt #F1F1EE` · `ink #14151A` ·
`inkSecondary #5C5F68` · `inkTertiary #8B8E97` · `border #E5E5E0` ·
`accent #B7691A` (white text on it) · `accentContainer #F7E8D6` ·
`ok #2F8F53` · `alert #C0483C`

Dark:
`background #0C0D10` · `surface #15161A` · `surfaceAlt #1D1F24` · `ink #F2F2EF` ·
`inkSecondary #A7AAB3` · `inkTertiary #74777F` · `border #26282E` ·
`accent #E8A34B` (dark text `#1A1206` on it) · `ok #55C07E` · `alert #E5756A`

Rules: monochrome foundation + exactly one accent. Semantic colours only for status.
No gradients, no glassmorphism, no second accent hue.

## 4. Typography scale

| Style | Size / line | Weight |
|---|---|---|
| Display | 34 / 40 | bold |
| Title1 | 26 | bold |
| Title2 | 21 | bold |
| Title3 | 17 | bold |
| Body | 16 / +3 leading | regular |
| BodySmall | 14 | regular, secondary colour |
| Caption | 12, +0.04 em | regular |
| SectionLabel | 12, +0.12 em, uppercase | bold |
| Countdown | 52 | bold, tabular feel |
| Button | 15–16 | bold, +0.04 em |

## 5. Shape & spacing

- Radii: buttons 12, cards 14, notes 10, chips full-round. No giant rounded panels.
- Spacing scale: 4 / 8 / 10 / 14 / 16 / 20 / 24 / 32 / 48.
- Cards: surface fill + 1 dp hairline border; elevation comes from contrast, not shadow.

## 6. Assets and where they live

| Asset | File |
|---|---|
| Adaptive launcher (+ round, + monochrome) | `mipmap-anydpi-v26/ic_launcher*.xml` |
| Launcher foreground / monochrome vector | `drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` |
| Notification silhouette | `drawable/ic_notification.xml` |
| In-app mark (light/dark) | `drawable/ic_brand_mark.xml`, `drawable-night/ic_brand_mark.xml` |
| Splash mark (light/dark) + window | `drawable/ic_splash_mark.xml`, `splash_background.xml` |
| Line-icon set (24 dp, 1.8 stroke, round caps) | `drawable/ic_*.xml` |
| All tokens, styles | `values/colors.xml`, `values-night/colors.xml`, `values/styles.xml` |

## 7. Usage rules

- Always clear space ≥ ¼ mark height around the wordmark lockup.
- Never recolour the mark with gradients, never outline-only on small sizes,
  never rotate, never add elements (no padlocks, sparkles, shadows).
- Notification icon must remain the pure-white silhouette (system-tinted).
- Launcher background never becomes light — keeps contrast on any wallpaper.
