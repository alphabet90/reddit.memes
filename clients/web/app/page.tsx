import type { Metadata } from "next";
import Script from "next/script";

import { Nav } from "@/components/nav/Nav";
import { Hero } from "@/components/hero/Hero";
import { MemeGrid } from "@/components/meme/MemeGrid";
import { MasonryGrid } from "@/components/meme/MasonryGrid";
import { Sidebar } from "@/components/sidebar/Sidebar";
import { SectionTitle } from "@/components/ui/SectionTitle";
import { Footer } from "@/components/Footer";

import { getCategories } from "@/lib/data/categories";
import {
  getTopMemes,
  getPopularMemes,
} from "@/lib/data/memes";
import { getTrending } from "@/lib/data/trending";
import { memeItemListJsonLd } from "@/lib/seo";
import { site } from "@/lib/site";

import styles from "./page.module.css";

/**
 * Pre-render the home at build time (SSG). Revalidate every 5 min
 * so ranked lists stay fresh without round-trips on every request.
 */
export const revalidate = 300;

export const metadata: Metadata = {
  title: `${site.tagline}`,
  description: site.description,
  alternates: { canonical: "/" },
  openGraph: {
    title: `${site.name} — ${site.tagline}`,
    description: site.description,
    url: "/",
  },
};

export default async function HomePage() {
  // Parallel-fetch everything the home needs.
  const [topMemes, popularMemes, categories, trending] = await Promise.all([
    getTopMemes(5),
    getPopularMemes(12),
    getCategories(),
    getTrending(),
  ]);

  const itemListLd = memeItemListJsonLd(topMemes);

  return (
    <>
      <Nav />

      <div className={styles.page}>
        <main id="contenido" className={styles.main}>
          <Hero />

          <div className="container">
            <div className={styles.layout}>
              <div className={styles.primary}>
                <section aria-labelledby="top-title">
                  <SectionTitle id="top-title" icon={<span>🔥</span>}>
                    Top memes del día
                  </SectionTitle>
                  <MemeGrid
                    memes={topMemes}
                    ranked
                    ariaLabel="Top memes del día"
                  />
                </section>

                <section aria-labelledby="populares-title">
                  <SectionTitle id="populares-title" icon={<span>⭐</span>}>
                    Memes populares
                  </SectionTitle>
                  <MasonryGrid
                    memes={popularMemes}
                    ariaLabel="Memes populares"
                  />
                </section>
              </div>

              <Sidebar
                categories={categories}
                trending={trending}
                activeCategorySlug="la-vida"
              />
            </div>
          </div>
        </main>

        <Footer />
      </div>

      <Script
        id="ld-home-itemlist"
        type="application/ld+json"
        strategy="beforeInteractive"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(itemListLd) }}
      />
    </>
  );
}
