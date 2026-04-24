import { site } from "@/lib/site";
import type { Meme } from "@/lib/types";

/**
 * SEO helpers — produce JSON-LD documents consumed by search engines
 * and rich-result cards. We render these inside a <script
 * type="application/ld+json"> tag in Server Components.
 */

export function websiteJsonLd() {
  return {
    "@context": "https://schema.org",
    "@type": "WebSite",
    name: site.name,
    alternateName: site.legalName,
    url: site.url,
    inLanguage: site.language,
    description: site.description,
    potentialAction: {
      "@type": "SearchAction",
      target: `${site.url}/buscar?q={search_term_string}`,
      "query-input": "required name=search_term_string",
    },
  };
}

export function organizationJsonLd() {
  return {
    "@context": "https://schema.org",
    "@type": "Organization",
    name: site.name,
    url: site.url,
    logo: `${site.url}/favicon.ico`,
  };
}

export function memeItemListJsonLd(memes: Meme[]) {
  return {
    "@context": "https://schema.org",
    "@type": "ItemList",
    name: `Top memes — ${site.name}`,
    numberOfItems: memes.length,
    itemListElement: memes.map((m, i) => ({
      "@type": "ListItem",
      position: i + 1,
      url: `${site.url}/memes/${m.slug}`,
      name: m.title,
    })),
  };
}
