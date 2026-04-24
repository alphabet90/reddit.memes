import type { SVGProps } from "react";

/**
 * Custom outlined icon set, matching the OpenMEME brand kit
 * (DESING.md — "no external icon library"). Each icon:
 *  - has `role="img"` + accessible `aria-label`
 *  - inherits `currentColor` for stroke so parents control color
 *  - takes all SVG props for sizing / overrides
 */
type IconProps = SVGProps<SVGSVGElement> & {
  title?: string;
  size?: number | string;
};

function base({
  title,
  size = 16,
  strokeWidth = 2,
  children,
  ...rest
}: IconProps & { children: React.ReactNode }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      role={title ? "img" : "presentation"}
      aria-label={title}
      aria-hidden={title ? undefined : true}
      {...rest}
    >
      {title ? <title>{title}</title> : null}
      {children}
    </svg>
  );
}

export const SearchIcon = (p: IconProps) =>
  base({
    ...p,
    strokeWidth: 2.5,
    children: (
      <>
        <circle cx="11" cy="11" r="8" />
        <line x1="21" y1="21" x2="16.65" y2="16.65" />
      </>
    ),
  });

export const UploadIcon = (p: IconProps) =>
  base({
    ...p,
    strokeWidth: 2.5,
    children: (
      <>
        <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4" />
        <polyline points="17 8 12 3 7 8" />
        <line x1="12" y1="3" x2="12" y2="15" />
      </>
    ),
  });

export const ShareIcon = (p: IconProps) =>
  base({
    ...p,
    strokeWidth: 2.5,
    children: (
      <>
        <path d="M4 12v8a2 2 0 002 2h12a2 2 0 002-2v-8" />
        <polyline points="17 8 12 3 7 8" />
        <line x1="12" y1="2" x2="12" y2="15" />
      </>
    ),
  });

export const BookmarkIcon = (p: IconProps) =>
  base({
    ...p,
    children: <path d="M19 21l-7-5-7 5V5a2 2 0 012-2h10a2 2 0 012 2z" />,
  });

export const CopyIcon = (p: IconProps) =>
  base({
    ...p,
    strokeWidth: 2.5,
    children: (
      <>
        <rect x="9" y="9" width="13" height="13" rx="2" />
        <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1" />
      </>
    ),
  });

export const EyeIcon = (p: IconProps) =>
  base({
    ...p,
    strokeWidth: 2.5,
    children: (
      <>
        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
        <circle cx="12" cy="12" r="3" />
      </>
    ),
  });

export const StarIcon = (p: IconProps) =>
  base({
    ...p,
    strokeWidth: 2.5,
    children: (
      <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
    ),
  });

export const UserIcon = (p: IconProps) =>
  base({
    ...p,
    children: (
      <>
        <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2" />
        <circle cx="12" cy="7" r="4" />
      </>
    ),
  });

export const ChevronLeft = (p: IconProps) =>
  base({
    ...p,
    strokeWidth: 2.5,
    children: <polyline points="15 18 9 12 15 6" />,
  });

export const GlobeIcon = (p: IconProps) =>
  base({
    ...p,
    children: (
      <>
        <circle cx="12" cy="12" r="10" />
        <line x1="2" y1="12" x2="22" y2="12" />
        <path d="M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z" />
      </>
    ),
  });

export const TvIcon = (p: IconProps) =>
  base({
    ...p,
    children: (
      <>
        <rect x="2" y="3" width="20" height="14" rx="2" />
        <line x1="8" y1="21" x2="16" y2="21" />
        <line x1="12" y1="17" x2="12" y2="21" />
      </>
    ),
  });

export const CircleIcon = (p: IconProps) =>
  base({ ...p, children: <circle cx="12" cy="12" r="10" /> });

export const PersonIcon = (p: IconProps) =>
  base({
    ...p,
    children: (
      <>
        <circle cx="12" cy="8" r="4" />
        <path d="M4 20c0-4 3.6-7 8-7s8 3 8 7" />
      </>
    ),
  });

export const RefreshIcon = (p: IconProps) =>
  base({
    ...p,
    children: (
      <>
        <polyline points="17 1 21 5 17 9" />
        <path d="M3 11V9a4 4 0 014-4h14" />
        <polyline points="7 23 3 19 7 15" />
        <path d="M21 13v2a4 4 0 01-4 4H3" />
      </>
    ),
  });

export const BoltIcon = (p: IconProps) =>
  base({
    ...p,
    strokeWidth: 2.5,
    children: <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />,
  });

export const SmileIcon = (p: IconProps) =>
  base({
    ...p,
    children: (
      <>
        <circle cx="12" cy="12" r="10" />
        <path d="M8 14s1.5 2 4 2 4-2 4-2" />
        <line x1="9" y1="9" x2="9.01" y2="9" />
        <line x1="15" y1="9" x2="15.01" y2="9" />
      </>
    ),
  });

export const ShuffleIcon = (p: IconProps) =>
  base({
    ...p,
    children: (
      <>
        <polyline points="16 3 21 3 21 8" />
        <line x1="4" y1="20" x2="21" y2="3" />
        <polyline points="21 16 21 21 16 21" />
        <line x1="15" y1="15" x2="21" y2="21" />
        <line x1="4" y1="4" x2="9" y2="9" />
      </>
    ),
  });

export const MenuIcon = (p: IconProps) =>
  base({
    ...p,
    children: (
      <>
        <line x1="3" y1="6" x2="21" y2="6" />
        <line x1="3" y1="12" x2="21" y2="12" />
        <line x1="3" y1="18" x2="21" y2="18" />
      </>
    ),
  });

import type { CategoryIcon } from "@/lib/types";

const categoryIconMap = {
  globe: GlobeIcon,
  tv: TvIcon,
  circle: CircleIcon,
  user: PersonIcon,
  star: StarIcon,
  refresh: RefreshIcon,
} satisfies Record<CategoryIcon, (p: IconProps) => React.ReactElement>;

export function CategoryIconGlyph({
  name,
  ...rest
}: IconProps & { name: CategoryIcon }) {
  const Component = categoryIconMap[name];
  return <Component {...rest} />;
}
