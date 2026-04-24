import type { Category } from "@/lib/types";

/**
 * Static category list. Replace this function with a fetch to the API
 * once the backend exposes /categories. Keep the return type stable.
 */
export async function getCategories(): Promise<Category[]> {
  return [
    { slug: "la-vida",    name: "La Vida",    count: 32_100, iconName: "globe"   },
    { slug: "politica",   name: "Política",   count: 25_700, iconName: "tv"      },
    { slug: "futbol",     name: "Fútbol",     count: 18_300, iconName: "circle"  },
    { slug: "argentinos", name: "Argentinos", count: 15_200, iconName: "user"    },
    { slug: "clasicos",   name: "Clásicos",   count: 28_700, iconName: "star"    },
    { slug: "random",     name: "Random",     count: Number.POSITIVE_INFINITY, iconName: "refresh" },
  ];
}
