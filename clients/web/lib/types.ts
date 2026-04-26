/**
 * Domain types for the OpenMEME frontend.
 * Adapted from the API contract (see lib/api.ts) — we hold our own
 * UI shape so the API can evolve independently of the components.
 */

export type MemeFormat = "jpg" | "png" | "gif" | "webp";

export interface Meme {
  /** Unique id within UI: `${category}/${slug}` */
  id: string;
  slug: string;
  /** Category slug as returned by the API (raw, kebab-case). */
  category: string;
  title: string;
  description?: string;
  author?: string;
  subreddit?: string;
  /** Reddit upvote score — used for ranking and the "score" sort. */
  score: number;
  tags: string[];
  /** Public CDN URL (resolved via cdnUrl()) — empty string if unknown. */
  imageUrl: string;
  /** Detail-page URL `/memes/{category}/{slug}`. */
  href: string;
  format: MemeFormat;
  /** ISO-8601. Defaults to "now" if the API omits the field. */
  createdAt: string;
  /** Reddit post URL (deep-link back to source). */
  postUrl?: string;
  /** Original direct image URL on Reddit. */
  sourceUrl?: string;
  /** Short emoji/glyph used in placeholder tiles. */
  placeholder: string;
  /** CSS gradient used while the image is loading or absent. */
  placeholderGradient: string;
  /** True when published in the last N days — see lib/data/memes.ts. */
  isNew?: boolean;
}

export interface Category {
  /** Raw API slug (kebab-case). Used in URLs. */
  slug: string;
  /** Human-readable label (Title Case, with diacritics). */
  name: string;
  count: number;
  /** Highest score in this category — drives sort/rank in the sidebar. */
  topScore: number;
  iconName: CategoryIcon;
}

export type CategoryIcon =
  | "globe"
  | "tv"
  | "circle"
  | "user"
  | "star"
  | "refresh";

export interface TrendingTag {
  rank: number;
  /** Raw tag string, with leading "#". */
  tag: string;
  count: number;
}

export interface PageInfo {
  page: number;
  limit: number;
  total: number;
  totalPages: number;
}

export interface MemeListing {
  data: Meme[];
  pageInfo: PageInfo;
}
