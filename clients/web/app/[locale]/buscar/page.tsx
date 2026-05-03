import type { Metadata } from "next";
import Script from "next/script";
import { getTranslations } from "next-intl/server";

import { Nav } from "@/components/nav/Nav";
import { Footer } from "@/components/Footer";
import { MemeListingGrid } from "@/components/meme/MemeListingGrid";
import { Pagination } from "@/components/ui/Pagination";
import { EmptyState } from "@/components/ui/EmptyState";
import { SearchIcon } from "@/components/icons";

import { searchListing } from "@/lib/data/memes";
import { breadcrumbJsonLd } from "@/lib/seo";
import { site } from "@/lib/site";
import { buildAlternates, localePath } from "@/lib/i18n-utils";
import { localeOgMap, type Locale } from "@/i18n/routing";
import type { LocaleCode } from "@/lib/api";

import styles from "./page.module.css";

const PAGE_SIZE = 24;

type SearchParams = { q?: string; page?: string };
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
  const sp = await searchParams;
  const q = (sp.q ?? "").trim();
  const t = await getTranslations({ locale, namespace: "buscar" });
  const canonical = q
    ? `/${locale}/buscar?q=${encodeURIComponent(q)}`
    : `/${locale}/buscar`;

  if (!q) {
    return {
      title: t("meta_title_empty"),
      description: t("meta_description_empty", { siteName: site.name }),
      alternates: {
        canonical,
        languages: buildAlternates("/buscar"),
      },
      robots: { index: false, follow: true },
    };
  }

  return {
    title: t("meta_title_results", { q }),
    description: t("meta_description_results", { q, siteName: site.name }),
    alternates: { canonical },
    openGraph: {
      title: t("og_title_results", { q, siteName: site.name }),
      description: t("og_description_results", { q }),
      url: canonical,
      locale: localeOgMap[locale],
    },
    robots: { index: false, follow: true },
  };
}

export default async function SearchPage({ params, searchParams }: Props) {
  const { locale } = await params;
  const sp = await searchParams;
  const q = (sp.q ?? "").trim();
  const page = parsePage(sp.page);
  const apiLocale = locale as LocaleCode;
  const t = await getTranslations({ locale, namespace: "buscar" });

  const result = await searchListing({ q, page, limit: PAGE_SIZE, locale: apiLocale });
  const memes = result.data.map((m) => ({ ...m, href: localePath(locale, m.href) }));

  const breadcrumbs = [
    { name: t("breadcrumb_inicio"), href: "/" },
    { name: t("breadcrumb_buscar"), href: "/buscar" },
  ];

  const buildPageHref = (p: number) => {
    const qs = new URLSearchParams();
    qs.set("q", q);
    if (p > 0) qs.set("page", String(p));
    return localePath(locale, `/buscar?${qs.toString()}`);
  };

  return (
    <>
      <Nav />

      <div className={styles.page}>
        <main id="contenido" className={styles.main}>
          <div className="container">
            <header className={styles.header}>
              <p className={styles.eyebrow}>{t("eyebrow")}</p>
              <h1 className={styles.title}>
                {q ? t("title_results", { q }) : t("title_empty")}
              </h1>
              {q && result.pageInfo.total > 0 ? (
                <p className={styles.sub}>
                  {t("results_count", { total: result.pageInfo.total.toLocaleString() })}
                </p>
              ) : null}

              <form
                action={localePath(locale, "/buscar")}
                role="search"
                className={styles.form}
                aria-label={t("aria_label")}
              >
                <label className={styles.field}>
                  <span className="sr-only">{t("aria_label")}</span>
                  <SearchIcon size={16} />
                  <input
                    type="search"
                    name="q"
                    placeholder={t("input_placeholder")}
                    defaultValue={q}
                    autoComplete="off"
                    autoFocus={!q}
                  />
                </label>
                <button type="submit" className={styles.button}>
                  {t("button")}
                </button>
              </form>
            </header>

            {!q ? (
              <EmptyState
                title={t("empty_title")}
                description={t("empty_desc")}
                ctaLabel={t("cta_categories")}
                ctaHref={localePath(locale, "/categorias")}
                glyph="🔍"
              />
            ) : memes.length === 0 ? (
              <EmptyState
                title={t("no_results_title")}
                description={t("no_results_desc", { q })}
                ctaLabel={t("cta_categories")}
                ctaHref={localePath(locale, "/categorias")}
                glyph="🤷"
              />
            ) : (
              <>
                <MemeListingGrid
                  memes={memes}
                  ariaLabel={`${t("title_results", { q })}`}
                  priorityCount={5}
                />
                <Pagination
                  page={result.pageInfo.page}
                  totalPages={result.pageInfo.totalPages}
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
          __html: JSON.stringify(breadcrumbJsonLd(breadcrumbs, locale)),
        }}
      />
    </>
  );
}
