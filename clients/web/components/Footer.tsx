import Link from "next/link";
import { getLocale, getTranslations } from "next-intl/server";
import { site } from "@/lib/site";
import { localePath } from "@/lib/i18n-utils";
import type { Locale } from "@/i18n/routing";
import styles from "./Footer.module.css";

export async function Footer() {
  const locale = (await getLocale()) as Locale;
  const t = await getTranslations("footer");

  const primaryLinks = [
    { href: localePath(locale, "/"),          label: t("inicio") },
    { href: localePath(locale, "/top"),       label: t("top") },
    { href: localePath(locale, "/nuevos"),    label: t("nuevos") },
    { href: localePath(locale, "/clasicos"),  label: t("clasicos") },
    { href: localePath(locale, "/aleatorio"), label: t("aleatorio") },
    { href: localePath(locale, "/subir"),     label: t("subir") },
  ];

  const categoryLinks = [
    { href: localePath(locale, "/categorias/la-vida"),    label: "La Vida" },
    { href: localePath(locale, "/categorias/politica"),   label: "Política" },
    { href: localePath(locale, "/categorias/futbol"),     label: "Fútbol" },
    { href: localePath(locale, "/categorias/argentinos"), label: "Argentinos" },
    { href: localePath(locale, "/categorias/clasicos"),   label: "Clásicos" },
    { href: localePath(locale, "/categorias/random"),     label: "Random" },
  ];

  const legalLinks = [
    { href: localePath(locale, "/terminos"),   label: t("terminos") },
    { href: localePath(locale, "/privacidad"), label: t("privacidad") },
    { href: localePath(locale, "/dmca"),       label: t("dmca") },
    { href: localePath(locale, "/contacto"),   label: t("contacto") },
  ];

  return (
    <footer className={styles.footer}>
      <div className="container">
        <div className={styles.grid}>
          <div>
            <p className={styles.brand}>
              <span>OPEN</span>
              <span className={styles.accent}>MEME</span>
            </p>
            <p className={styles.copy}>{t("tagline")}</p>
          </div>

          <nav aria-label={t("nav_label")}>
            <h3 className={styles.colTitle}>{t("explore")}</h3>
            <ul>
              {primaryLinks.map((l) => (
                <li key={l.href}>
                  <Link href={l.href}>{l.label}</Link>
                </li>
              ))}
            </ul>
          </nav>

          <nav aria-label={t("categories_label")}>
            <h3 className={styles.colTitle}>{t("categories")}</h3>
            <ul>
              {categoryLinks.map((l) => (
                <li key={l.href}>
                  <Link href={l.href}>{l.label}</Link>
                </li>
              ))}
            </ul>
          </nav>

          <nav aria-label={t("legal_label")}>
            <h3 className={styles.colTitle}>{t("legal")}</h3>
            <ul>
              {legalLinks.map((l) => (
                <li key={l.href}>
                  <Link href={l.href}>{l.label}</Link>
                </li>
              ))}
            </ul>
          </nav>
        </div>

        <div className={styles.bottom}>
          <small>
            © {new Date().getFullYear()} {site.legalName}. {t("copyright")}
          </small>
        </div>
      </div>
    </footer>
  );
}
