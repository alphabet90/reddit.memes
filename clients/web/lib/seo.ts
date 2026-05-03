import { site } from "@/lib/site";
import { localeLangMap, type Locale } from "@/i18n/routing";
import type { Category, Meme } from "@/lib/types";

/**
 * SEO helpers — produce JSON-LD documents consumed by search engines
 * and rich-result cards. We render these inside a <script
 * type="application/ld+json"> tag in Server Components.
 */

export function websiteJsonLd(
  locale: Locale = "en",
  strings: { tagline: string; description: string } = {
    tagline: site.tagline,
    description: site.description,
  },
) {
  const lang = localeLangMap[locale];
  return {
    "@context": "https://schema.org",
    "@type": "WebSite",
    name: site.name,
    alternateName: site.legalName,
    url: site.url,
    inLanguage: lang,
    description: strings.description,
    potentialAction: {
      "@type": "SearchAction",
      target: `${site.url}/${locale}/buscar?q={search_term_string}`,
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

export function memeItemListJsonLd(
  memes: Meme[],
  locale: Locale = "en",
  name = `Top memes — ${site.name}`,
) {
  const lang = localeLangMap[locale];
  return {
    "@context": "https://schema.org",
    "@type": "ItemList",
    name,
    inLanguage: lang,
    numberOfItems: memes.length,
    itemListElement: memes.map((m, i) => ({
      "@type": "ListItem",
      position: i + 1,
      url: `${site.url}/${locale}${m.href}`,
      name: m.title,
      image: m.imageUrl || undefined,
    })),
  };
}

/**
 * ImageObject for a single meme — gives Google enough to surface
 * the meme as an Image result with the OpenMEME page as the host.
 */
export function memeImageObjectJsonLd(meme: Meme, locale: Locale = "en") {
  const lang = localeLangMap[locale];
  const pageUrl = `${site.url}/${locale}${meme.href}`;
  return {
    "@context": "https://schema.org",
    "@type": "ImageObject",
    name: meme.title,
    description: meme.description ?? meme.title,
    contentUrl: meme.imageUrl || undefined,
    thumbnailUrl: meme.imageUrl || undefined,
    license: `${site.url}/terminos`,
    acquireLicensePage: `${site.url}/terminos`,
    creditText: meme.author ? `u/${meme.author}` : site.legalName,
    creator: meme.author
      ? { "@type": "Person", name: `u/${meme.author}` }
      : { "@type": "Organization", name: site.legalName },
    copyrightNotice: meme.author ? `© u/${meme.author}` : undefined,
    keywords: meme.tags.join(", "),
    uploadDate: meme.createdAt,
    inLanguage: lang,
    representativeOfPage: true,
    isAccessibleForFree: true,
    url: pageUrl,
    mainEntityOfPage: pageUrl,
  };
}

export function breadcrumbJsonLd(
  items: { name: string; href: string }[],
  locale: Locale = "en",
) {
  return {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: items.map((it, i) => ({
      "@type": "ListItem",
      position: i + 1,
      name: it.name,
      item: `${site.url}/${locale}${it.href}`,
    })),
  };
}

export function categoryCollectionJsonLd(
  category: Category,
  memes: Meme[],
  locale: Locale = "en",
  strings?: { name: string; description: string },
) {
  const lang = localeLangMap[locale];
  const displayName = strings?.name ?? category.name;
  const description =
    strings?.description ??
    `${displayName} memes — ${category.count.toLocaleString()} results.`;

  return {
    "@context": "https://schema.org",
    "@type": "CollectionPage",
    name: displayName,
    description,
    url: `${site.url}/${locale}/categorias/${category.slug}`,
    inLanguage: lang,
    hasPart: memes.slice(0, 20).map((m) => ({
      "@type": "ImageObject",
      name: m.title,
      url: `${site.url}/${locale}${m.href}`,
      contentUrl: m.imageUrl || undefined,
    })),
  };
}
