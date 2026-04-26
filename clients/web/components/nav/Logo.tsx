import Link from "next/link";
import styles from "./Logo.module.css";
import { site } from "@/lib/site";

type LogoProps = { className?: string };

export function Logo({ className }: LogoProps) {
  return (
    <Link
      href="/"
      aria-label={`${site.name} — Inicio`}
      className={`${styles.logo} ${className ?? ""}`.trim()}
    >
      <span aria-hidden="true">OPEN</span>
      <span aria-hidden="true" className={styles.accent}>MEME</span>
    </Link>
  );
}
