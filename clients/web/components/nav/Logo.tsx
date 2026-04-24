import Link from "next/link";
import styles from "./Logo.module.css";
import { site } from "@/lib/site";

type LogoProps = { className?: string };

/**
 * Brand wordmark. Renders as "MEMEAR_g_" in Anton, with the middle
 * "ME" (from memeARg) highlighted in lime. An accessible name is
 * provided via aria-label so screen readers announce "OpenMeme".
 */
export function Logo({ className }: LogoProps) {
  return (
    <Link
      href="/"
      aria-label={`${site.name} — Inicio`}
      className={`${styles.logo} ${className ?? ""}`.trim()}
    >
      <span aria-hidden="true">ME</span>
      <span aria-hidden="true" className={styles.accent}>ME</span>
      <span aria-hidden="true">AR</span>
      <span aria-hidden="true" className={styles.small}>g</span>
    </Link>
  );
}
