/**
 * Domain types for the OpenMEME frontend.
 * These model the shapes the UI consumes — the backing store
 * (future API / RSC fetch) must conform to these contracts.
 */

export type MemeFormat = "jpg" | "png" | "gif" | "webp";

export interface Meme {
  id: string;
  slug: string;
  title: string;
  category: CategorySlug;
  tags: string[];
  /** Public image URL (served by CDN or Next/Image loader) */
  imageUrl: string;
  /** Width/height aid the Image component avoid CLS */
  width: number;
  height: number;
  /** Pre-computed low-quality placeholder (base64 data URL) — optional */
  blurDataURL?: string;
  views: number;
  shares: number;
  format: MemeFormat;
  /** ISO-8601 timestamp */
  createdAt: string;
  /** Short emoji/glyph used in UI kit placeholders until real assets land */
  placeholder: string;
  /** CSS gradient used while images are still being wired up */
  placeholderGradient: string;
  /** If true, render NUEVO badge */
  isNew?: boolean;
}

export type CategorySlug =
  | "la-vida"
  | "politica"
  | "futbol"
  | "argentinos"
  | "clasicos"
  | "random";

export interface Category {
  slug: CategorySlug;
  name: string;
  count: number;
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
  tag: string;
  count: number;
}
