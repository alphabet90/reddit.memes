import type { Metadata } from "next";
import Script from "next/script";
import { getTranslations } from "next-intl/server";

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
import { buildAlternates, localePath } from "@/lib/i18n-utils";
import { localeOgMap, type Locale } from "@/i18n/routing";
import type { LocaleCode } from "@/lib/api";

import styles from "./page.module.css";

export const revalidate = 300;

const PAGE_SIZE = 24;

type SearchParams = { page?: string };
type Props = {
  params: Promise<{ locale: Locale }>;
  searchParams: Promise<SearchParams>;
};

function parsePage(raw: string | undefined): number {
  const n = Number(raw ?? "0");
  return Number.isFinite(n) && n >= 0 ? Math.floor(n) : 0;
}

export async function generateMetadata({ params, searchParams }: Props): Promise<Metadata> {
  const { locale } = await params;
  const { page: pageRaw } = await searchParams;
  const t = await getTranslations({ locale, namespace: "populares" });
  const page = parsePage(pageRaw);
  const canonical = `/${locale}/memes/populares${page > 0 ? `?page=${page}` : ""}`;

  return {
    title: t("meta_title"),
    description: t("meta_description", { siteName: site.name }),
    alternates: {
      canonical,
      languages: buildAlternates(`/memes/populares${page > 0 ? `?page=${page}` : ""}`),
    },
    openGraph: {
      title: t("og_title", { siteName: site.name }),
      description: t("meta_description", { siteName: site.name }),
      url: canonical,
      locale: localeOgMap[locale],
    },
  };
}

export default async function PopularMemesPage({ params, searchParams }: Props) {
  const { locale } = await params;
  const { page: pageRaw } = await searchParams;
  const page = parsePage(pageRaw);
  const apiLocale = locale as LocaleCode;
  const t = await getTranslations({ locale, namespace: "populares" });

  const [listing, categories, trending] = await Promise.all([
    getMemeListing({ sort: "score", page, limit: PAGE_SIZE, locale: apiLocale }).catch(() => null),
    getCategories(apiLocale).then((c) => c.slice(0, 10)).catch(() => []),
    getTrending(5, apiLocale).catch(() => []),
  ]);

  const breadcrumbs = [
    { name: t("breadcrumb_inicio"), href: "/" },
    { name: t("breadcrumb_populares"), href: "/memes/populares" },
  ];

  const listingWithHref = listing
    ? {
        ...listing,
        data: listing.data.map((m) => ({ ...m, href: localePath(locale, m.href) })),
      }
    : null;

  const buildPageHref = (p: number) =>
    p > 0
      ? localePath(locale, `/memes/populares?page=${p}`)
      : localePath(locale, "/memes/populares");

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
              <p className={styles.eyebrow}>{t("eyebrow")}</p>
              <h1 className={styles.title}>{t("title")}</h1>
              <p className={styles.sub}>{t("sub")}</p>
            </header>

            <div className={styles.layout}>
              <div className={styles.primary}>
                <SectionTitle id="populares-title" icon={<span>⭐</span>}>
                  {t("section_title")}
                </SectionTitle>
                {listingWithHref && listingWithHref.data.length > 0 ? (
                  <>
                    <MemeListingGrid
                      memes={listingWithHref.data}
                      ariaLabel={t("title")}
                      priorityCount={5}
                    />
                    <Pagination
                      page={listingWithHref.pageInfo.page}
                      totalPages={listingWithHref.pageInfo.totalPages}
                      buildHref={buildPageHref}
                      label={t("pagination_label")}
                    />
                  </>
                ) : (
                  <p className={styles.empty}>{t("empty")}</p>
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
          __html: JSON.stringify(breadcrumbJsonLd(breadcrumbs, locale)),
        }}
      />
    </>
  );
}
