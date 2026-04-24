import Link from "next/link";
import type { Category } from "@/lib/types";
import { CategoryIconGlyph } from "@/components/icons";
import { formatCompact } from "@/lib/format";
import styles from "./CategoryList.module.css";

type CategoryListProps = {
  categories: Category[];
  /** Slug of the currently active category, if any */
  activeSlug?: string;
};

export function CategoryList({ categories, activeSlug }: CategoryListProps) {
  return (
    <div className={styles.widget}>
      <h2 className={styles.title}>Categorías</h2>
      <ul className={styles.list} role="list">
        {categories.map((c) => {
          const isActive = c.slug === activeSlug;
          return (
            <li key={c.slug}>
              <Link
                href={`/categorias/${c.slug}`}
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
      <Link href="/categorias" className={styles.verTodas}>
        Ver todas
      </Link>
    </div>
  );
}
