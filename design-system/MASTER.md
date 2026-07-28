# open_sapien — Design System

Offline voice recorder + on-device transcription. Android (Jetpack Compose, Material 3) and Wear OS.

## 1. The subject

This is not a productivity SaaS app. It is a **field recording instrument**: it listens, it keeps
a written record, and it never phones home. Every design decision below comes from that: the
lineage is a handheld recorder and a tape counter, not a dashboard.

Two facts drive the visual language:

- **It runs for hours.** Recording sessions are long, so the screen is dark by default for real
  battery reasons on OLED, not for fashion.
- **Nothing leaves the device.** The interface should feel like a private instrument — precise,
  quiet, unbranded — rather than a service that wants engagement.

The generated starting point (blue/orange, centred-CTA landing pattern) was rejected: it is a
marketing-page system, and this product has no marketing surface. What follows was chosen for
this brief.

## 2. Signature

**The level meter is the hero.** One idea carries the whole product:

- **Record screen** — a live amplitude meter drawn as thin vertical ticks, scrolling right to
  left. It is the only animated element, and it is animated because it is *data*, not decoration.
- **Archive** — each transcript carries a small static waveform derived from its own recording,
  so the list reads as a shelf of tapes rather than a list of documents.
- **Everywhere** — durations, sizes, percentages and timestamps are set in tabular monospace so
  digits line up in a column, the way a counter does.

If a screen has nothing to measure, it stays completely still.

## 3. Colour

Dark is the primary theme; light is a full peer, not an afterthought.

| Role | Dark | Light | Use |
|---|---|---|---|
| `background` | `#0B0B0C` | `#F7F7F5` | App canvas. True-ish black for OLED. |
| `surface` | `#141416` | `#FFFFFF` | Cards, sheets, list rows. |
| `surfaceVariant` | `#1D1D20` | `#ECECE8` | Meter troughs, progress tracks, inset wells. |
| `outline` | `#2C2C31` | `#D6D6D0` | Hairlines, dividers, card borders. |
| `onBackground` | `#F2F2EF` | `#17171A` | Primary text. Warm off-white, never pure `#FFF`. |
| `onSurfaceVariant` | `#9A9AA2` | `#5C5C63` | Secondary text, meta, labels. |
| `primary` (Ember) | `#E8853A` | `#B4551A` | Accent: armed state, active model, focus. |
| `error` (Signal) | `#E0483C` | `#C2352A` | Recording indicator, destructive actions. |
| `success` | `#4E9A6A` | `#2F6B45` | Installed / complete. |

**Ember `#E8853A`** is the record lamp on a tape deck — the accent is quoted from the subject's
own hardware, which is why it is amber rather than the default product-blue or acid-green.
Ember means *ready / selected*; Signal red means *capturing right now*. They are never
interchangeable, and only one accent appears per screen.

Contrast: `onBackground` on `background` is >13:1 in both themes; `onSurfaceVariant` clears
4.5:1 in both. Ember on dark `surface` clears 4.5:1 for text and 3:1 for the meter graphics.

## 4. Type

No bundled webfonts — the APK stays small and the app renders identically offline.

| Role | Family | Spec |
|---|---|---|
| Display | System sans (Roboto) | 34sp / w600 / `-0.5sp` tracking |
| Title | System sans | 20sp / w600 / `-0.2sp` |
| Body | System sans | 16sp / w400 / 1.5 line height |
| Label | System sans | 13sp / w500 |
| **Counter** | **`FontFamily.Monospace`** | tabular figures for time, size, % |

Body text never drops below 16sp. Line length is capped so long transcripts stay readable.

## 5. Layout & motion

- 8dp spacing rhythm; 16dp screen gutters; 24dp between sections.
- All touch targets ≥48dp, including icon-only controls, whose hit area is expanded beyond
  their visual bounds.
- Safe areas respected for status bar, nav bar and the Wear round display.
- Motion budget: 150–300ms for state changes, ease-out entering / ease-in leaving. The meter is
  exempt — it is continuous because the signal is continuous.
- `prefers-reduced-motion` (Android's *Remove animations*) collapses the meter to a static bar
  and disables all transitions.

## 6. Copy

Written from the user's side of the screen, in plain verbs.

- Buttons say what happens: **Record**, **Stop**, **Download**, **Delete**, **Use this model**.
- The name never changes between screens: a button that says *Download* produces a row that
  says *Downloaded*.
- Errors explain the cause and the fix, in the interface's voice, without apologising:
  *"Model not downloaded — pick one to get started."*
- Empty states invite the next action rather than reporting emptiness.

## 7. Non-negotiables

- No emoji anywhere in the UI. Icons are vectors from a single family.
- Colour is never the only signal — record state carries a label and a shape, not just red.
- Every control has an accessibility label; the meter is summarised for screen readers rather
  than exposed as noise.
- One primary action per screen.
