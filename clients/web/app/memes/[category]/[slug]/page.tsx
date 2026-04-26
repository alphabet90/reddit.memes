import type { Metadata } from "next";
import Image from "next/image";
import Link from "next/link";
import Script from "next/script";
import { notFound } from "next/navigation";

import { Nav } from "@/components/nav/Nav";
import { Footer } from "@/components/Footer";
import { MemeListingGrid } from "@/components/meme/MemeListingGrid";
import { SectionTitle } from "@/components/ui/SectionTitle";

import { getCategoryListing, getMeme } from "@/lib/data/memes";
import { formatCompact } from "@/lib/format";
import {
  breadcrumbJsonLd,
  memeImageObjectJsonLd,
} from "@/lib/seo";
import { site } from "@/lib/site";

import styles from "./page.module.css";

/** Re-render every hour — meme metadata rarely changes. */
export const revalidate = 3600;

type RouteParams = { category: string; slug: string };

type Props = {
  params: Promise<RouteParams>;
};

function humanize(slug: string): string {
  return slug
    .split(/[-_]/)
    .filter(Boolean)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(" ");
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { category, slug } = await params;
  const meme = await getMeme(category, slug);
  if (!meme) return { title: "Meme no encontrado" };

  const canonical = `/memes/${meme.category}/${meme.slug}`;
  const description =
    meme.description ??
    `${meme.title} — meme argentino subido por la gente. Descargá y compartí en ${site.name}.`;

  const images = meme.imageUrl
    ? [{ url: meme.imageUrl, alt: meme.title }]
    : [{ url: site.ogImage, alt: site.name }];

  return {
    title: meme.title,
    description,
    alternates: { canonical },
    openGraph: {
      title: meme.title,
      description,
      type: "article",
      url: canonical,
      images,
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
  const { category, slug } = await params;
  const meme = await getMeme(category, slug);
  if (!meme) notFound();

  const related = await getCategoryListing(meme.category, { limit: 12, sort: "score" });
  const relatedMemes = (related?.data ?? []).filter((m) => m.slug !== meme.slug).slice(0, 10);

  const breadcrumbs = [
    { name: "Inicio", href: "/" },
    { name: "Categorías", href: "/categorias" },
    { name: humanize(meme.category), href: `/categorias/${meme.category}` },
    { name: meme.title, href: meme.href },
  ];

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
                      <Link href={b.href}>{b.name}</Link>
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
                  href={`/categorias/${meme.category}`}
                  className={styles.categoryPill}
                >
                  {humanize(meme.category)}
                </Link>

                <h1 className={styles.title}>{meme.title}</h1>

                {meme.description ? (
                  <p className={styles.description}>{meme.description}</p>
                ) : null}

                <dl className={styles.stats}>
                  <div>
                    <dt>Score</dt>
                    <dd>{formatCompact(meme.score)}</dd>
                  </div>
                  {meme.author ? (
                    <div>
                      <dt>Autor</dt>
                      <dd>u/{meme.author}</dd>
                    </div>
                  ) : null}
                  {meme.subreddit ? (
                    <div>
                      <dt>Subreddit</dt>
                      <dd>r/{meme.subreddit}</dd>
                    </div>
                  ) : null}
                  <div>
                    <dt>Publicado</dt>
                    <dd>
                      <time dateTime={meme.createdAt}>
                        {new Date(meme.createdAt).toLocaleDateString("es-AR", {
                          day: "2-digit",
                          month: "long",
                          year: "numeric",
                        })}
                      </time>
                    </dd>
                  </div>
                </dl>

                {meme.tags.length ? (
                  <ul className={styles.tags} aria-label="Etiquetas">
                    {meme.tags.map((t) => (
                      <li key={t}>
                        <Link
                          href={`/buscar?q=${encodeURIComponent(t)}`}
                          className={styles.tag}
                        >
                          #{t}
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
                      Descargar
                    </a>
                  ) : null}
                  {meme.postUrl ? (
                    <a
                      href={meme.postUrl}
                      target="_blank"
                      rel="noopener noreferrer nofollow"
                      className={styles.secondary}
                    >
                      Ver en Reddit
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
                  Más de {humanize(meme.category)}
                </SectionTitle>
                <MemeListingGrid
                  memes={relatedMemes}
                  ariaLabel={`Más memes de ${humanize(meme.category)}`}
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
          __html: JSON.stringify(memeImageObjectJsonLd(meme)),
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
