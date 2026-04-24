/**
 * Format 12_345 → "12.3k", 54_200_000 → "54.2M".
 * Locale-agnostic — uses '.' as the decimal separator by convention
 * with the rest of the UI kit.
 */
export function formatCompact(n: number): string {
  if (!Number.isFinite(n)) return "∞";
  if (n < 1_000) return String(n);
  if (n < 1_000_000) return `${(n / 1_000).toFixed(1).replace(/\.0$/, "")}k`;
  return `${(n / 1_000_000).toFixed(1).replace(/\.0$/, "")}M`;
}
