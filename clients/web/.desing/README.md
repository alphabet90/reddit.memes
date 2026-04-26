# OPENMEME Website UI Kit

## Overview
High-fidelity click-through prototype of the openmeme.com website. Dark theme, interactive navigation between 3 core screens.

## Screens
1. **Home** (`/`) — Hero section with search, filter pills, TOP memes grid, Últimos memes grid, sidebar with categories + Meme del Día
2. **Meme Detail** — Full meme view with image, share/save/comment actions, tags, related memes
3. **Upload** (`/subir`) — Upload form with drag-zone, title, category, tags

## Navigation
- Click any meme card → Meme Detail screen
- Click "SUBÍ TU MEME" button → Upload screen
- Click logo or "Volver" → Home screen
- Filter pills (TOP / NUEVOS / CLÁSICOS / RANDOM) are interactive

## Components Used
- `Nav` — top navigation bar with logo, links, search, CTA
- `HeroSection` — headline + search + filter pills + collage visual
- `MemeCard` — thumbnail card with rank badge or NUEVO badge, stats
- `MemeGrid` — 5-column responsive grid
- `Sidebar` — category list widget + Meme del Día widget
- `DetailView` — full meme detail with actions
- `UploadForm` — drag-and-drop upload zone + form fields

## Design Notes
- Design width: 1280px
- Fonts: Anton (display) + Space Grotesk (UI)
- Primary accent: #D4FF00 (Lima Meme)
- All assets self-contained; fonts loaded from ../../fonts/
