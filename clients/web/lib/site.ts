/**
 * Single source of truth for site-level metadata.
 * Kept tiny and string-only so it can be consumed from both
 * Server Components and edge runtimes (sitemap, robots).
 */

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
} as const;

export type Site = typeof site;
