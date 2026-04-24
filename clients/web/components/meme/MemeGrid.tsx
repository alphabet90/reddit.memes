import type { Meme } from "@/lib/types";
import { MemeCard } from "./MemeCard";
import styles from "./MemeGrid.module.css";

type MemeGridProps = {
  memes: Meme[];
  /** When true, renders rank badges 1..N on each card */
  ranked?: boolean;
  /** Accessible heading rendered for screen readers */
  ariaLabel: string;
};

/**
 * 5-column responsive grid — used for "Top memes del día".
 * Pure server component; no client-side state.
 */
export function MemeGrid({ memes, ranked = false, ariaLabel }: MemeGridProps) {
  return (
    <ul className={styles.grid} aria-label={ariaLabel} role="list">
      {memes.map((m, i) => (
        <li key={m.id}>
          <MemeCard
            meme={m}
            rank={ranked ? i + 1 : undefined}
            priority={i < 5}
          />
        </li>
      ))}
    </ul>
  );
}
