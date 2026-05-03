import Link from "next/link";
import { getLocale } from "next-intl/server";
import { site } from "@/lib/site";
import { localePath } from "@/lib/i18n-utils";
import type { Locale } from "@/i18n/routing";
import styles from "./Logo.module.css";

type LogoProps = { className?: string };

export async function Logo({ className }: LogoProps) {
  const locale = (await getLocale()) as Locale;

  return (
    <Link
      href={localePath(locale, "/")}
      aria-label={`${site.name} — Inicio`}
      className={`${styles.logo} ${className ?? ""}`.trim()}
    >
      <span aria-hidden="true">OPEN</span>
      <span aria-hidden="true" className={styles.accent}>MEME</span>
    </Link>
  );
}
