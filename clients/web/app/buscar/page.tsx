import type { Metadata } from "next";
import Script from "next/script";

import { Nav } from "@/components/nav/Nav";
import { Footer } from "@/components/Footer";
import { MemeListingGrid } from "@/components/meme/MemeListingGrid";
import { Pagination } from "@/components/ui/Pagination";
import { EmptyState } from "@/components/ui/EmptyState";
import { SearchIcon } from "@/components/icons";

import { searchMemes } from "@/lib/api";
import { toMeme } from "@/lib/data/memes";
import { breadcrumbJsonLd } from "@/lib/seo";
import { site } from "@/lib/site";

import styles from "./page.module.css";

const PAGE_SIZE = 24;

type SearchParams = { q?: string; page?: string };
type Props = { searchParams: Promise<SearchParams> };

function parsePage(raw: string | undefined): number {
  const n = Number(raw ?? "0");
  return Number.isFinite(n) && n >= 0 ? Math.floor(n) : 0;
}

export async function generateMetadata({ searchParams }: Props): Promise<Metadata> {
  const sp = await searchParams;
  const q = (sp.q ?? "").trim();
  const canonical = q ? `/buscar?q=${encodeURIComponent(q)}` : "/buscar";

  if (!q) {
    return {
      title: "Buscar memes",
      description: `Buscá entre miles de memes argentinos en ${site.name}. Política, fútbol, clásicos, reacciones y más.`,
      alternates: { canonical },
      // Empty search has no useful content for crawlers — let them skip it
      // but still allow following links to actual results.
      robots: { index: false, follow: true },
    };
  }

  return {
    title: `Buscar: ${q}`,
    description: `Resultados para "${q}" — memes argentinos en ${site.name}.`,
    alternates: { canonical },
    openGraph: {
      title: `Resultados para "${q}" — ${site.name}`,
      description: `Memes argentinos relacionados con "${q}".`,
      url: canonical,
    },
    // Search-result pages should not be indexed (thin content, dup risk).
    robots: { index: false, follow: true },
  };
}

export default async function SearchPage({ searchParams }: Props) {
  const sp = await searchParams;
  const q = (sp.q ?? "").trim();
  const page = parsePage(sp.page);

  const result = await searchMemes({ q, page, limit: PAGE_SIZE });
  const memes = result.data.map(toMeme);

  const breadcrumbs = [
    { name: "Inicio", href: "/" },
    { name: "Buscar", href: "/buscar" },
  ];

  const buildPageHref = (p: number) => {
    const qs = new URLSearchParams();
    qs.set("q", q);
    if (p > 0) qs.set("page", String(p));
    return `/buscar?${qs.toString()}`;
  };

  return (
    <>
      <Nav />

      <div className={styles.page}>
        <main id="contenido" className={styles.main}>
          <div className="container">
            <header className={styles.header}>
              <p className={styles.eyebrow}>Buscar</p>
              <h1 className={styles.title}>
                {q ? `Resultados para "${q}"` : "Buscá tu meme"}
              </h1>
              {q && result.total > 0 ? (
                <p className={styles.sub}>
                  {result.total.toLocaleString("es-AR")} resultados
                </p>
              ) : null}

              <form
                action="/buscar"
                role="search"
                className={styles.form}
                aria-label="Buscar memes"
              >
                <label className={styles.field}>
                  <span className="sr-only">Buscar memes</span>
                  <SearchIcon size={16} />
                  <input
                    type="search"
                    name="q"
                    placeholder="Buscar memes…"
                    defaultValue={q}
                    autoComplete="off"
                    autoFocus={!q}
                  />
                </label>
                <button type="submit" className={styles.button}>
                  Buscar
                </button>
              </form>
            </header>

            {!q ? (
              <EmptyState
                title="Escribí algo para buscar"
                description="Probá con un nombre, una frase o una etiqueta. Por ejemplo: «milei», «asado» o «lunes»."
                ctaLabel="Ver categorías"
                ctaHref="/categorias"
                glyph="🔍"
              />
            ) : memes.length === 0 ? (
              <EmptyState
                title="No encontramos nada"
                description={`No hay memes para "${q}". Probá con otra búsqueda o explorá las categorías.`}
                ctaLabel="Ver categorías"
                ctaHref="/categorias"
                glyph="🤷"
              />
            ) : (
              <>
                <MemeListingGrid
                  memes={memes}
                  ariaLabel={`Resultados de búsqueda para ${q}`}
                  priorityCount={5}
                />
                <Pagination
                  page={result.page}
                  totalPages={result.total_pages}
                  buildHref={buildPageHref}
                />
              </>
            )}
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
