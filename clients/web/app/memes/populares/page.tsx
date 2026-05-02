import type { Metadata } from "next";
import Script from "next/script";

import { Nav } from "@/components/nav/Nav";
import { Footer } from "@/components/Footer";
import { Sidebar } from "@/components/sidebar/Sidebar";
import { MemeListingGrid } from "@/components/meme/MemeListingGrid";
import { Pagination } from "@/components/ui/Pagination";
import { SectionTitle } from "@/components/ui/SectionTitle";

import { getCategories } from "@/lib/data/categories";
import { getMemeListing } from "@/lib/data/memes";
import { getTrending } from "@/lib/data/trending";
import { breadcrumbJsonLd } from "@/lib/seo";
import { site } from "@/lib/site";

import styles from "./page.module.css";

export const revalidate = 300;

const PAGE_SIZE = 24;

type SearchParams = { page?: string };
type Props = { searchParams: Promise<SearchParams> };

function parsePage(raw: string | undefined): number {
  const n = Number(raw ?? "0");
  return Number.isFinite(n) && n >= 0 ? Math.floor(n) : 0;
}

export async function generateMetadata({ searchParams }: Props): Promise<Metadata> {
  const { page: pageRaw } = await searchParams;
  const page = parsePage(pageRaw);
  const canonical = `/memes/populares${page > 0 ? `?page=${page}` : ""}`;
  const description = `Los memes más populares de ${site.name}. Explorá el ranking de los mejores memes argentinos ordenados por puntuación.`;

  return {
    title: "Memes populares",
    description,
    alternates: { canonical },
    openGraph: {
      title: `Memes populares — ${site.name}`,
      description,
      url: canonical,
    },
  };
}

export default async function PopularMemesPage({ searchParams }: Props) {
  const { page: pageRaw } = await searchParams;
  const page = parsePage(pageRaw);

  const [listing, categories, trending] = await Promise.all([
    getMemeListing({ sort: "score", page, limit: PAGE_SIZE }).catch(() => null),
    getCategories().then((c) => c.slice(0, 10)).catch(() => []),
    getTrending().catch(() => []),
  ]);

  const breadcrumbs = [
    { name: "Inicio", href: "/" },
    { name: "Memes populares", href: "/memes/populares" },
  ];

  const buildPageHref = (p: number) =>
    p > 0 ? `/memes/populares?page=${p}` : "/memes/populares";

  return (
    <>
      <Nav />

      <div className={styles.page}>
        <main id="contenido" className={styles.main}>
          <div className="container">
            <nav aria-label="Migas de pan" className={styles.breadcrumbs}>
              <ol>
                {breadcrumbs.map((b, i) => (
                  <li key={b.href}>
                    {i < breadcrumbs.length - 1 ? (
                      <a href={b.href}>{b.name}</a>
                    ) : (
                      <span aria-current="page">{b.name}</span>
                    )}
                  </li>
                ))}
              </ol>
            </nav>

            <header className={styles.header}>
              <p className={styles.eyebrow}>Ranking</p>
              <h1 className={styles.title}>Memes populares</h1>
              <p className={styles.sub}>Los memes con más puntos de la comunidad.</p>
            </header>

            <div className={styles.layout}>
              <div className={styles.primary}>
                <SectionTitle id="populares-title" icon={<span>⭐</span>}>
                  Top memes
                </SectionTitle>
                {listing && listing.data.length > 0 ? (
                  <>
                    <MemeListingGrid
                      memes={listing.data}
                      ariaLabel="Memes populares"
                      priorityCount={5}
                    />
                    <Pagination
                      page={listing.pageInfo.page}
                      totalPages={listing.pageInfo.totalPages}
                      buildHref={buildPageHref}
                      label="Paginación de memes populares"
                    />
                  </>
                ) : (
                  <p className={styles.empty}>No hay memes disponibles.</p>
                )}
              </div>

              <Sidebar categories={categories} trending={trending} />
            </div>
          </div>
        </main>

        <Footer />
      </div>

      <Script
        id="ld-breadcrumb"
        type="application/ld+json"
        strategy="beforeInteractive"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(breadcrumbJsonLd(breadcrumbs)),
        }}
      />
    </>
  );
}
