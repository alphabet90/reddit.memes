import type { Metadata } from "next";
import Link from "next/link";
import Script from "next/script";
import { getTranslations } from "next-intl/server";

import { Nav } from "@/components/nav/Nav";
import { Footer } from "@/components/Footer";
import { CategoryIconGlyph } from "@/components/icons";

import { getCategories } from "@/lib/data/categories";
import { breadcrumbJsonLd } from "@/lib/seo";
import { site } from "@/lib/site";
import { formatCompact } from "@/lib/format";
import { buildAlternates, localePath } from "@/lib/i18n-utils";
import { localeOgMap, type Locale } from "@/i18n/routing";
import type { LocaleCode } from "@/lib/api";

import styles from "./page.module.css";

export const revalidate = 1800;

type Props = { params: Promise<{ locale: Locale }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "categorias" });

  return {
    title: t("meta_title"),
    description: t("meta_description", { siteName: site.name }),
    alternates: {
      canonical: `/${locale}/categorias`,
      languages: buildAlternates("/categorias"),
    },
    openGraph: {
      title: t("og_title", { siteName: site.name }),
      description: t("og_description", { siteName: site.name }),
      url: `/${locale}/categorias`,
      locale: localeOgMap[locale],
    },
  };
}

export default async function CategoriesPage({ params }: Props) {
  const { locale } = await params;
  const apiLocale = locale as LocaleCode;
  const t = await getTranslations({ locale, namespace: "categorias" });

  const categories = await getCategories(apiLocale).catch(() => []);

  const breadcrumbs = [
    { name: t("breadcrumb_inicio"), href: "/" },
    { name: t("breadcrumb_categorias"), href: "/categorias" },
  ];

  return (
    <>
      <Nav />

      <div className={styles.page}>
        <main id="contenido" className={styles.main}>
          <div className="container">
            <header className={styles.header}>
              <p className={styles.eyebrow}>{t("eyebrow")}</p>
              <h1 className={styles.title}>{t("title")}</h1>
              <p className={styles.sub}>
                {t("sub", {
                  count: categories.length,
                  total: formatCompact(
                    categories.reduce((sum, c) => sum + c.count, 0),
                  ),
                })}
              </p>
            </header>

            <ul className={styles.grid} role="list">
              {categories.map((c) => (
                <li key={c.slug}>
                  <Link
                    href={localePath(locale, `/categorias/${c.slug}`)}
                    className={styles.tile}
                    aria-label={`${c.name} — ${formatCompact(c.count)} memes`}
                  >
                    <span className={styles.iconWrap} aria-hidden="true">
                      <CategoryIconGlyph
                        name={c.iconName}
                        size={20}
                        color="var(--accent-primary)"
                      />
                    </span>
                    <span className={styles.name}>{c.name}</span>
                    <span className={styles.count}>
                      {t("memes_count", { count: formatCompact(c.count) })}
                    </span>
                    <span className={styles.topScore}>
                      {t("top_score", { score: formatCompact(c.topScore) })}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
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
