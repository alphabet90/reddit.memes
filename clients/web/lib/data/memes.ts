/**
 * Data layer for memes. Adapts the API v2 contract (lib/api.ts) into
 * the UI's `Meme` shape (lib/types.ts). All functions are server
 * helpers and never run in the browser bundle.
 */

import type { Meme, MemeListing } from "@/lib/types";
import {
  cdnUrl,
  fetchMeme,
  fetchMemes,
  fetchMemesByCategory,
  searchMemes,
  type ApiMeme,
  type ApiMemeImage,
  type ApiMemeTranslation,
  type ApiMemePage,
  type ApiSearchResult,
  type LocaleCode,
  type SortKey,
} from "@/lib/api";

/** Two-week window for the "Nuevo" badge. */
const NEW_WINDOW_MS = 1000 * 60 * 60 * 24 * 14;

/** Stable per-id index into `placeholderTiles` — avoids hydration drift. */
function hash(id: string): number {
  let h = 0;
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0;
  return h;
}

/** Visual fallback tiles when an image fails to load or is missing. */
const placeholderTiles: Array<{ glyph: string; gradient: string }> = [
  { glyph: "🐕", gradient: "linear-gradient(135deg,#3a2510,#5a3510)" },
  { glyph: "🎭", gradient: "linear-gradient(135deg,#1a1a3a,#2a2a5a)" },
  { glyph: "🐱", gradient: "linear-gradient(135deg,#1a3020,#204a30)" },
  { glyph: "🐧", gradient: "linear-gradient(135deg,#2a1a3a,#3a2a5a)" },
  { glyph: "🎬", gradient: "linear-gradient(135deg,#3a2a1a,#5a4020)" },
  { glyph: "🐭", gradient: "linear-gradient(135deg,#0a1a2a,#0a2030)" },
  { glyph: "🦊", gradient: "linear-gradient(135deg,#2a1010,#401820)" },
  { glyph: "🐩", gradient: "linear-gradient(135deg,#1a0a1a,#2a1030)" },
];

function inferFormat(imagePath: string | undefined | null): Meme["format"] {
  if (!imagePath) return "jpg";
  const ext = imagePath.toLowerCase().split(".").pop();
  if (ext === "png" || ext === "gif" || ext === "webp") return ext;
  return "jpg";
}

function getTranslation(
  translations: ApiMemeTranslation[],
  preferLocale: LocaleCode = "en",
): ApiMemeTranslation | undefined {
  return translations.find((t) => t.locale === preferLocale) ?? translations[0];
}

function getPrimaryImage(images: ApiMemeImage[]): ApiMemeImage | undefined {
  return images.find((i) => i.is_primary) ?? images[0];
}

function makePlaceholder(id: string) {
  return placeholderTiles[hash(id) % placeholderTiles.length];
}

/** Adapt a single V2 API record into the UI shape. */
export function toMeme(api: ApiMeme, locale: LocaleCode = "en"): Meme {
  const id = `${api.category}/${api.slug}`;
  const tile = makePlaceholder(id);
  const translation = getTranslation(api.translations, locale);
  const primaryImage = getPrimaryImage(api.images);
  const createdAt = api.created_at ?? new Date(0).toISOString();
  const isNew =
    api.created_at != null &&
    Date.now() - new Date(api.created_at).getTime() < NEW_WINDOW_MS;

  return {
    id,
    slug: api.slug,
    category: api.category,
    title: translation?.title ?? api.slug,
    description: translation?.description ?? undefined,
    author: api.author ?? undefined,
    subreddit: api.subreddit ?? undefined,
    score: api.score ?? 0,
    tags: api.tags ?? [],
    imageUrl: cdnUrl(primaryImage?.path) ?? "",
    href: `/memes/${api.category}/${api.slug}`,
    format: inferFormat(primaryImage?.path),
    createdAt,
    postUrl: api.post_url ?? undefined,
    sourceUrl: api.source_url ?? undefined,
    placeholder: tile.glyph,
    placeholderGradient: tile.gradient,
    isNew,
  };
}

/** Adapt a V2 SearchResult (flat shape) into the UI Meme shape. */
export function toMemeFromSearchResult(api: ApiSearchResult): Meme {
  const id = `${api.category}/${api.slug}`;
  const tile = makePlaceholder(id);

  return {
    id,
    slug: api.slug,
    category: api.category,
    title: api.title,
    description: api.description ?? undefined,
    author: api.author ?? undefined,
    subreddit: undefined,
    score: api.score,
    tags: api.tags,
    imageUrl: cdnUrl(api.image_path) ?? "",
    href: `/memes/${api.category}/${api.slug}`,
    format: inferFormat(api.image_path),
    createdAt: new Date(0).toISOString(),
    placeholder: tile.glyph,
    placeholderGradient: tile.gradient,
  };
}

function toListing(page: ApiMemePage, locale: LocaleCode = "en"): MemeListing {
  return {
    data: page.data.map((m) => toMeme(m, locale)),
    pageInfo: {
      page: page.page,
      limit: page.limit,
      total: page.total,
      totalPages: page.total_pages,
    },
  };
}

/* ───────────────────────────── Public API ───────────────────────────── */

export async function getTopMemes(limit = 5, locale: LocaleCode = "en"): Promise<Meme[]> {
  const page = await fetchMemes({ limit, sort: "score", locale });
  return page.data.map((m) => toMeme(m, locale));
}

export async function getPopularMemes(limit = 12, locale: LocaleCode = "en"): Promise<Meme[]> {
  const page = await fetchMemes({ limit, sort: "score", page: 1, locale });
  return page.data.map((m) => toMeme(m, locale));
}

export async function getRecentMemes(limit = 12, locale: LocaleCode = "en"): Promise<Meme[]> {
  const page = await fetchMemes({ limit, sort: "created_at", locale });
  return page.data.map((m) => toMeme(m, locale));
}

export async function getMemeListing(args: {
  page?: number;
  limit?: number;
  sort?: SortKey;
  category?: string;
  locale?: LocaleCode;
}): Promise<MemeListing> {
  const locale = args.locale ?? "en";
  return toListing(await fetchMemes(args), locale);
}

export async function getCategoryListing(
  category: string,
  args: { page?: number; limit?: number; sort?: SortKey; locale?: LocaleCode } = {},
): Promise<MemeListing | null> {
  const locale = args.locale ?? "en";
  const page = await fetchMemesByCategory(category, args);
  return page ? toListing(page, locale) : null;
}

export async function getMeme(
  category: string,
  slug: string,
  locale: LocaleCode = "en",
): Promise<Meme | null> {
  const api = await fetchMeme(category, slug, locale);
  return api ? toMeme(api, locale) : null;
}

/**
 * Search memes and return a MemeListing-compatible shape.
 * V2 /search returns a flat array with no total count — `totalPages` is
 * estimated: if we got a full page of results there is likely a next page.
 */
export async function searchListing(args: {
  q: string;
  page?: number;
  limit?: number;
  locale?: LocaleCode;
}): Promise<MemeListing> {
  const limit = args.limit ?? 20;
  const page = args.page ?? 0;
  const results = await searchMemes({ ...args, limit, page });
  const hasMore = results.length === limit;

  return {
    data: results.map(toMemeFromSearchResult),
    pageInfo: {
      page,
      limit,
      total: hasMore ? (page + 2) * limit : page * limit + results.length,
      totalPages: hasMore ? page + 2 : page + 1,
    },
  };
}
