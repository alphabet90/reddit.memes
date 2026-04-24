import Link from "next/link";
import type { Meme } from "@/lib/types";
import { CopyIcon, EyeIcon, ShareIcon } from "@/components/icons";
import { formatCompact } from "@/lib/format";
import styles from "./MemeCard.module.css";

type MemeCardProps = {
  meme: Meme;
  /** 1-based rank → renders the rank badge (top-3 get brand colors) */
  rank?: number;
  /** Override intrinsic aspect; used by the masonry grid */
  aspectRatio?: string;
  /** Forces fixed height on the image (masonry) */
  height?: number;
  /** Lazy-load hint — default true */
  priority?: boolean;
};

function rankColor(rank: number) {
  if (rank === 1) return { bg: "var(--accent-primary)", fg: "var(--color-negro)" };
  if (rank === 2) return { bg: "var(--color-gris-humo)", fg: "var(--color-negro)" };
  if (rank === 3) return { bg: "var(--badge-top)", fg: "#fff" };
  return { bg: "rgba(0,0,0,0.55)", fg: "var(--color-gris-humo)" };
}

/**
 * MemeCard — the single visual primitive used by both the top grid
 * and the masonry grid. Wraps the whole card in an <a> so every meme
 * is indexable and keyboard-navigable.
 */
export function MemeCard({
  meme,
  rank,
  aspectRatio = "1 / 1",
  height,
  priority = false,
}: MemeCardProps) {
  const badge = rank ? rankColor(rank) : null;

  return (
    <Link
      href={`/memes/${meme.slug}`}
      className={styles.card}
      aria-label={`${meme.title} — ${formatCompact(meme.views)} vistas`}
      prefetch={priority ? true : null}
    >
      <article className={styles.article}>
        <div
          className={styles.image}
          style={{
            background: meme.placeholderGradient,
            aspectRatio: height ? undefined : aspectRatio,
            height: height ? `${height}px` : undefined,
          }}
          role="img"
          aria-label={meme.title}
        >
          <span aria-hidden="true">{meme.placeholder}</span>

          {badge ? (
            <span
              className={styles.rank}
              style={{ background: badge.bg, color: badge.fg }}
              aria-label={`Puesto ${rank}`}
            >
              {rank}
            </span>
          ) : null}

          {meme.isNew ? <span className={styles.nuevo}>Nuevo</span> : null}
        </div>

        <div className={styles.overlay} aria-hidden="true" />

        <span className={styles.views}>
          <EyeIcon size={9} />
          {formatCompact(meme.views)}
        </span>

        <div className={styles.actions} aria-hidden="true">
          <span className={styles.actionBtn}>
            <CopyIcon size={11} />
          </span>
          <span className={styles.actionBtn}>
            <ShareIcon size={11} />
          </span>
        </div>

        <h3 className={styles.srOnly}>{meme.title}</h3>
      </article>
    </Link>
  );
}
