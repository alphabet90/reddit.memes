import { site } from "@/lib/site";
import type { Category, Meme } from "@/lib/types";

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

export function memeItemListJsonLd(memes: Meme[], name = `Top memes — ${site.name}`) {
  return {
    "@context": "https://schema.org",
    "@type": "ItemList",
    name,
    numberOfItems: memes.length,
    itemListElement: memes.map((m, i) => ({
      "@type": "ListItem",
      position: i + 1,
      url: `${site.url}${m.href}`,
      name: m.title,
      image: m.imageUrl || undefined,
    })),
  };
}

/**
 * ImageObject for a single meme — gives Google enough to surface
 * the meme as an Image result with the OpenMEME page as the host.
 */
export function memeImageObjectJsonLd(meme: Meme) {
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
    inLanguage: site.language,
    representativeOfPage: true,
    isAccessibleForFree: true,
    url: `${site.url}${meme.href}`,
    mainEntityOfPage: `${site.url}${meme.href}`,
  };
}

export function breadcrumbJsonLd(items: { name: string; href: string }[]) {
  return {
    "@context": "https://schema.org",
    "@type": "BreadcrumbList",
    itemListElement: items.map((it, i) => ({
      "@type": "ListItem",
      position: i + 1,
      name: it.name,
      item: `${site.url}${it.href}`,
    })),
  };
}

export function categoryCollectionJsonLd(category: Category, memes: Meme[]) {
  return {
    "@context": "https://schema.org",
    "@type": "CollectionPage",
    name: `Memes de ${category.name}`,
    description: `Memes argentinos en la categoría ${category.name} — ${category.count.toLocaleString("es-AR")} resultados.`,
    url: `${site.url}/categorias/${category.slug}`,
    inLanguage: site.language,
    hasPart: memes.slice(0, 20).map((m) => ({
      "@type": "ImageObject",
      name: m.title,
      url: `${site.url}${m.href}`,
      contentUrl: m.imageUrl || undefined,
    })),
  };
}
