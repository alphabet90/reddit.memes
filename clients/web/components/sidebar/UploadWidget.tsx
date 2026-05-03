import Link from "next/link";
import { getLocale, getTranslations } from "next-intl/server";
import { UploadIcon } from "@/components/icons";
import { localePath } from "@/lib/i18n-utils";
import type { Locale } from "@/i18n/routing";
import styles from "./UploadWidget.module.css";

export async function UploadWidget() {
  const locale = (await getLocale()) as Locale;
  const t = await getTranslations("sidebar");

  return (
    <aside className={styles.widget} aria-labelledby="upload-widget-title">
      <h2 id="upload-widget-title" className={styles.title}>
        {t("upload_title")}
      </h2>
      <p className={styles.copy}>{t("upload_copy")}</p>
      <Link href={localePath(locale, "/subir")} className={styles.cta}>
        <UploadIcon size={13} />
        <span>{t("upload_cta")}</span>
      </Link>
    </aside>
  );
}
