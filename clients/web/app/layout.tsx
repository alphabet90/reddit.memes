import type { Metadata, Viewport } from "next";
import { Space_Grotesk } from "next/font/google";
import localFont from "next/font/local";
import Script from "next/script";

import { site } from "@/lib/site";
import { organizationJsonLd, websiteJsonLd } from "@/lib/seo";

import "./globals.css";

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
      path: "../public/fonts/Anton-Regular.ttf",
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

export const metadata: Metadata = {
  metadataBase: new URL(site.url),
  title: {
    default: `${site.name} — ${site.tagline}`,
    template: `%s · ${site.name}`,
  },
  description: site.description,
  keywords: [...site.keywords],
  applicationName: site.name,
  authors: [{ name: site.legalName, url: site.url }],
  creator: site.legalName,
  publisher: site.legalName,
  category: "entertainment",
  alternates: {
    canonical: "/",
    languages: {
      "es-AR": "/",
      "x-default": "/",
    },
  },
  openGraph: {
    type: "website",
    locale: site.locale,
    siteName: site.name,
    url: site.url,
    title: `${site.name} — ${site.tagline}`,
    description: site.description,
    images: [
      {
        url: site.ogImage,
        width: 1200,
        height: 630,
        alt: `${site.name} — ${site.tagline}`,
      },
    ],
  },
  twitter: {
    card: "summary_large_image",
    site: site.twitter,
    creator: site.twitter,
    title: `${site.name} — ${site.tagline}`,
    description: site.description,
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
  icons: {
    icon: "/favicon.ico",
  },
  formatDetection: {
    email: false,
    telephone: false,
    address: false,
  },
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html
      lang="es-AR"
      dir="ltr"
      className={`${spaceGrotesk.variable} ${anton.variable}`}
    >
      <body>
        <a href="#contenido" className="skip-link">
          Saltar al contenido
        </a>
        {children}
        <Script
          id="ld-website"
          type="application/ld+json"
          strategy="beforeInteractive"
          dangerouslySetInnerHTML={{ __html: JSON.stringify(websiteJsonLd()) }}
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
