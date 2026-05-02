import type { Metadata } from "next";

import { Nav } from "@/components/nav/Nav";
import { Hero } from "@/components/hero/Hero";
import { MasonryGrid } from "@/components/meme/MasonryGrid";
import { Sidebar } from "@/components/sidebar/Sidebar";
import { SectionTitle } from "@/components/ui/SectionTitle";
import { Footer } from "@/components/Footer";

import { getCategories } from "@/lib/data/categories";
import { getPopularMemes } from "@/lib/data/memes";
import { getTrending } from "@/lib/data/trending";
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
  // Parallel-fetch everything the home needs. Each call is guarded
  // so a transient API failure never breaks the build or the shell —
  // ISR will refill the section on the next request.
  const [popularMemes, categories, trending] = await Promise.all([
    getPopularMemes(25).catch(() => []),
    getCategories().then((c) => c.slice(0, 10)).catch(() => []),
    getTrending().catch(() => []),
  ]);

  return (
    <>
      <Nav />

      <div className={styles.page}>
        <main id="contenido" className={styles.main}>
          <Hero />

          <div className="container">
            <div className={styles.layout}>
              <div className={styles.primary}>
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
              />
            </div>
          </div>
        </main>

        <Footer />
      </div>
    </>
  );
}
