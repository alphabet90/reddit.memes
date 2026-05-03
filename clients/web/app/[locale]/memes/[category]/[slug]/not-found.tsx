import Link from "next/link";
import { getTranslations } from "next-intl/server";
import { getLocale } from "next-intl/server";
import { Nav } from "@/components/nav/Nav";
import { Footer } from "@/components/Footer";
import { EmptyState } from "@/components/ui/EmptyState";
import { localePath } from "@/lib/i18n-utils";
import type { Locale } from "@/i18n/routing";

export default async function MemeNotFound() {
  const locale = (await getLocale()) as Locale;
  const t = await getTranslations({ locale, namespace: "not_found" });

  return (
    <>
      <Nav />
      <main id="contenido" style={{ paddingTop: "var(--nav-height)" }}>
        <div className="container" style={{ padding: "var(--space-12) 0" }}>
          <EmptyState
            title={t("meme_title")}
            description={t("meme_desc")}
            ctaLabel={t("meme_cta")}
            ctaHref={localePath(locale, "/")}
            glyph="🤷"
          />
          <p style={{ textAlign: "center", marginTop: "var(--space-4)" }}>
            <Link
              href={localePath(locale, "/buscar")}
              style={{ color: "var(--accent-primary)" }}
            >
              {t("search_link")}
            </Link>
          </p>
        </div>
      </main>
      <Footer />
    </>
  );
}
