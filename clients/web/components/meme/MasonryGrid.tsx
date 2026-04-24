"use client";

import { useState } from "react";
import type { Meme } from "@/lib/types";
import { MemeCard } from "./MemeCard";
import styles from "./MasonryGrid.module.css";

type MasonryGridProps = {
  memes: Meme[];
  ariaLabel: string;
};

/** Pseudo-random but deterministic heights, keyed on meme id —
 *  avoids hydration mismatches and gives the block its editorial feel. */
function heightFor(id: string): number {
  const heights = [110, 120, 130, 140, 160, 170, 190, 200];
  let hash = 0;
  for (let i = 0; i < id.length; i++) {
    hash = (hash * 31 + id.charCodeAt(i)) >>> 0;
  }
  return heights[hash % heights.length];
}

/**
 * Masonry grid with a "Ver más" CSS-clipped reveal. Collapsed state
 * shows a fade-out overlay; expanded reveals the full column flow.
 * Uses CSS `columns` for true masonry without JS layout.
 */
export function MasonryGrid({ memes, ariaLabel }: MasonryGridProps) {
  const [expanded, setExpanded] = useState(false);

  return (
    <div className={styles.wrap}>
      <ul
        className={`${styles.grid} ${expanded ? styles.expanded : ""}`.trim()}
        aria-label={ariaLabel}
        role="list"
      >
        {memes.map((m) => (
          <li key={m.id} className={styles.item}>
            <MemeCard meme={m} height={heightFor(m.id)} />
          </li>
        ))}
      </ul>

      <div
        className={`${styles.fade} ${expanded ? styles.fadeHidden : ""}`.trim()}
        aria-hidden="true"
      />

      <div className={styles.more}>
        <button
          type="button"
          className={styles.btnMore}
          onClick={() => setExpanded((v) => !v)}
          aria-expanded={expanded}
        >
          {expanded ? "Ver menos" : "Ver más populares"}
        </button>
      </div>
    </div>
  );
}
