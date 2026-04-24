import { CategoryList } from "./CategoryList";
import { TrendingList } from "./TrendingList";
import { UploadWidget } from "./UploadWidget";
import type { Category, TrendingTag } from "@/lib/types";
import styles from "./Sidebar.module.css";

type SidebarProps = {
  categories: Category[];
  trending: TrendingTag[];
  activeCategorySlug?: string;
};

/**
 * Right-rail sidebar — complementary content by definition,
 * hence an <aside>. Individual widgets are self-contained so
 * the list can grow without refactoring the shell.
 */
export function Sidebar({
  categories,
  trending,
  activeCategorySlug,
}: SidebarProps) {
  return (
    <aside className={styles.sidebar} aria-label="Explorar">
      <CategoryList categories={categories} activeSlug={activeCategorySlug} />
      <TrendingList tags={trending} />
      <UploadWidget />
    </aside>
  );
}
