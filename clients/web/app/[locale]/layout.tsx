import type { Metadata, Viewport } from "next";
import { Space_Grotesk } from "next/font/google";
import localFont from "next/font/local";
import Script from "next/script";
import { notFound } from "next/navigation";
import { NextIntlClientProvider } from "next-intl";
import { getMessages, getTranslations } from "next-intl/server";

import { site } from "@/lib/site";
import { websiteJsonLd, organizationJsonLd } from "@/lib/seo";
import { locales, localeLangMap, localeOgMap, rtlLocales, type Locale } from "@/i18n/routing";
import { buildAlternates } from "@/lib/i18n-utils";

import "../globals.css";

const spaceGrotesk = Space_Grotesk({
  variable: "--font-ui",
  subsets: ["latin"],
  display: "swap",
  weight: ["300", "400", "500", "600", "700"],
});

const anton = localFont({
  variable: "--font-display",
  display: "swap",
  src: [
    {
      path: "../../public/fonts/Anton-Regular.ttf",
      weight: "400",
      style: "normal",
    },
  ],
});

export const viewport: Viewport = {
  themeColor: "#0D0D0D",
  width: "device-width",
  initialScale: 1,
  colorScheme: "dark",
};

export async function generateStaticParams() {
  return locales.map((locale) => ({ locale }));
}

type Props = { params: Promise<{ locale: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { locale: rawLocale } = await params;
  const locale = rawLocale as Locale;
  const t = await getTranslations({ locale, namespace: "site" });

  return {
    metadataBase: new URL(site.url),
    title: {
      default: `${site.name} — ${t("tagline")}`,
      template: `%s · ${site.name}`,
    },
    description: t("description"),
    keywords: t("keywords").split(","),
    applicationName: site.name,
    authors: [{ name: site.legalName, url: site.url }],
    creator: site.legalName,
    publisher: site.legalName,
    category: "entertainment",
    alternates: {
      canonical: `/${locale}`,
      languages: buildAlternates("/"),
    },
    openGraph: {
      type: "website",
      locale: localeOgMap[locale],
      alternateLocale: locales
        .filter((l) => l !== locale)
        .map((l) => localeOgMap[l]),
      siteName: site.name,
      url: `/${locale}`,
      title: `${site.name} — ${t("tagline")}`,
      description: t("description"),
      images: [
        {
          url: site.ogImage,
          width: 1200,
          height: 630,
          alt: `${site.name} — ${t("tagline")}`,
        },
      ],
    },
    twitter: {
      card: "summary_large_image",
      site: site.twitter,
      creator: site.twitter,
      title: `${site.name} — ${t("tagline")}`,
      description: t("description"),
      images: [site.ogImage],
    },
    robots: {
      index: true,
      follow: true,
      googleBot: {
        index: true,
        follow: true,
        "max-image-preview": "large",
        "max-snippet": -1,
        "max-video-preview": -1,
      },
    },
    icons: { icon: "/favicon.ico" },
    formatDetection: { email: false, telephone: false, address: false },
  };
}

export default async function LocaleLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
}) {
  const { locale: rawLocale } = await params;
  const locale = rawLocale as Locale;
  if (!locales.includes(locale)) notFound();

  const messages = await getMessages();
  const t = await getTranslations({ locale, namespace: "site" });
  const lang = localeLangMap[locale];
  const dir = rtlLocales.includes(locale) ? "rtl" : "ltr";

  return (
    <html
      lang={lang}
      dir={dir}
      className={`${spaceGrotesk.variable} ${anton.variable}`}
    >
      <body>
        <a href="#contenido" className="skip-link">
          {t("skip_link")}
        </a>
        <NextIntlClientProvider messages={messages}>
          {children}
        </NextIntlClientProvider>
        <Script
          id="ld-website"
          type="application/ld+json"
          strategy="beforeInteractive"
          dangerouslySetInnerHTML={{
            __html: JSON.stringify(
              websiteJsonLd(locale, {
                tagline: t("tagline"),
                description: t("description"),
              }),
            ),
          }}
        />
        <Script
          id="ld-organization"
          type="application/ld+json"
          strategy="beforeInteractive"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(organizationJsonLd()) }}
        />
      </body>
    </html>
  );
}
