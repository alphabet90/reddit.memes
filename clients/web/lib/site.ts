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
  /** English fallback tagline — per-locale values live in messages/*.json */
  tagline: "All the memes. In one place.",
  /** English fallback description — per-locale values live in messages/*.json */
  description:
    "The largest community meme repository. Thousands of memes in JPG/PNG, uploaded by the people for the people. Search, download and share the best memes.",
  twitter: "@openmeme",
  ogImage: "/og-default.png",
  apiBaseUrl,
  cdnBaseUrl,
} as const;

export type Site = typeof site;
