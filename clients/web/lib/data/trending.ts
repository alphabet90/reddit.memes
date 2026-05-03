import type { TrendingTag } from "@/lib/types";
import { fetchMemes, type LocaleCode } from "@/lib/api";

/**
 * Trending tags are derived from the top-scoring slice of recent
 * memes — the API doesn't expose a /trending endpoint. We weight
 * each tag by aggregate score across the slice so brand-new memes
 * with high scores rank higher than older memes that share a tag.
 */
export async function getTrending(
  limit = 5,
  locale: LocaleCode = "en",
): Promise<TrendingTag[]> {
  const page = await fetchMemes({ limit: 60, sort: "score", locale });

  const totals = new Map<string, number>();
  for (const meme of page.data) {
    const score = Math.max(1, meme.score ?? 1);
    for (const raw of meme.tags ?? []) {
      const tag = raw.trim().toLowerCase();
      if (!tag || tag.length > 32) continue;
      totals.set(tag, (totals.get(tag) ?? 0) + score);
    }
  }

  const ranked = [...totals.entries()]
    .sort((a, b) => b[1] - a[1])
    .slice(0, limit);

  return ranked.map(([tag, count], i) => ({
    rank: i + 1,
    tag: `#${tag.replace(/^#/, "")}`,
    count,
  }));
}
