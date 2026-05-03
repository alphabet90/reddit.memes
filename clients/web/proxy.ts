import createMiddleware from "next-intl/middleware";
import { routing } from "./i18n/routing";

export default createMiddleware(routing);

export const config = {
  matcher: [
    // Match all paths except Next.js internals, static assets, and well-known files
    "/((?!_next|_vercel|api|favicon\\.ico|robots\\.txt|sitemap\\.xml|manifest\\.webmanifest|.*\\..*).*)",
  ],
};
