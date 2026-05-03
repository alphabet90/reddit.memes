import { defineRouting } from "next-intl/routing";

export const locales = ["en", "es", "es-AR", "pt", "fr", "de", "ar"] as const;
export type Locale = (typeof locales)[number];
export const defaultLocale: Locale = "es-AR";

/** BCP-47 language tag for each locale (used in <html lang>). */
export const localeLangMap: Record<Locale, string> = {
  en: "en",
  es: "es",
  "es-AR": "es-AR",
  pt: "pt",
  fr: "fr",
  de: "de",
  ar: "ar",
};

/** OpenGraph locale format (underscore-separated IETF tags). */
export const localeOgMap: Record<Locale, string> = {
  en: "en_US",
  es: "es_ES",
  "es-AR": "es_AR",
  pt: "pt_BR",
  fr: "fr_FR",
  de: "de_DE",
  ar: "ar_SA",
};

/** Locales that require right-to-left text direction. */
export const rtlLocales: Locale[] = ["ar"];

export const routing = defineRouting({
  locales,
  defaultLocale,
  localePrefix: "always",
});
