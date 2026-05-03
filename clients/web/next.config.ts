import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

/**
 * Whitelist the CDN host so `next/image` can optimize remote images.
 * Match the same default as `lib/site.ts`.
 */
const cdnHost = (
  process.env.NEXT_PUBLIC_MEMES_CDN_URL ||
  "https://cdn-openmeme.clientes-g4a.workers.dev"
).replace(/^https?:\/\//, "");

const withNextIntl = createNextIntlPlugin("./i18n/request.ts");

const nextConfig: NextConfig = {
  images: {
    remotePatterns: [
      {
        protocol: "https",
        hostname: cdnHost,
        pathname: "/**",
      },
    ],
  },
};

export default withNextIntl(nextConfig);
