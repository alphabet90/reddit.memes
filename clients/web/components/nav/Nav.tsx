import Link from "next/link";
import { Logo } from "./Logo";
import { SearchIcon, UploadIcon, UserIcon } from "@/components/icons";
import styles from "./Nav.module.css";

const links = [
  { href: "/",           label: "Memes",      active: true  },
  { href: "/categorias", label: "Categorías", active: false },
  { href: "/top",        label: "Top",        active: false },
  { href: "/nuevos",     label: "Nuevos",     active: false },
  { href: "/aleatorio",  label: "Aleatorio",  active: false },
];

/**
 * Top navigation bar. Renders as <header><nav> so search engines can
 * identify the primary site navigation.
 */
export function Nav() {
  return (
    <header className={styles.wrap}>
      <nav aria-label="Principal" className={styles.nav}>
        <Logo />
        <ul className={styles.links}>
          {links.map((l) => (
            <li key={l.href}>
              <Link
                href={l.href}
                className={`${styles.link} ${l.active ? styles.active : ""}`.trim()}
                aria-current={l.active ? "page" : undefined}
              >
                {l.label.toUpperCase()}
              </Link>
            </li>
          ))}
        </ul>

        <form
          action="/buscar"
          role="search"
          className={styles.search}
          aria-label="Buscar memes"
        >
          <SearchIcon size={14} />
          <input
            type="search"
            name="q"
            placeholder="Buscar memes…"
            aria-label="Buscar memes"
            autoComplete="off"
          />
        </form>

        <Link href="/subir" className={styles.cta}>
          <UploadIcon size={12} />
          <span>Subí tu meme</span>
        </Link>

        <Link href="/perfil" aria-label="Mi perfil" className={styles.avatar}>
          <UserIcon size={16} />
        </Link>
      </nav>
    </header>
  );
}
