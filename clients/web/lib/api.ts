/**
 * Typed client for the OpenMEME memes API.
 *
 * Contract: https://api-production-681e.up.railway.app  (OpenAPI 3.0.3)
 * Server-side fetches use Next.js' built-in `cache` + `revalidate`
 * options for ISR — no third-party SWR layer needed for SSR/SSG.
 *
 * Image URLs are constructed via {@link cdnUrl}, never returned as
 * absolute URLs by the API; the API ships only `image_path`.
 */

import { site } from "@/lib/site";

/* ───────────────────────── API contract types ───────────────────────── */

export interface ApiStats {
  total_memes: number;
  total_categories: number;
  total_subreddits: number;
  top_category: string;
  indexed_at: string;
}

export interface ApiCategorySummary {
  category: string;
  count: number;
  top_score: number;
}

export interface ApiMeme {
  slug: string;
  category: string;
  title: string;
  description?: string;
  author?: string;
  subreddit?: string;
  score?: number;
  created_at?: string;
  source_url?: string;
  post_url?: string;
  image_path?: string;
  tags?: string[];
}

export interface ApiMemePage {
  data: ApiMeme[];
  page: number;
  limit: number;
  total: number;
  total_pages: number;
}

export type SortKey = "score" | "created_at" | "title";

/* ──────────────────────────── Configuration ─────────────────────────── */

/** Default ISR window — 5 min. Matches the home page's `revalidate`. */
const DEFAULT_REVALIDATE = 300;

/** Encode a path-ish value, leaving forward slashes alone. */
function encodePathPart(p: string): string {
  return encodeURIComponent(p).replace(/%2F/gi, "/");
}

/* ───────────────────────────── Low-level fetch ──────────────────────── */

class ApiError extends Error {
  constructor(public status: number, public path: string, message: string) {
    super(message);
    this.name = "ApiError";
  }
}

interface FetchOptions {
  /** Override ISR window. Use 0 for no caching. */
  revalidate?: number | false;
  /** Cache tags — useful for `revalidateTag` from admin paths. */
  tags?: string[];
  /** Suppress 404 → throw; return null instead. */
  allow404?: boolean;
}

async function apiGet<T>(
  path: string,
  query: Record<string, string | number | undefined> = {},
  opts: FetchOptions = {},
): Promise<T> {
  const url = new URL(path.replace(/^\//, ""), site.apiBaseUrl + "/");
  for (const [k, v] of Object.entries(query)) {
    if (v === undefined || v === "" || Number.isNaN(v)) continue;
    url.searchParams.set(k, String(v));
  }

  const res = await fetch(url, {
    next: {
      revalidate: opts.revalidate ?? DEFAULT_REVALIDATE,
      tags: opts.tags,
    },
    headers: { Accept: "application/json" },
  });

  if (!res.ok) {
    if (res.status === 404 && opts.allow404) {
      // Caller wants null on miss; surface a sentinel.
      throw new ApiError(404, url.pathname, "Not found");
    }
    throw new ApiError(
      res.status,
      url.pathname,
      `API ${res.status} for ${url.pathname}`,
    );
  }

  return res.json() as Promise<T>;
}

/* ──────────────────────────── Public helpers ────────────────────────── */

/**
 * Build a public CDN URL for an image_path returned by the API.
 * The API ships paths like `memes/argentina-reaction/foo.jpg`; the
 * worker serves them at the CDN root.
 */
export function cdnUrl(imagePath: string | undefined | null): string | null {
  if (!imagePath) return null;
  // Strip a leading slash if the API ever sends one.
  const cleaned = imagePath.replace(/^\/+/, "");
  return `${site.cdnBaseUrl}/${encodePathPart(cleaned)}`;
}

/** GET / — global stats. Cached for 1 hour. */
export function fetchStats(): Promise<ApiStats> {
  return apiGet<ApiStats>("/", {}, { revalidate: 3600, tags: ["stats"] });
}

/** GET /categories — full taxonomy with counts. Cached for 1 hour. */
export function fetchCategories(): Promise<ApiCategorySummary[]> {
  return apiGet<ApiCategorySummary[]>(
    "/categories",
    {},
    { revalidate: 3600, tags: ["categories"] },
  );
}

interface ListMemesArgs {
  page?: number;
  limit?: number;
  category?: string;
  subreddit?: string;
  sort?: SortKey;
}

/** GET /memes — paginated, filterable, sortable. */
export function fetchMemes(args: ListMemesArgs = {}): Promise<ApiMemePage> {
  return apiGet<ApiMemePage>("/memes", {
    page: args.page,
    limit: args.limit,
    category: args.category,
    subreddit: args.subreddit,
    sort: args.sort,
  });
}

interface ListByCategoryArgs {
  page?: number;
  limit?: number;
  sort?: SortKey;
}

/** GET /memes/{category} — null if the category does not exist. */
export async function fetchMemesByCategory(
  category: string,
  args: ListByCategoryArgs = {},
): Promise<ApiMemePage | null> {
  try {
    return await apiGet<ApiMemePage>(
      `/memes/${encodePathPart(category)}`,
      { page: args.page, limit: args.limit, sort: args.sort },
      { allow404: true },
    );
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) return null;
    throw err;
  }
}

/** GET /memes/{category}/{slug} — null when not found. */
export async function fetchMeme(
  category: string,
  slug: string,
): Promise<ApiMeme | null> {
  try {
    return await apiGet<ApiMeme>(
      `/memes/${encodePathPart(category)}/${encodePathPart(slug)}`,
      {},
      { allow404: true },
    );
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) return null;
    throw err;
  }
}

interface SearchArgs {
  q: string;
  page?: number;
  limit?: number;
}

/** GET /search — empty page when q is empty (avoids the 400). */
export async function searchMemes(args: SearchArgs): Promise<ApiMemePage> {
  const q = args.q.trim();
  if (!q) {
    return { data: [], page: 0, limit: args.limit ?? 20, total: 0, total_pages: 0 };
  }
  return apiGet<ApiMemePage>(
    "/search",
    { q, page: args.page, limit: args.limit },
    { revalidate: 60 },
  );
}
