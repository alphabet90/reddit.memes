import Link from "next/link";
import { getLocale, getTranslations } from "next-intl/server";
import { Logo } from "./Logo";
import { SearchIcon, UploadIcon, UserIcon } from "@/components/icons";
import { localePath } from "@/lib/i18n-utils";
import type { Locale } from "@/i18n/routing";
import styles from "./Nav.module.css";

export async function Nav() {
  const locale = (await getLocale()) as Locale;
  const t = await getTranslations("nav");

  const links = [
    { href: localePath(locale, "/"),           label: t("memes"),     active: true  },
    { href: localePath(locale, "/categorias"), label: t("categorias"), active: false },
    { href: localePath(locale, "/top"),        label: t("top"),       active: false },
    { href: localePath(locale, "/nuevos"),     label: t("nuevos"),    active: false },
    { href: localePath(locale, "/aleatorio"),  label: t("aleatorio"), active: false },
  ];

  return (
    <header className={styles.wrap}>
      <nav aria-label="Principal" className={styles.nav}>
        <Logo />
        <ul className={styles.links}>
          {links.map((l) => (
            <li key={l.href}>
              <Link
                href={l.href}
                className={`${styles.link} ${l.active ? styles.active : ""}`.trim()}
                aria-current={l.active ? "page" : undefined}
              >
                {l.label.toUpperCase()}
              </Link>
            </li>
          ))}
        </ul>

        <form
          action={localePath(locale, "/buscar")}
          role="search"
          className={styles.search}
          aria-label={t("buscar_label")}
        >
          <SearchIcon size={14} />
          <input
            type="search"
            name="q"
            placeholder={t("buscar_placeholder")}
            aria-label={t("buscar_label")}
            autoComplete="off"
          />
        </form>

        <Link href={localePath(locale, "/subir")} className={styles.cta}>
          <UploadIcon size={12} />
          <span>{t("subir")}</span>
        </Link>

        <Link
          href={localePath(locale, "/perfil")}
          aria-label={t("perfil_label")}
          className={styles.avatar}
        >
          <UserIcon size={16} />
        </Link>
      </nav>
    </header>
  );
}
