import type { MetadataRoute } from "next";
import { locales } from "@/i18n/routing";
import { site } from "@/lib/site";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: "*",
        allow: "/",
        disallow: ["/api/", "/perfil/", "/subir/enviado"],
      },
    ],
    sitemap: locales.map((l) => `${site.url}/${l}/sitemap.xml`),
    host: site.url,
  };
}
