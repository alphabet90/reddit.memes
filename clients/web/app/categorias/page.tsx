import type { Metadata } from "next";
import Link from "next/link";
import Script from "next/script";

import { Nav } from "@/components/nav/Nav";
import { Footer } from "@/components/Footer";
import { CategoryIconGlyph } from "@/components/icons";

import { getCategories } from "@/lib/data/categories";
import { breadcrumbJsonLd } from "@/lib/seo";
import { site } from "@/lib/site";
import { formatCompact } from "@/lib/format";

import styles from "./page.module.css";

export const revalidate = 1800;

export const metadata: Metadata = {
  title: "Categorías",
  description: `Explorá todas las categorías de ${site.name}. Memes argentinos organizados por temática: política, fútbol, vida cotidiana, clásicos y más.`,
  alternates: { canonical: "/categorias" },
  openGraph: {
    title: `Categorías — ${site.name}`,
    description: `Explorá todas las categorías de memes argentinos en ${site.name}.`,
    url: "/categorias",
  },
};

export default async function CategoriesPage() {
  const categories = await getCategories().catch(() => []);

  const breadcrumbs = [
    { name: "Inicio", href: "/" },
    { name: "Categorías", href: "/categorias" },
  ];

  return (
    <>
      <Nav />

      <div className={styles.page}>
        <main id="contenido" className={styles.main}>
          <div className="container">
            <header className={styles.header}>
              <p className={styles.eyebrow}>Explorar</p>
              <h1 className={styles.title}>Categorías</h1>
              <p className={styles.sub}>
                {categories.length} categorías ·{" "}
                {formatCompact(
                  categories.reduce((sum, c) => sum + c.count, 0),
                )}{" "}
                memes en total. Elegí tu veneno.
              </p>
            </header>

            <ul className={styles.grid} role="list">
              {categories.map((c) => (
                <li key={c.slug}>
                  <Link
                    href={`/categorias/${c.slug}`}
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
                      {formatCompact(c.count)} memes
                    </span>
                    <span className={styles.topScore}>
                      Top {formatCompact(c.topScore)} pts
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
          __html: JSON.stringify(breadcrumbJsonLd(breadcrumbs)),
        }}
      />
    </>
  );
}
