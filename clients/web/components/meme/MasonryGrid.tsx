import Link from "next/link";
import type { Meme } from "@/lib/types";
import { MemeCard } from "./MemeCard";
import styles from "./MasonryGrid.module.css";

type MasonryGridProps = {
  memes: Meme[];
  ariaLabel: string;
  moreHref?: string;
};

export function MasonryGrid({ memes, ariaLabel, moreHref }: MasonryGridProps) {
  return (
    <div className={styles.wrap}>
      <ul className={styles.grid} aria-label={ariaLabel} role="list">
        {memes.map((m) => (
          <li key={m.id} className={styles.item}>
            <MemeCard meme={m} naturalSize />
          </li>
        ))}
      </ul>

      {moreHref && (
        <div className={styles.more}>
          <Link href={moreHref} className={styles.btnMore}>
            Ver más
          </Link>
        </div>
      )}
    </div>
  );
}
