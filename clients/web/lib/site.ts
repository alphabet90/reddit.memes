/**
 * Single source of truth for site-level metadata.
 * Kept tiny and string-only so it can be consumed from both
 * Server Components and edge runtimes (sitemap, robots).
 */

/**
 * API + CDN endpoints. Environment overrides:
 *   NEXT_PUBLIC_MEMES_API_URL   → backend base, no trailing slash
 *   NEXT_PUBLIC_MEMES_CDN_URL   → image CDN base, no trailing slash
 *
 * Defaults match the production deployment so a fresh checkout
 * builds without extra env wiring.
 */
const apiBaseUrl =
  process.env.NEXT_PUBLIC_MEMES_API_URL?.replace(/\/+$/, "") ||
  "https://api-production-681e.up.railway.app";

const cdnBaseUrl =
  process.env.NEXT_PUBLIC_MEMES_CDN_URL?.replace(/\/+$/, "") ||
  "https://cdn-openmeme.clientes-g4a.workers.dev";

export const site = {
  name: "OPENMEME",
  legalName: "OpenMeme",
  domain: "openmeme.com",
  url: "https://openmeme.com",
  locale: "es_AR",
  language: "es-AR",
  tagline: "Todos los memes. En un solo lugar.",
  description:
    "El repositorio de memes más grande de Argentina. Miles de memes argentinos en JPG/PNG, subidos por la gente para la gente. Buscá, descargá y compartí los memes del momento.",
  keywords: [
    "memes argentinos",
    "memes argentina",
    "repositorio memes",
    "memes gratis",
    "openmeme",
    "memes en español",
    "memes jpg",
    "memes png",
    "memes milei",
    "memes maradona",
    "memes futbol",
    "memes politica",
  ],
  twitter: "@openmeme",
  ogImage: "/og-default.png",
  apiBaseUrl,
  cdnBaseUrl,
} as const;

export type Site = typeof site;
