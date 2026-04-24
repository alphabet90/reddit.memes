import Link from "next/link";
import { UploadIcon } from "@/components/icons";
import styles from "./UploadWidget.module.css";

export function UploadWidget() {
  return (
    <aside className={styles.widget} aria-labelledby="upload-widget-title">
      <h2 id="upload-widget-title" className={styles.title}>
        Subí tu meme
      </h2>
      <p className={styles.copy}>
        Pasá a la historia. Subido por la gente, para la gente.
      </p>
      <Link href="/subir" className={styles.cta}>
        <UploadIcon size={13} />
        <span>Subí tu meme</span>
      </Link>
    </aside>
  );
}
