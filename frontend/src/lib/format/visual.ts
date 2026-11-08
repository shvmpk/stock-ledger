const FNV_OFFSET_BASIS = 0x811c9dc5;
const FNV_PRIME = 0x01000193;

/** Deterministic 32-bit FNV-1a hash over UTF-16 code units. */
export function hashString(text: string): number {
  let hash = FNV_OFFSET_BASIS;
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index);
    hash = Math.imul(hash, FNV_PRIME);
  }
  return hash >>> 0;
}

export function hueFromString(text: string): number {
  return hashString(text) % 360;
}

/** Deterministic solid avatar colour with fixed saturation and lightness. */
export function stringToColor(text: string): string {
  return `hsl(${hueFromString(text)}, 65%, 52%)`;
}

/** Solid, contrast-safe chip colours for a hue. */
export function hslPairFromHue(hue: number): { bg: string; color: string } {
  const normalizedHue = ((hue % 360) + 360) % 360;
  return {
    bg: `hsl(${normalizedHue}, 70%, 92%)`,
    color: `hsl(${normalizedHue}, 60%, 26%)`,
  };
}

export function stringToColorPair(text: string): { bg: string; color: string } {
  return hslPairFromHue(hueFromString(text));
}

/** Up to two uppercase alphanumeric initials from any string. */
export function toInitials(text: string): string {
  const alphanumeric = Array.from(text).filter((character) =>
    /[\p{L}\p{N}]/u.test(character),
  );
  return alphanumeric.slice(0, 2).join('').toUpperCase() || '?';
}

/**
 * Returns the last meaningful SKU segment. Numeric-only check segments such as
 * a trailing "-01" are skipped in favour of the preceding alphabetic segment.
 */
export function skuTail(sku: string): string {
  const segments = sku.split(/[-_/\s]+/).filter(Boolean);
  for (let index = segments.length - 1; index >= 0; index -= 1) {
    if (/[A-Za-z]/.test(segments[index])) return segments[index];
  }
  return segments.at(-1) ?? sku;
}
