import { locales, localeLangMap, type Locale } from "@/i18n/routing";
import { site } from "@/lib/site";

/** Prefix a bare path with locale segment: /memes/foo → /en/memes/foo */
export function localePath(locale: Locale, path: string): string {
  const normalized = path.startsWith("/") ? path : `/${path}`;
  return `/${locale}${normalized}`;
}

/** Full canonical URL for a locale-prefixed path */
export function localeUrl(locale: Locale, path: string): string {
  return `${site.url}${localePath(locale, path)}`;
}

/**
 * Build the hreflang alternates object consumed by Next.js `alternates.languages`.
 * Next.js auto-emits <link rel="alternate" hreflang="..."> from this.
 */
export function buildAlternates(path: string): Record<string, string> {
  const normalized = path.startsWith("/") ? path : `/${path}`;
  const languages: Record<string, string> = { "x-default": `/en${normalized}` };
  for (const l of locales) {
    languages[localeLangMap[l]] = `/${l}${normalized}`;
  }
  return languages;
}
