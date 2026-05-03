import type { Metadata } from "next";
import Image from "next/image";
import Link from "next/link";
import Script from "next/script";
import { notFound } from "next/navigation";
import { getTranslations } from "next-intl/server";

import { Nav } from "@/components/nav/Nav";
import { Footer } from "@/components/Footer";
import { MemeListingGrid } from "@/components/meme/MemeListingGrid";
import { SectionTitle } from "@/components/ui/SectionTitle";

import { getCategoryListing, getMeme } from "@/lib/data/memes";
import { formatCompact } from "@/lib/format";
import { breadcrumbJsonLd, memeImageObjectJsonLd } from "@/lib/seo";
import { site } from "@/lib/site";
import { buildAlternates, localePath } from "@/lib/i18n-utils";
import { localeLangMap, localeOgMap, type Locale } from "@/i18n/routing";
import type { LocaleCode } from "@/lib/api";

import styles from "./page.module.css";

export const revalidate = 3600;

type RouteParams = { locale: Locale; category: string; slug: string };
type Props = { params: Promise<RouteParams> };

function humanize(slug: string): string {
  return slug
    .split(/[-_]/)
    .filter(Boolean)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale, category, slug } = await params;
  const apiLocale = locale as LocaleCode;
  const t = await getTranslations({ locale, namespace: "meme_detail" });

  const meme = await getMeme(category, slug, apiLocale);
  if (!meme) return { title: t("not_found_title") };

  const canonical = `/${locale}/memes/${meme.category}/${meme.slug}`;
  const description =
    meme.description ??
    t("meta_description_fallback", { title: meme.title, siteName: site.name });

  const images = meme.imageUrl
    ? [{ url: meme.imageUrl, alt: meme.title }]
    : [{ url: site.ogImage, alt: site.name }];

  return {
    title: meme.title,
    description,
    alternates: {
      canonical,
      languages: buildAlternates(`/memes/${meme.category}/${meme.slug}`),
    },
    openGraph: {
      title: meme.title,
      description,
      type: "article",
      url: canonical,
      images,
      locale: localeOgMap[locale],
    },
    twitter: {
      card: "summary_large_image",
      title: meme.title,
      description,
      images: meme.imageUrl ? [meme.imageUrl] : [site.ogImage],
    },
    keywords: meme.tags,
  };
}

export default async function MemeDetailPage({ params }: Props) {
  const { locale, category, slug } = await params;
  const apiLocale = locale as LocaleCode;
  const t = await getTranslations({ locale, namespace: "meme_detail" });

  const meme = await getMeme(category, slug, apiLocale);
  if (!meme) notFound();

  const related = await getCategoryListing(meme.category, {
    limit: 12,
    sort: "score",
    locale: apiLocale,
  });
  const relatedMemes = (related?.data ?? [])
    .filter((m) => m.slug !== meme.slug)
    .slice(0, 10)
    .map((m) => ({ ...m, href: localePath(locale, m.href) }));

  const categoryDisplay = humanize(meme.category);
  const breadcrumbs = [
    { name: t("breadcrumb_inicio"), href: "/" },
    { name: t("breadcrumb_categorias"), href: "/categorias" },
    { name: categoryDisplay, href: `/categorias/${meme.category}` },
    { name: meme.title, href: `/memes/${meme.category}/${meme.slug}` },
  ];

  const lang = localeLangMap[locale];

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
                      <Link href={localePath(locale, b.href)}>{b.name}</Link>
                    ) : (
                      <span aria-current="page">{b.name}</span>
                    )}
                  </li>
                ))}
              </ol>
            </nav>

            <article className={styles.hero}>
              <div
                className={styles.imageWrap}
                style={{ background: meme.placeholderGradient }}
              >
                {meme.imageUrl ? (
                  <Image
                    src={meme.imageUrl}
                    alt={meme.title}
                    fill
                    priority
                    className={styles.image}
                    sizes="(max-width: 800px) 100vw, 720px"
                    unoptimized={meme.format === "gif"}
                  />
                ) : (
                  <span className={styles.glyph} aria-hidden="true">
                    {meme.placeholder}
                  </span>
                )}
              </div>

              <header className={styles.meta}>
                <Link
                  href={localePath(locale, `/categorias/${meme.category}`)}
                  className={styles.categoryPill}
                >
                  {categoryDisplay}
                </Link>

                <h1 className={styles.title}>{meme.title}</h1>

                {meme.description ? (
                  <p className={styles.description}>{meme.description}</p>
                ) : null}

                <dl className={styles.stats}>
                  <div>
                    <dt>{t("score_label")}</dt>
                    <dd>{formatCompact(meme.score)}</dd>
                  </div>
                  {meme.author ? (
                    <div>
                      <dt>{t("autor_label")}</dt>
                      <dd>u/{meme.author}</dd>
                    </div>
                  ) : null}
                  {meme.subreddit ? (
                    <div>
                      <dt>{t("subreddit_label")}</dt>
                      <dd>r/{meme.subreddit}</dd>
                    </div>
                  ) : null}
                  <div>
                    <dt>{t("publicado_label")}</dt>
                    <dd>
                      <time dateTime={meme.createdAt}>
                        {new Date(meme.createdAt).toLocaleDateString(lang, {
                          day: "2-digit",
                          month: "long",
                          year: "numeric",
                        })}
                      </time>
                    </dd>
                  </div>
                </dl>

                {meme.tags.length ? (
                  <ul className={styles.tags} aria-label={t("etiquetas_label")}>
                    {meme.tags.map((tag) => (
                      <li key={tag}>
                        <Link
                          href={localePath(locale, `/buscar?q=${encodeURIComponent(tag)}`)}
                          className={styles.tag}
                        >
                          #{tag}
                        </Link>
                      </li>
                    ))}
                  </ul>
                ) : null}

                <div className={styles.actions}>
                  {meme.imageUrl ? (
                    <a
                      href={meme.imageUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className={styles.primary}
                      download
                    >
                      {t("descargar")}
                    </a>
                  ) : null}
                  {meme.postUrl ? (
                    <a
                      href={meme.postUrl}
                      target="_blank"
                      rel="noopener noreferrer nofollow"
                      className={styles.secondary}
                    >
                      {t("ver_reddit")}
                    </a>
                  ) : null}
                </div>
              </header>
            </article>

            {relatedMemes.length ? (
              <section
                aria-labelledby="related-title"
                className={styles.relatedWrap}
              >
                <SectionTitle id="related-title" icon={<span>🔥</span>}>
                  {t("related_title", { category: categoryDisplay })}
                </SectionTitle>
                <MemeListingGrid
                  memes={relatedMemes}
                  ariaLabel={t("related_aria", { category: categoryDisplay })}
                />
              </section>
            ) : null}
          </div>
        </main>

        <Footer />
      </div>

      <Script
        id="ld-meme"
        type="application/ld+json"
        strategy="beforeInteractive"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(memeImageObjectJsonLd(meme, locale)),
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
