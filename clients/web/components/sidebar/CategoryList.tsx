import Link from "next/link";
import { getLocale, getTranslations } from "next-intl/server";
import type { Category } from "@/lib/types";
import { CategoryIconGlyph } from "@/components/icons";
import { formatCompact } from "@/lib/format";
import { localePath } from "@/lib/i18n-utils";
import type { Locale } from "@/i18n/routing";
import styles from "./CategoryList.module.css";

type CategoryListProps = {
  categories: Category[];
  activeSlug?: string;
};

export async function CategoryList({ categories, activeSlug }: CategoryListProps) {
  const locale = (await getLocale()) as Locale;
  const t = await getTranslations("sidebar");

  return (
    <div className={styles.widget}>
      <h2 className={styles.title}>{t("categories_title")}</h2>
      <ul className={styles.list} role="list">
        {categories.map((c) => {
          const isActive = c.slug === activeSlug;
          return (
            <li key={c.slug}>
              <Link
                href={localePath(locale, `/categorias/${c.slug}`)}
                className={`${styles.item} ${isActive ? styles.active : ""}`.trim()}
                aria-current={isActive ? "page" : undefined}
              >
                <span className={styles.left}>
                  <CategoryIconGlyph
                    name={c.iconName}
                    size={14}
                    color={isActive ? "var(--accent-primary)" : "var(--fg-secondary)"}
                  />
                  <span className={styles.name}>{c.name}</span>
                </span>
                <span
                  className={styles.count}
                  style={isActive ? { color: "var(--accent-primary)" } : undefined}
                >
                  {formatCompact(c.count)}
                </span>
              </Link>
            </li>
          );
        })}
      </ul>
      <Link href={localePath(locale, "/categorias")} className={styles.verTodas}>
        {t("ver_todas")}
      </Link>
    </div>
  );
}
