import type { Category, CategoryIcon } from "@/lib/types";
import { fetchCategories, type ApiCategorySummary } from "@/lib/api";

/**
 * Map known category slugs to brand icons. Categories the API
 * returns that are not in this map fall back to {@link defaultIcon}.
 * Keep additions tiny — the icon set is intentionally small.
 */
const iconBySlug: Record<string, CategoryIcon> = {
  "la-vida": "globe",
  "politica": "tv",
  "futbol": "circle",
  "argentinos": "user",
  "argentina-reaction": "user",
  "clasicos": "star",
  "random": "refresh",
};

const defaultIcon: CategoryIcon = "star";

/** Normalize "argentina-reaction" → "Argentina Reaction" for headings. */
function humanize(slug: string): string {
  return slug
    .split(/[-_]/)
    .filter(Boolean)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

function toCategory(api: ApiCategorySummary): Category {
  return {
    slug: api.category,
    name: humanize(api.category),
    count: api.count,
    topScore: api.top_score,
    iconName: iconBySlug[api.category] ?? defaultIcon,
  };
}

/** All categories, ordered by count (largest first). */
export async function getCategories(): Promise<Category[]> {
  const list = await fetchCategories();
  return list
    .map(toCategory)
    .sort((a, b) => b.count - a.count);
}

/** Quick lookup for a single category — returns undefined if missing. */
export async function getCategory(slug: string): Promise<Category | undefined> {
  const list = await getCategories();
  return list.find((c) => c.slug === slug);
}
