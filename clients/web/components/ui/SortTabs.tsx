import Link from "next/link";
import styles from "./SortTabs.module.css";

type Option = {
  value: string;
  label: string;
};

type SortTabsProps = {
  options: Option[];
  active: string;
  buildHref: (value: string) => string;
  ariaLabel?: string;
};

/**
 * Tabs that map to an `?sort=` query param. Renders real anchors
 * so the variant pages stay crawlable without JS.
 */
export function SortTabs({ options, active, buildHref, ariaLabel = "Orden" }: SortTabsProps) {
  return (
    <div role="tablist" aria-label={ariaLabel} className={styles.wrap}>
      {options.map((opt) => {
        const selected = opt.value === active;
        return (
          <Link
            key={opt.value}
            href={buildHref(opt.value)}
            role="tab"
            aria-selected={selected}
            className={`${styles.tab} ${selected ? styles.active : ""}`.trim()}
            scroll={false}
          >
            {opt.label}
          </Link>
        );
      })}
    </div>
  );
}
