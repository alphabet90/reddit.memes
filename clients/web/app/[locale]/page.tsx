import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";

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
import { buildAlternates, localePath } from "@/lib/i18n-utils";
import { localeOgMap, type Locale } from "@/i18n/routing";
import type { LocaleCode } from "@/lib/api";

import styles from "./page.module.css";

export const revalidate = 300;

type Props = { params: Promise<{ locale: Locale }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "site" });

  return {
    title: t("tagline"),
    description: t("description"),
    alternates: {
      canonical: `/${locale}`,
      languages: buildAlternates("/"),
    },
    openGraph: {
      title: `${site.name} — ${t("tagline")}`,
      description: t("description"),
      url: `/${locale}`,
      locale: localeOgMap[locale],
    },
  };
}

export default async function HomePage({ params }: Props) {
  const { locale } = await params;
  const apiLocale = locale as LocaleCode;
  const t = await getTranslations({ locale, namespace: "home" });

  const [popularMemes, categories, trending] = await Promise.all([
    getPopularMemes(25, apiLocale).catch(() => []),
    getCategories(apiLocale).then((c) => c.slice(0, 10)).catch(() => []),
    getTrending(5, apiLocale).catch(() => []),
  ]);

  const memesWithLocaleHref = popularMemes.map((m) => ({
    ...m,
    href: localePath(locale, m.href),
  }));

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
                    {t("populares_title")}
                  </SectionTitle>
                  <MasonryGrid
                    memes={memesWithLocaleHref}
                    ariaLabel={t("populares_aria")}
                    moreHref={localePath(locale, "/memes/populares")}
                  />
                </section>
              </div>

              <Sidebar categories={categories} trending={trending} />
            </div>
          </div>
        </main>

        <Footer />
      </div>
    </>
  );
}
