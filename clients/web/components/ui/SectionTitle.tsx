import styles from "./SectionTitle.module.css";
import type { ReactNode } from "react";

type SectionTitleProps = {
  /** Visible heading text (rendered uppercase via CSS) */
  children: ReactNode;
  /** Leading glyph/emoji — decorative */
  icon?: ReactNode;
  /** Heading level — defaults to h2 */
  as?: "h1" | "h2" | "h3";
  id?: string;
};

export function SectionTitle({
  children,
  icon,
  as: As = "h2",
  id,
}: SectionTitleProps) {
  return (
    <As id={id} className={styles.title}>
      {icon ? <span className={styles.icon} aria-hidden="true">{icon}</span> : null}
      <span>{children}</span>
    </As>
  );
}
