import type { MetadataRoute } from "next";
import { site } from "@/lib/site";
import { getCategories } from "@/lib/data/categories";
import { getMemes } from "@/lib/data/memes";

export const dynamic = "force-static";
export const revalidate = 3600;

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const [categories, memes] = await Promise.all([getCategories(), getMemes()]);
  const now = new Date();

  const staticEntries: MetadataRoute.Sitemap = [
    { url: `${site.url}/`,            lastModified: now, changeFrequency: "hourly",  priority: 1.0 },
    { url: `${site.url}/top`,         lastModified: now, changeFrequency: "hourly",  priority: 0.9 },
    { url: `${site.url}/nuevos`,      lastModified: now, changeFrequency: "hourly",  priority: 0.9 },
    { url: `${site.url}/clasicos`,    lastModified: now, changeFrequency: "daily",   priority: 0.8 },
    { url: `${site.url}/aleatorio`,   lastModified: now, changeFrequency: "daily",   priority: 0.6 },
    { url: `${site.url}/categorias`,  lastModified: now, changeFrequency: "weekly",  priority: 0.8 },
    { url: `${site.url}/subir`,       lastModified: now, changeFrequency: "monthly", priority: 0.5 },
    { url: `${site.url}/manifiesto`,  lastModified: now, changeFrequency: "yearly",  priority: 0.3 },
  ];

  const categoryEntries: MetadataRoute.Sitemap = categories.map((c) => ({
    url: `${site.url}/categorias/${c.slug}`,
    lastModified: now,
    changeFrequency: "daily",
    priority: 0.7,
  }));

  const memeEntries: MetadataRoute.Sitemap = memes.map((m) => ({
    url: `${site.url}/memes/${m.slug}`,
    lastModified: new Date(m.createdAt),
    changeFrequency: "monthly",
    priority: 0.6,
  }));

  return [...staticEntries, ...categoryEntries, ...memeEntries];
}
