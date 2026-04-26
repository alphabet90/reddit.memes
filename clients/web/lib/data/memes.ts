/**
 * Data layer for memes. Adapts the API contract (lib/api.ts) into
 * the UI's `Meme` shape (lib/types.ts). All functions are server
 * helpers and never run in the browser bundle.
 */

import type { Meme, MemeListing } from "@/lib/types";
import {
  cdnUrl,
  fetchMeme,
  fetchMemes,
  fetchMemesByCategory,
  type ApiMeme,
  type ApiMemePage,
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

function inferFormat(imagePath: string | undefined): Meme["format"] {
  if (!imagePath) return "jpg";
  const ext = imagePath.toLowerCase().split(".").pop();
  if (ext === "png" || ext === "gif" || ext === "webp") return ext;
  return "jpg";
}

/** Adapt a single API record into the UI shape. */
export function toMeme(api: ApiMeme): Meme {
  const id = `${api.category}/${api.slug}`;
  const tile = placeholderTiles[hash(id) % placeholderTiles.length];
  const createdAt = api.created_at ?? new Date(0).toISOString();
  const isNew =
    api.created_at !== undefined &&
    Date.now() - new Date(api.created_at).getTime() < NEW_WINDOW_MS;

  return {
    id,
    slug: api.slug,
    category: api.category,
    title: api.title,
    description: api.description,
    author: api.author,
    subreddit: api.subreddit,
    score: api.score ?? 0,
    tags: api.tags ?? [],
    imageUrl: cdnUrl(api.image_path) ?? "",
    href: `/memes/${api.category}/${api.slug}`,
    format: inferFormat(api.image_path),
    createdAt,
    postUrl: api.post_url,
    sourceUrl: api.source_url,
    placeholder: tile.glyph,
    placeholderGradient: tile.gradient,
    isNew,
  };
}

function toListing(page: ApiMemePage): MemeListing {
  return {
    data: page.data.map(toMeme),
    pageInfo: {
      page: page.page,
      limit: page.limit,
      total: page.total,
      totalPages: page.total_pages,
    },
  };
}

/* ───────────────────────────── Public API ───────────────────────────── */

export async function getTopMemes(limit = 5): Promise<Meme[]> {
  const page = await fetchMemes({ limit, sort: "score" });
  return page.data.map(toMeme);
}

export async function getPopularMemes(limit = 12): Promise<Meme[]> {
  const page = await fetchMemes({ limit, sort: "score", page: 1 });
  return page.data.map(toMeme);
}

export async function getRecentMemes(limit = 12): Promise<Meme[]> {
  const page = await fetchMemes({ limit, sort: "created_at" });
  return page.data.map(toMeme);
}

export async function getMemeListing(args: {
  page?: number;
  limit?: number;
  sort?: SortKey;
  category?: string;
}): Promise<MemeListing> {
  return toListing(await fetchMemes(args));
}

export async function getCategoryListing(
  category: string,
  args: { page?: number; limit?: number; sort?: SortKey } = {},
): Promise<MemeListing | null> {
  const page = await fetchMemesByCategory(category, args);
  return page ? toListing(page) : null;
}

export async function getMeme(category: string, slug: string): Promise<Meme | null> {
  const api = await fetchMeme(category, slug);
  return api ? toMeme(api) : null;
}
