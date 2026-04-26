import type { Metadata } from "next";
import Script from "next/script";
import { notFound } from "next/navigation";

import { Nav } from "@/components/nav/Nav";
import { Footer } from "@/components/Footer";
import { Sidebar } from "@/components/sidebar/Sidebar";
import { MemeListingGrid } from "@/components/meme/MemeListingGrid";
import { Pagination } from "@/components/ui/Pagination";
import { SortTabs } from "@/components/ui/SortTabs";
import { EmptyState } from "@/components/ui/EmptyState";
import { SectionTitle } from "@/components/ui/SectionTitle";

import { getCategories, getCategory } from "@/lib/data/categories";
import { getCategoryListing } from "@/lib/data/memes";
import { getTrending } from "@/lib/data/trending";
import {
  breadcrumbJsonLd,
  categoryCollectionJsonLd,
} from "@/lib/seo";
import { site } from "@/lib/site";
import { formatCompact } from "@/lib/format";

import styles from "./page.module.css";

export const revalidate = 600;

const PAGE_SIZE = 24;
const SORT_VALUES = ["score", "created_at", "title"] as const;
type SortValue = (typeof SORT_VALUES)[number];

const SORT_OPTIONS = [
  { value: "score", label: "Top" },
  { value: "created_at", label: "Nuevos" },
  { value: "title", label: "A–Z" },
];

type RouteParams = { slug: string };
type SearchParams = { page?: string; sort?: string };

type Props = {
  params: Promise<RouteParams>;
  searchParams: Promise<SearchParams>;
};

function parsePage(raw: string | undefined): number {
  const n = Number(raw ?? "0");
  return Number.isFinite(n) && n >= 0 ? Math.floor(n) : 0;
}

function parseSort(raw: string | undefined): SortValue {
  return (SORT_VALUES as readonly string[]).includes(raw ?? "")
    ? (raw as SortValue)
    : "score";
}

export async function generateMetadata({ params, searchParams }: Props): Promise<Metadata> {
  const { slug } = await params;
  const { page: pageRaw } = await searchParams;
  const category = await getCategory(slug);
  if (!category) return { title: "Categoría no encontrada" };

  const page = parsePage(pageRaw);
  const canonical = `/categorias/${category.slug}${page > 0 ? `?page=${page}` : ""}`;
  const description = `Memes de ${category.name} — ${formatCompact(
    category.count,
  )} memes argentinos. Buscá y compartí los mejores memes de ${category.name} en ${site.name}.`;

  return {
    title: `Memes de ${category.name}`,
    description,
    alternates: { canonical },
    openGraph: {
      title: `Memes de ${category.name} — ${site.name}`,
      description,
      url: canonical,
      type: "website",
    },
    twitter: {
      card: "summary_large_image",
      title: `Memes de ${category.name}`,
      description,
    },
  };
}

export default async function CategoryPage({ params, searchParams }: Props) {
  const { slug } = await params;
  const sp = await searchParams;
  const page = parsePage(sp.page);
  const sort = parseSort(sp.sort);

  const [category, listing, categories, trending] = await Promise.all([
    getCategory(slug),
    getCategoryListing(slug, { page, limit: PAGE_SIZE, sort }),
    getCategories(),
    getTrending(),
  ]);

  if (!category || !listing) notFound();

  const breadcrumbs = [
    { name: "Inicio", href: "/" },
    { name: "Categorías", href: "/categorias" },
    { name: category.name, href: `/categorias/${category.slug}` },
  ];

  const buildSortHref = (s: string) =>
    s === "score" ? `/categorias/${slug}` : `/categorias/${slug}?sort=${s}`;
  const buildPageHref = (p: number) => {
    const qs = new URLSearchParams();
    if (p > 0) qs.set("page", String(p));
    if (sort !== "score") qs.set("sort", sort);
    const q = qs.toString();
    return q ? `/categorias/${slug}?${q}` : `/categorias/${slug}`;
  };

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
              <div>
                <p className={styles.eyebrow}>Categoría</p>
                <h1 className={styles.title}>{category.name}</h1>
                <p className={styles.sub}>
                  {formatCompact(category.count)} memes · top score{" "}
                  {formatCompact(category.topScore)}
                </p>
              </div>

              <SortTabs
                options={SORT_OPTIONS}
                active={sort}
                buildHref={buildSortHref}
                ariaLabel={`Ordenar memes de ${category.name}`}
              />
            </header>

            <div className={styles.layout}>
              <div className={styles.primary}>
                {listing.data.length === 0 ? (
                  <EmptyState
                    title="No hay memes en esta categoría todavía"
                    description="Volvé pronto o explorá otras categorías."
                    ctaLabel="Ver todas las categorías"
                    ctaHref="/categorias"
                  />
                ) : (
                  <>
                    <SectionTitle id="cat-title">
                      {sort === "score" ? "Top memes" : sort === "created_at" ? "Más nuevos" : "Por título"}
                    </SectionTitle>
                    <MemeListingGrid
                      memes={listing.data}
                      ariaLabel={`Memes de ${category.name}`}
                      priorityCount={5}
                    />
                    <Pagination
                      page={listing.pageInfo.page}
                      totalPages={listing.pageInfo.totalPages}
                      buildHref={buildPageHref}
                      label="Paginación de memes"
                    />
                  </>
                )}
              </div>

              <Sidebar
                categories={categories}
                trending={trending}
                activeCategorySlug={category.slug}
              />
            </div>
          </div>
        </main>

        <Footer />
      </div>

      <Script
        id="ld-category-collection"
        type="application/ld+json"
        strategy="beforeInteractive"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(
            categoryCollectionJsonLd(category, listing.data),
          ),
        }}
      />
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
