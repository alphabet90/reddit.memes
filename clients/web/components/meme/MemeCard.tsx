import Image from "next/image";
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
  /** Lazy-load hint — true for above-the-fold cards */
  priority?: boolean;
  /** `sizes` hint for the optimizer (responsive layout). */
  sizes?: string;
  /** Render image at its natural aspect ratio instead of a fixed box */
  naturalSize?: boolean;
};

function rankColor(rank: number) {
  if (rank === 1) return { bg: "var(--accent-primary)", fg: "var(--color-negro)" };
  if (rank === 2) return { bg: "var(--color-gris-humo)", fg: "var(--color-negro)" };
  if (rank === 3) return { bg: "var(--badge-top)", fg: "#fff" };
  return { bg: "rgba(0,0,0,0.55)", fg: "var(--color-gris-humo)" };
}

/**
 * MemeCard — single visual primitive for grids and masonry. The
 * <Link> wraps the entire surface so each card is one tap target,
 * which also helps SEO crawl every meme via a stable href.
 *
 * Image strategy:
 * - When `meme.imageUrl` is present, render <Image fill> against
 *   the placeholder gradient (gives a graceful fallback if the
 *   image 404s) with `loading=lazy` by default.
 * - When absent, only the gradient + glyph render — same layout.
 */
export function MemeCard({
  meme,
  rank,
  aspectRatio = "1 / 1",
  height,
  priority = false,
  sizes = "(max-width: 540px) 50vw, (max-width: 820px) 33vw, (max-width: 1100px) 25vw, 256px",
  naturalSize = false,
}: MemeCardProps) {
  const badge = rank ? rankColor(rank) : null;
  const showImage = Boolean(meme.imageUrl);

  return (
    <Link
      href={meme.href}
      className={styles.card}
      aria-label={`${meme.title} — ${formatCompact(meme.score)} puntos`}
      prefetch={priority ? true : null}
    >
      <article className={styles.article}>
        <div
          className={naturalSize ? styles.imageNatural : styles.image}
          style={{
            background: meme.placeholderGradient,
            ...(naturalSize
              ? {}
              : {
                  aspectRatio: height ? undefined : aspectRatio,
                  height: height ? `${height}px` : undefined,
                }),
          }}
        >
          {showImage ? (
            naturalSize ? (
              <Image
                src={meme.imageUrl}
                alt={meme.title}
                width={0}
                height={0}
                sizes={sizes}
                className={styles.imgNatural}
                priority={priority}
                loading={priority ? "eager" : "lazy"}
                unoptimized={meme.format === "gif"}
                style={{ width: "100%", height: "auto", display: "block" }}
              />
            ) : (
              <Image
                src={meme.imageUrl}
                alt={meme.title}
                fill
                sizes={sizes}
                className={styles.img}
                priority={priority}
                loading={priority ? "eager" : "lazy"}
                unoptimized={meme.format === "gif"}
              />
            )
          ) : (
            <span aria-hidden="true">{meme.placeholder}</span>
          )}

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
          {formatCompact(meme.score)}
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
