import type { Metadata } from "next";
import Script from "next/script";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";

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
import { breadcrumbJsonLd, categoryCollectionJsonLd } from "@/lib/seo";
import { site } from "@/lib/site";
import { formatCompact } from "@/lib/format";
import { buildAlternates, localePath } from "@/lib/i18n-utils";
import { localeOgMap, type Locale } from "@/i18n/routing";
import type { LocaleCode } from "@/lib/api";

import styles from "./page.module.css";

export const revalidate = 600;

const PAGE_SIZE = 24;
const SORT_VALUES = ["score", "created_at", "title"] as const;
type SortValue = (typeof SORT_VALUES)[number];

type RouteParams = { locale: Locale; slug: string };
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
  const { locale, slug } = await params;
  const { page: pageRaw } = await searchParams;
  const t = await getTranslations({ locale, namespace: "categoria_slug" });
  const apiLocale = locale as LocaleCode;
  const category = await getCategory(slug, apiLocale);
  if (!category) return { title: t("not_found") };

  const page = parsePage(pageRaw);
  const canonical = `/${locale}/categorias/${category.slug}${page > 0 ? `?page=${page}` : ""}`;

  return {
    title: t("meta_title", { name: category.name }),
    description: t("meta_description", {
      name: category.name,
      count: formatCompact(category.count),
      siteName: site.name,
    }),
    alternates: {
      canonical,
      languages: buildAlternates(`/categorias/${category.slug}${page > 0 ? `?page=${page}` : ""}`),
    },
    openGraph: {
      title: t("og_title", { name: category.name, siteName: site.name }),
      description: t("meta_description", {
        name: category.name,
        count: formatCompact(category.count),
        siteName: site.name,
      }),
      url: canonical,
      type: "website",
      locale: localeOgMap[locale],
    },
    twitter: {
      card: "summary_large_image",
      title: t("meta_title", { name: category.name }),
      description: t("meta_description", {
        name: category.name,
        count: formatCompact(category.count),
        siteName: site.name,
      }),
    },
  };
}

export default async function CategoryPage({ params, searchParams }: Props) {
  const { locale, slug } = await params;
  const sp = await searchParams;
  const page = parsePage(sp.page);
  const sort = parseSort(sp.sort);
  const apiLocale = locale as LocaleCode;
  const t = await getTranslations({ locale, namespace: "categoria_slug" });

  const sortOptions = [
    { value: "score", label: t("sort_top") },
    { value: "created_at", label: t("sort_nuevos") },
    { value: "title", label: t("sort_az") },
  ];

  const [category, listing, categories, trending] = await Promise.all([
    getCategory(slug, apiLocale),
    getCategoryListing(slug, { page, limit: PAGE_SIZE, sort, locale: apiLocale }),
    getCategories(apiLocale),
    getTrending(5, apiLocale),
  ]);

  if (!category || !listing) notFound();

  const breadcrumbs = [
    { name: t("breadcrumb_inicio"), href: "/" },
    { name: t("breadcrumb_categorias"), href: "/categorias" },
    { name: category.name, href: `/categorias/${category.slug}` },
  ];

  const listingWithHref = {
    ...listing,
    data: listing.data.map((m) => ({ ...m, href: localePath(locale, m.href) })),
  };

  const buildSortHref = (s: string) =>
    s === "score"
      ? localePath(locale, `/categorias/${slug}`)
      : localePath(locale, `/categorias/${slug}?sort=${s}`);

  const buildPageHref = (p: number) => {
    const qs = new URLSearchParams();
    if (p > 0) qs.set("page", String(p));
    if (sort !== "score") qs.set("sort", sort);
    const q = qs.toString();
    return q
      ? localePath(locale, `/categorias/${slug}?${q}`)
      : localePath(locale, `/categorias/${slug}`);
  };

  const sectionTitle =
    sort === "score"
      ? t("section_top")
      : sort === "created_at"
        ? t("section_nuevos")
        : t("section_az");

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
                      <a href={localePath(locale, b.href)}>{b.name}</a>
                    ) : (
                      <span aria-current="page">{b.name}</span>
                    )}
                  </li>
                ))}
              </ol>
            </nav>

            <header className={styles.header}>
              <div>
                <p className={styles.eyebrow}>{t("eyebrow")}</p>
                <h1 className={styles.title}>{category.name}</h1>
                <p className={styles.sub}>
                  {t("memes_count_text", {
                    count: formatCompact(category.count),
                    score: formatCompact(category.topScore),
                  })}
                </p>
              </div>

              <SortTabs
                options={sortOptions}
                active={sort}
                buildHref={buildSortHref}
                ariaLabel={t("sort_aria", { name: category.name })}
              />
            </header>

            <div className={styles.layout}>
              <div className={styles.primary}>
                {listingWithHref.data.length === 0 ? (
                  <EmptyState
                    title={t("empty_title")}
                    description={t("empty_desc")}
                    ctaLabel={t("empty_cta")}
                    ctaHref={localePath(locale, "/categorias")}
                  />
                ) : (
                  <>
                    <SectionTitle id="cat-title">{sectionTitle}</SectionTitle>
                    <MemeListingGrid
                      memes={listingWithHref.data}
                      ariaLabel={`${category.name} memes`}
                      priorityCount={5}
                    />
                    <Pagination
                      page={listing.pageInfo.page}
                      totalPages={listing.pageInfo.totalPages}
                      buildHref={buildPageHref}
                      label={t("pagination_label")}
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
            categoryCollectionJsonLd(category, listingWithHref.data, locale),
          ),
        }}
      />
      <Script
        id="ld-breadcrumb"
        type="application/ld+json"
        strategy="beforeInteractive"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(breadcrumbJsonLd(breadcrumbs, locale)),
        }}
      />
    </>
  );
}
