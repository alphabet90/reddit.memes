"use client";

import { useState } from "react";
import type { Meme } from "@/lib/types";
import { MemeCard } from "./MemeCard";
import styles from "./MasonryGrid.module.css";

type MasonryGridProps = {
  memes: Meme[];
  ariaLabel: string;
};

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
            <MemeCard meme={m} naturalSize />
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
