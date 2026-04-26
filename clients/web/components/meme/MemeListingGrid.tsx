import type { Meme } from "@/lib/types";
import { MemeCard } from "./MemeCard";
import styles from "./MemeListingGrid.module.css";

type MemeListingGridProps = {
  memes: Meme[];
  ariaLabel: string;
  /** First N cards get priority loading. */
  priorityCount?: number;
};

/**
 * 5-col responsive grid for category and search pages. Differs
 * from MemeGrid only in default densities (more rows, no rank
 * badges). Pure server component.
 */
export function MemeListingGrid({
  memes,
  ariaLabel,
  priorityCount = 4,
}: MemeListingGridProps) {
  return (
    <ul className={styles.grid} aria-label={ariaLabel} role="list">
      {memes.map((m, i) => (
        <li key={m.id}>
          <MemeCard
            meme={m}
            priority={i < priorityCount}
            naturalSize
            sizes="(max-width: 540px) 50vw, (max-width: 820px) 33vw, (max-width: 1100px) 25vw, 20vw"
          />
        </li>
      ))}
    </ul>
  );
}
