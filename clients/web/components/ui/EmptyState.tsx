import Link from "next/link";
import styles from "./EmptyState.module.css";

type EmptyStateProps = {
  title: string;
  description?: string;
  ctaLabel?: string;
  ctaHref?: string;
  glyph?: string;
};

/** Used when a filter / search returns zero memes. */
export function EmptyState({
  title,
  description,
  ctaLabel = "Volver al inicio",
  ctaHref = "/",
  glyph = "🤷",
}: EmptyStateProps) {
  return (
    <div className={styles.wrap} role="status" aria-live="polite">
      <div className={styles.glyph} aria-hidden="true">
        {glyph}
      </div>
      <h2 className={styles.title}>{title}</h2>
      {description ? <p className={styles.copy}>{description}</p> : null}
      <Link href={ctaHref} className={styles.cta}>
        {ctaLabel}
      </Link>
    </div>
  );
}
