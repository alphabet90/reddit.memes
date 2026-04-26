import Link from "next/link";
import type { TrendingTag } from "@/lib/types";
import { formatCompact } from "@/lib/format";
import styles from "./TrendingList.module.css";

function rankColor(rank: number) {
  if (rank === 1) return "var(--accent-primary)";
  if (rank === 2) return "var(--color-gris-humo)";
  if (rank === 3) return "var(--badge-top)";
  return "var(--color-negro-muted)";
}

export function TrendingList({ tags }: { tags: TrendingTag[] }) {
  return (
    <div className={styles.widget}>
      <h2 className={styles.title}>Tendencias</h2>
      <ul className={styles.list} role="list">
        {tags.map((t, i) => (
          <li
            key={t.tag}
            className={`${styles.row} ${i < tags.length - 1 ? styles.rowBorder : ""}`.trim()}
          >
            <Link
              href={`/buscar?q=${encodeURIComponent(t.tag.replace(/^#/, ""))}`}
              className={styles.link}
            >
              <span className={styles.rank} style={{ color: rankColor(t.rank) }}>
                {t.rank}
              </span>
              <span className={styles.tag}>{t.tag}</span>
              <span className={styles.count}>{formatCompact(t.count)}</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
