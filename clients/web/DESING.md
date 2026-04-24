# OPENMEME Design System

## Overview

**OPENMEME** (openmeme.com) is Argentina's largest meme repository — a community-powered platform hosting 20,000+ memes in JPG/PNG format. It competes directly with Tenor and Giphy but is tailored entirely to Argentine internet culture.

The brand is bold, irreverent, and deeply local. Think internet-native chaos with a strong visual identity: neon lime on jet black, uppercase display type, cut-out collage aesthetics, and writing that sounds like your most online friend.

## CONTENT FUNDAMENTALS

### Voice & Tone
- **Habla como la gente** — Writes the way Argentines actually talk. No corporate speak, no filters.
- **Con humor** — Everything has a comedic angle. The brand doesn't take itself seriously.
- **Un toque de ironía** — Dry irony is the default mode. Understatement is funnier than overselling.
- **Sin caretas** — Authentic, direct, no-BS. The brand says what it means.
- **Comunitario** — Celebrates the community; the platform is "subido por la gente, para la gente."

### Language Rules
- **Language:** Spanish (Argentine variant). Uses **vos** not tú. ("Dale, vos podés", "Subí tu meme")
- **Casing:** ALL CAPS for headlines and display text (Anton font). Sentence case or all-caps for UI labels.
- **Punctuation:** Inverted question/exclamation marks used (¿ ¡). Not optional — it's Argentine Spanish.
- **Tone markers:** Uses "JAJAJA" as a standalone expression. Phrases like "exquisito", "argento", "dale".
- **Emoji:** Not part of the core brand. The one exception is 🔥 for "TOP" / trending — matches the fire icon used in UI.
- **Tagline:** *"Todos los memes. En un solo lugar."* — always rendered in display type.
- **Sub-tagline:** *"El repositorio de memes más grande de Argentina. Subido por la gente. Para la gente."*

### Copy Examples
- "¿Qué mirá bobo?" (meme overlay)
- "La tenés adentro" (badge)
- "Sin memes no hay paraíso" (badge)
- "Hecho en Argentina. Subido por la gente. Para la gente."
- "Todo meme. Todo acá." / "Guardá. Compartí." / "Reí. Repetí." / "100% Argento. 100% Real."
- "Yo los vi" (badge)
- "Subí tu meme, pasá a la historia."

---

## VISUAL FOUNDATIONS

### Colors
| Name | Hex | Usage |
|---|---|---|
| Negro | `#0D0D0D` | Primary background |
| Lima Meme | `#D4FF00` | Primary accent, CTAs, highlights |
| Blanco | `#FFFFFF` | Primary text, logo on dark |
| Celeste Argento | `#74C6F4` | Secondary accent (Argentine flag reference) |
| Gris Humo | `#A7A7A7` | Secondary text, muted UI |

**Color vibe:** High contrast dark mode. Neon lime pops against near-black backgrounds. Celeste Argento is used sparingly as a patriotic nod. No pastels, no gradients in the core palette.

### Typography
- **Display / Logo:** Anton (local TTF). All caps, bold geometric. Used for all headlines, hero text, logo wordmark.
- **UI / Body:** Space Grotesk (Google Fonts). Clean, modern grotesque. Used for all body copy, labels, navigation, captions.
- **Substitution note:** Space Grotesk is the specified brand font. Anton is bundled.

### Backgrounds & Surfaces
- Base: Solid `#0D0D0D` — no gradients, no textures on the background itself.
- Cards / surfaces: `#1A1A1A` (slightly lighter black)
- Borders / dividers: `#2C2C2C`
- The hero uses **collage-style cut-out photography** (transparent-bg PNG characters placed over black) — not a gradient or illustration.

### Imagery Style
- **Cut-out collage:** Characters, celebrities, animals with removed backgrounds placed directly on black. Key Argentine cultural references (Maradona, Milei, local celebrities, dogs).
- **Warm + chaotic:** High contrast, meme-template JPGs/PNGs. No color grading or consistency needed — that's the point.
- **Sticker badges:** Neon lime pill-shaped labels with black text ("NUEVO", "TOP", "CLÁSICO"), plus hand-drawn arrow/underline decorations.
- **Hand-drawn accents:** Rough sketch arrows, underlines, and scribbles layered over content. Creates a "torn from the internet" feel.

### Icons
- Custom outlined icon set: crown (TOP/ranking), bookmark (save), share arrow, magnifier, fire (trending), smiley, cloud-upload, hamburger menu.
- Stroke style: clean, medium weight, outlined (not filled). Matches Space Grotesk weight.
- No external icon library specified; icons appear custom/SVG.

### Corner Radii
- Cards: `8px` (subtle, not bubbly)
- Buttons / pills: `999px` (full pill) or `6px` (rectangular)
- Badges: `4px` to `999px` depending on style
- Images in cards: `8px`

### Shadows & Elevation
- Card shadow: `0 2px 8px rgba(0,0,0,0.5)` — subtle on dark backgrounds
- Lime glow: `0 0 16px rgba(212,255,0,0.35)` — used on active/hovered accent elements
- No heavy drop shadows; elevation is communicated via background color differences

### Animation & Interaction
- **Hover states:** Buttons slightly darken (`#C6F000` on lime). Cards scale subtly (`scale(1.02)`). Image thumbnails may brighten.
- **Press states:** Small scale-down (`scale(0.97)`) on buttons.
- **Transitions:** Fast and snappy — 120–200ms. `cubic-bezier(0.16, 1, 0.3, 1)` ease-out. No slow fades.
- **Bounce:** Used sparingly on badges/popups — `cubic-bezier(0.34, 1.56, 0.64, 1)`.
- No parallax, no looping animations. The content (memes) is the entertainment.

### Layout
- **Max content width:** ~1280px centered
- **Grid:** Meme grid is 5-column on desktop, responsive down to 2-col mobile
- **Sidebar:** Right sidebar (~280px) for categories, "Meme del día"
- **Nav:** Fixed top nav bar, black, with logo left + links center + search + CTA right
- **Spacing:** 4px base unit, 8/12/16/24/32/48px scale

### Buttons
- **Primary CTA:** Lime (`#D4FF00`) background, black text, pill or slight radius, bold weight, uppercase. Icon optional (upload arrow).
- **Secondary / Tab pills:** Dark border (`#2C2C2C`) background, white text, pill shape. Active state: lime bg.
- **Ghost:** White outline, transparent bg.

### Cards (Meme cards)
- Dark surface `#1A1A1A`, 8px radius
- Image fills top portion
- Caption text in white (Space Grotesk)
- Rank badge (number) top-left, yellow/numbered
- "NUEVO" badge top-left on new items (lime bg, black text)
- Stats row: views + shares in gray
- Hover: slight scale + image brightness

---

## ICONOGRAPHY
No external icon library (Lucide, Heroicons, etc.) is used. MEMEARg uses a **custom outlined icon set** visible in the brand kit, including:
- 👑 Crown (TOP/ranking) — custom SVG
- 🔖 Bookmark — custom SVG  
- ↗ Share arrow — custom SVG
- 🔍 Magnifier (search) — custom SVG
- 🔥 Fire (trending/nuevos) — custom SVG
- 🙂 Smiley (clásicos) — custom SVG
- ☁ Cloud upload — custom SVG
- ≡ Hamburger menu — custom SVG

**Usage rules:**
- Icons appear in white or lime on dark backgrounds
- Stroke weight matches Space Grotesk (medium)
- Icons are paired with text labels in navigation
- No emoji in UI chrome; emoji (🔥) only used in specific "TOP" section headings to match meme culture
- Hand-drawn sketch arrows/underlines are used as **decorative brand elements** (not icons) in hero sections and badges

---