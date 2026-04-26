import Link from "next/link";
import styles from "./Pagination.module.css";

type PaginationProps = {
  /** 0-indexed current page (matches the API). */
  page: number;
  totalPages: number;
  /** Builds the href for a specific page number. */
  buildHref: (page: number) => string;
  /** Optional aria-label override for the nav landmark. */
  label?: string;
};

const WINDOW = 2;

function pageNumbers(current: number, total: number): (number | "…")[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i);
  const out: (number | "…")[] = [];
  const left = Math.max(1, current - WINDOW);
  const right = Math.min(total - 2, current + WINDOW);
  out.push(0);
  if (left > 1) out.push("…");
  for (let i = left; i <= right; i++) out.push(i);
  if (right < total - 2) out.push("…");
  out.push(total - 1);
  return out;
}

/**
 * SEO-friendly pagination. Renders real anchors so crawlers can
 * follow them, plus rel="prev"/"next" for explicit pagination
 * signals (still supported by Bing and a few others; cheap to add).
 */
export function Pagination({ page, totalPages, buildHref, label = "Paginación" }: PaginationProps) {
  if (totalPages <= 1) return null;
  const pages = pageNumbers(page, totalPages);

  const prevHref = page > 0 ? buildHref(page - 1) : null;
  const nextHref = page < totalPages - 1 ? buildHref(page + 1) : null;

  return (
    <nav aria-label={label} className={styles.wrap}>
      {prevHref ? (
        <Link href={prevHref} rel="prev" className={styles.step}>
          ← Anterior
        </Link>
      ) : (
        <span className={`${styles.step} ${styles.disabled}`} aria-disabled="true">
          ← Anterior
        </span>
      )}

      <ul className={styles.list} role="list">
        {pages.map((p, i) =>
          p === "…" ? (
            <li key={`gap-${i}`} className={styles.gap} aria-hidden="true">
              …
            </li>
          ) : (
            <li key={p}>
              {p === page ? (
                <span className={`${styles.num} ${styles.current}`} aria-current="page">
                  {p + 1}
                </span>
              ) : (
                <Link href={buildHref(p)} className={styles.num}>
                  {p + 1}
                </Link>
              )}
            </li>
          ),
        )}
      </ul>

      {nextHref ? (
        <Link href={nextHref} rel="next" className={styles.step}>
          Siguiente →
        </Link>
      ) : (
        <span className={`${styles.step} ${styles.disabled}`} aria-disabled="true">
          Siguiente →
        </span>
      )}
    </nav>
  );
}
