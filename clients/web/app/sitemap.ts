import type { MetadataRoute } from "next";
import { site } from "@/lib/site";
import { getCategories } from "@/lib/data/categories";
import { fetchMemes } from "@/lib/api";
import { toMeme } from "@/lib/data/memes";

/**
 * Single XML sitemap. We cap at the first ~5000 highest-scoring memes
 * to keep within Google's 50k/50MB limit and minimize regen cost.
 * For larger collections, sitemap-index + chunked sitemaps would replace
 * this — out of scope for v1.
 */
export const dynamic = "force-static";
export const revalidate = 3600;

const MEME_LIMIT = 5000;
const PAGE_SIZE = 100;

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const now = new Date();

  const staticEntries: MetadataRoute.Sitemap = [
    { url: `${site.url}/`,           lastModified: now, changeFrequency: "hourly",  priority: 1.0 },
    { url: `${site.url}/categorias`, lastModified: now, changeFrequency: "weekly",  priority: 0.8 },
    { url: `${site.url}/buscar`,     lastModified: now, changeFrequency: "monthly", priority: 0.4 },
  ];

  let categoryEntries: MetadataRoute.Sitemap = [];
  let memeEntries: MetadataRoute.Sitemap = [];

  try {
    const categories = await getCategories();
    categoryEntries = categories.map((c) => ({
      url: `${site.url}/categorias/${c.slug}`,
      lastModified: now,
      changeFrequency: "daily",
      priority: 0.7,
    }));
  } catch {
    // API unreachable at build time — emit only the static entries.
  }

  try {
    const collected: ReturnType<typeof toMeme>[] = [];
    let page = 0;
    while (collected.length < MEME_LIMIT) {
      const res = await fetchMemes({ page, limit: PAGE_SIZE, sort: "score" });
      collected.push(...res.data.map(toMeme));
      if (res.page >= res.total_pages - 1 || res.data.length === 0) break;
      page += 1;
    }
    memeEntries = collected.slice(0, MEME_LIMIT).map((m) => ({
      url: `${site.url}${m.href}`,
      lastModified: new Date(m.createdAt),
      changeFrequency: "monthly",
      priority: 0.6,
    }));
  } catch {
    // ignore — keep partial sitemap
  }

  return [...staticEntries, ...categoryEntries, ...memeEntries];
}
