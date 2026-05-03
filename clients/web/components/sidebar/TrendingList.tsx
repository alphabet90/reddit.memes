import Link from "next/link";
import { getLocale, getTranslations } from "next-intl/server";
import type { TrendingTag } from "@/lib/types";
import { formatCompact } from "@/lib/format";
import { localePath } from "@/lib/i18n-utils";
import type { Locale } from "@/i18n/routing";
import styles from "./TrendingList.module.css";

function rankColor(rank: number) {
  if (rank === 1) return "var(--accent-primary)";
  if (rank === 2) return "var(--color-gris-humo)";
  if (rank === 3) return "var(--badge-top)";
  return "var(--color-negro-muted)";
}

export async function TrendingList({ tags }: { tags: TrendingTag[] }) {
  const locale = (await getLocale()) as Locale;
  const t = await getTranslations("sidebar");

  return (
    <div className={styles.widget}>
      <h2 className={styles.title}>{t("trending_title")}</h2>
      <ul className={styles.list} role="list">
        {tags.map((tag, i) => (
          <li
            key={tag.tag}
            className={`${styles.row} ${i < tags.length - 1 ? styles.rowBorder : ""}`.trim()}
          >
            <Link
              href={localePath(
                locale,
                `/buscar?q=${encodeURIComponent(tag.tag.replace(/^#/, ""))}`,
              )}
              className={styles.link}
            >
              <span className={styles.rank} style={{ color: rankColor(tag.rank) }}>
                {tag.rank}
              </span>
              <span className={styles.tag}>{tag.tag}</span>
              <span className={styles.count}>{formatCompact(tag.count)}</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
