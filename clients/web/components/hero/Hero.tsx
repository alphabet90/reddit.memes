import { SearchIcon, StarIcon, BoltIcon, SmileIcon, ShuffleIcon } from "@/components/icons";
import styles from "./Hero.module.css";

const filters = [
  { id: "top",     label: "Top",      Icon: StarIcon,    active: true  },
  { id: "nuevos",  label: "Nuevos",   Icon: BoltIcon,    active: false },
  { id: "clasicos",label: "Clásicos", Icon: SmileIcon,   active: false },
  { id: "random",  label: "Random",   Icon: ShuffleIcon, active: false },
];

/**
 * Hero — H1 + search + filter pills + collage visual.
 * SEO: wraps the primary H1 and a concise description. The
 * search is a real <form> GETting to /buscar so crawlers and
 * no-JS visitors both work.
 */
export function Hero() {
  return (
    <section className={styles.hero} aria-labelledby="hero-title">
      <div className="container">
        <div className={styles.inner}>
          <div className={styles.content}>
            <p className={styles.eyebrow}>
              <span aria-hidden="true">🔥</span> El repositorio más grande de Argentina
            </p>

            <h1 id="hero-title" className={styles.title}>
              <span>Todos los</span>
              <span className={styles.highlight}>memes.</span>
              <span>En un solo</span>
              <span>lugar.</span>
            </h1>

            <p className={styles.sub}>
              El repositorio de memes más grande de Argentina.
              <br />
              Subido por la gente.{" "}
              <a href="/manifiesto">Para la gente.</a>
            </p>

            <form
              action="/buscar"
              role="search"
              aria-label="Buscar memes"
              className={styles.search}
            >
              <label className={styles.searchField}>
                <span className="sr-only">Buscar memes</span>
                <SearchIcon size={16} />
                <input
                  type="search"
                  name="q"
                  placeholder="Buscar memes…"
                  autoComplete="off"
                />
              </label>
              <button type="submit" className={styles.searchBtn} aria-label="Buscar">
                <SearchIcon size={16} />
              </button>
            </form>

            <div
              className={styles.pills}
              role="tablist"
              aria-label="Filtrar memes"
            >
              {filters.map(({ id, label, Icon, active }) => (
                <button
                  key={id}
                  type="button"
                  role="tab"
                  aria-selected={active}
                  className={`${styles.pill} ${active ? styles.pillActive : ""}`.trim()}
                >
                  <Icon size={12} />
                  <span>{label.toUpperCase()}</span>
                </button>
              ))}
            </div>
          </div>

          {/* Decorative collage. aria-hidden because it's pure brand fluff. */}
          <div className={styles.visual} aria-hidden="true">
            <div className={styles.dog}>🐕</div>
            <span className={`${styles.sticker} ${styles.stickerTop}`}>¿Qué mirá bobo?</span>
            <span className={`${styles.sticker} ${styles.stickerBlue}`}>Sin memes no hay paraíso</span>
            <span className={`${styles.sticker} ${styles.stickerGhost}`}>Yo los vi</span>
          </div>
        </div>
      </div>
    </section>
  );
}
