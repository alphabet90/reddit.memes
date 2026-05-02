/**
 * Typed client for the OpenMEME memes API v2.
 *
 * Contract: https://api-production-681e.up.railway.app  (OpenAPI 3.0.3 v2)
 * Server-side fetches use Next.js' built-in `cache` + `revalidate`
 * options for ISR — no third-party SWR layer needed for SSR/SSG.
 *
 * V2: multilingual schema. Every read endpoint accepts `?locale=`.
 * `Meme` carries `translations[]` (per-locale title/description) and
 * `images[]` (multi-image support, `is_primary` marks the lead image).
 * Image URLs are constructed via {@link cdnUrl} from `path` fields.
 */

import { site } from "@/lib/site";

/* ───────────────────────── API contract types ───────────────────────── */

export type LocaleCode = "en" | "es" | "pt" | "fr" | "de" | "ar";

export interface ApiStats {
  total_memes: number;
  total_categories: number;
  total_subreddits: number;
  top_category: string;
  indexed_at: string;
}

export interface ApiCategoryTranslation {
  locale: LocaleCode;
  name: string;
  description?: string | null;
}

export interface ApiCategorySummary {
  category: string;
  count: number;
  top_score: number;
  translations?: ApiCategoryTranslation[];
}

export interface ApiCategoryPage {
  data: ApiCategorySummary[];
  page: number;
  limit: number;
  total: number;
  total_pages: number;
}

export interface ApiMemeTranslation {
  locale: LocaleCode;
  title: string;
  description?: string | null;
}

export interface ApiMemeImage {
  path: string;
  width?: number | null;
  height?: number | null;
  bytes?: number | null;
  mime_type?: string | null;
  position: number;
  is_primary: boolean;
}

export interface ApiMeme {
  slug: string;
  category: string;
  default_locale: LocaleCode;
  author?: string | null;
  subreddit?: string | null;
  score?: number;
  created_at?: string | null;
  source_url?: string | null;
  post_url?: string | null;
  translations: ApiMemeTranslation[];
  images: ApiMemeImage[];
  tags?: string[];
}

export interface ApiMemePage {
  data: ApiMeme[];
  page: number;
  limit: number;
  total: number;
  total_pages: number;
}

export interface ApiSearchResult {
  slug: string;
  category: string;
  author?: string | null;
  score: number;
  title: string;
  description?: string | null;
  image_path?: string | null;
  tags: string[];
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
 * Build a public CDN URL for an image path returned by the API.
 * The API ships paths like `memes/argentina-reaction/foo.jpg`; the
 * worker serves them at the CDN root.
 */
export function cdnUrl(imagePath: string | undefined | null): string | null {
  if (!imagePath) return null;
  const cleaned = imagePath.replace(/^\/+/, "");
  return `${site.cdnBaseUrl}/${encodePathPart(cleaned)}`;
}

/** GET / — global stats. Cached for 1 hour. */
export function fetchStats(): Promise<ApiStats> {
  return apiGet<ApiStats>("/", {}, { revalidate: 3600, tags: ["stats"] });
}

interface ListCategoriesArgs {
  page?: number;
  limit?: number;
  locale?: LocaleCode;
}

/** GET /categories — paginated taxonomy with counts. Cached for 1 hour. */
export function fetchCategories(args: ListCategoriesArgs = {}): Promise<ApiCategoryPage> {
  return apiGet<ApiCategoryPage>(
    "/categories",
    { page: args.page, limit: args.limit, locale: args.locale },
    { revalidate: 3600, tags: ["categories"] },
  );
}

interface ListMemesArgs {
  page?: number;
  limit?: number;
  category?: string;
  subreddit?: string;
  sort?: SortKey;
  locale?: LocaleCode;
}

/** GET /memes — paginated, filterable, sortable. */
export function fetchMemes(args: ListMemesArgs = {}): Promise<ApiMemePage> {
  return apiGet<ApiMemePage>("/memes", {
    page: args.page,
    limit: args.limit,
    category: args.category,
    subreddit: args.subreddit,
    sort: args.sort,
    locale: args.locale,
  });
}

interface ListByCategoryArgs {
  page?: number;
  limit?: number;
  sort?: SortKey;
  locale?: LocaleCode;
}

/** GET /memes/{category} — null if the category does not exist. */
export async function fetchMemesByCategory(
  category: string,
  args: ListByCategoryArgs = {},
): Promise<ApiMemePage | null> {
  try {
    return await apiGet<ApiMemePage>(
      `/memes/${encodePathPart(category)}`,
      { page: args.page, limit: args.limit, sort: args.sort, locale: args.locale },
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
  locale?: LocaleCode,
): Promise<ApiMeme | null> {
  try {
    return await apiGet<ApiMeme>(
      `/memes/${encodePathPart(category)}/${encodePathPart(slug)}`,
      locale ? { locale } : {},
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
  locale?: LocaleCode;
}

/** GET /search — returns a flat array of results (no pagination envelope). Empty q returns []. */
export async function searchMemes(args: SearchArgs): Promise<ApiSearchResult[]> {
  const q = args.q.trim();
  if (!q) return [];
  return apiGet<ApiSearchResult[]>(
    "/search",
    { q, page: args.page, limit: args.limit, locale: args.locale },
    { revalidate: 60 },
  );
}
