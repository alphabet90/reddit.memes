import type { TrendingTag } from "@/lib/types";

export async function getTrending(): Promise<TrendingTag[]> {
  return [
    { rank: 1, tag: "#perroconfundido",  count: 8_400 },
    { rank: 2, tag: "#milei",            count: 7_100 },
    { rank: 3, tag: "#dalevodespodes",   count: 5_900 },
    { rank: 4, tag: "#maradona",         count: 4_800 },
    { rank: 5, tag: "#gatosargentinos",  count: 3_200 },
  ];
}
