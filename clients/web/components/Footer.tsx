import Link from "next/link";
import { site } from "@/lib/site";
import styles from "./Footer.module.css";

const primaryLinks = [
  { href: "/",            label: "Inicio" },
  { href: "/top",         label: "Top" },
  { href: "/nuevos",      label: "Nuevos" },
  { href: "/clasicos",    label: "Clásicos" },
  { href: "/aleatorio",   label: "Aleatorio" },
  { href: "/subir",       label: "Subí tu meme" },
];

const categoryLinks = [
  { href: "/categorias/la-vida",    label: "La Vida" },
  { href: "/categorias/politica",   label: "Política" },
  { href: "/categorias/futbol",     label: "Fútbol" },
  { href: "/categorias/argentinos", label: "Argentinos" },
  { href: "/categorias/clasicos",   label: "Clásicos" },
  { href: "/categorias/random",     label: "Random" },
];

const legalLinks = [
  { href: "/terminos",  label: "Términos" },
  { href: "/privacidad", label: "Privacidad" },
  { href: "/dmca",      label: "DMCA" },
  { href: "/contacto",  label: "Contacto" },
];

/**
 * Footer — dense internal linking for SEO crawl depth. Not
 * gigantic on purpose: a cluttered footer dilutes link equity.
 */
export function Footer() {
  return (
    <footer className={styles.footer}>
      <div className="container">
        <div className={styles.grid}>
          <div>
            <p className={styles.brand}>
              <span>Ope</span>
              <span className={styles.accent}>n</span>
              <span>Meme</span>
            </p>
            <p className={styles.copy}>
              Hecho en Argentina. Subido por la gente. Para la gente.
            </p>
          </div>

          <nav aria-label="Navegación del pie">
            <h3 className={styles.colTitle}>Explorar</h3>
            <ul>
              {primaryLinks.map((l) => (
                <li key={l.href}>
                  <Link href={l.href}>{l.label}</Link>
                </li>
              ))}
            </ul>
          </nav>

          <nav aria-label="Categorías">
            <h3 className={styles.colTitle}>Categorías</h3>
            <ul>
              {categoryLinks.map((l) => (
                <li key={l.href}>
                  <Link href={l.href}>{l.label}</Link>
                </li>
              ))}
            </ul>
          </nav>

          <nav aria-label="Información legal">
            <h3 className={styles.colTitle}>Legal</h3>
            <ul>
              {legalLinks.map((l) => (
                <li key={l.href}>
                  <Link href={l.href}>{l.label}</Link>
                </li>
              ))}
            </ul>
          </nav>
        </div>

        <div className={styles.bottom}>
          <small>
            © {new Date().getFullYear()} {site.legalName}. Todos los memes son de sus respectivos autores.
          </small>
        </div>
      </div>
    </footer>
  );
}
