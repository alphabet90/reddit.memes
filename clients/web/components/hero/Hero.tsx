import { getLocale, getTranslations } from "next-intl/server";
import { SearchIcon, StarIcon, BoltIcon, SmileIcon, ShuffleIcon } from "@/components/icons";
import { localePath } from "@/lib/i18n-utils";
import type { Locale } from "@/i18n/routing";
import styles from "./Hero.module.css";

export async function Hero() {
  const locale = (await getLocale()) as Locale;
  const t = await getTranslations("hero");

  const filters = [
    { id: "top",      label: t("filter_top"),      Icon: StarIcon,    active: true  },
    { id: "nuevos",   label: t("filter_nuevos"),   Icon: BoltIcon,    active: false },
    { id: "clasicos", label: t("filter_clasicos"), Icon: SmileIcon,   active: false },
    { id: "random",   label: t("filter_random"),   Icon: ShuffleIcon, active: false },
  ];

  return (
    <section className={styles.hero} aria-labelledby="hero-title">
      <div className="container">
        <div className={styles.inner}>
          <div className={styles.content}>
            <p className={styles.eyebrow}>
              <span aria-hidden="true">🔥</span> {t("eyebrow")}
            </p>

            <h1 id="hero-title" className={styles.title}>
              <span>{t("title_line1")}</span>
              <span className={styles.highlight}>{t("title_highlight")}</span>
              <span>{t("title_line2")}</span>
              <span>{t("title_line3")}</span>
            </h1>

            <p className={styles.sub}>
              {t("sub")}
              <br />
              <a href={localePath(locale, "/manifiesto")}>{t("sub_link")}</a>
            </p>

            <form
              action={localePath(locale, "/buscar")}
              role="search"
              aria-label={t("search_label")}
              className={styles.search}
            >
              <label className={styles.searchField}>
                <span className="sr-only">{t("search_label")}</span>
                <SearchIcon size={16} />
                <input
                  type="search"
                  name="q"
                  placeholder={t("search_placeholder")}
                  autoComplete="off"
                />
              </label>
              <button type="submit" className={styles.searchBtn} aria-label={t("search_label")}>
                <SearchIcon size={16} />
              </button>
            </form>

            <div
              className={styles.pills}
              role="tablist"
              aria-label={t("search_label")}
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
