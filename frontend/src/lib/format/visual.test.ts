import { describe, expect, it } from 'vitest';
import {
  hashString,
  hslPairFromHue,
  hueFromString,
  skuTail,
  stringToColor,
  stringToColorPair,
  toInitials,
} from './visual';

function parseHsl(color: string): { hue: number; saturation: number; lightness: number } {
  const match = /^hsl\((\d+), (\d+)%, (\d+)%\)$/.exec(color);
  if (!match) throw new Error(`Unsupported colour format: ${color}`);
  return {
    hue: Number(match[1]),
    saturation: Number(match[2]),
    lightness: Number(match[3]),
  };
}

function channelToLinear(channel: number): number {
  const value = channel / 255;
  return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
}

function hslToLuminance(color: string): number {
  const { hue, saturation, lightness } = parseHsl(color);
  const s = saturation / 100;
  const l = lightness / 100;
  const chroma = (1 - Math.abs(2 * l - 1)) * s;
  const segment = hue / 60;
  const secondary = chroma * (1 - Math.abs((segment % 2) - 1));
  const offset = l - chroma / 2;

  let red = 0;
  let green = 0;
  let blue = 0;
  if (segment < 1) [red, green, blue] = [chroma, secondary, 0];
  else if (segment < 2) [red, green, blue] = [secondary, chroma, 0];
  else if (segment < 3) [red, green, blue] = [0, chroma, secondary];
  else if (segment < 4) [red, green, blue] = [0, secondary, chroma];
  else if (segment < 5) [red, green, blue] = [secondary, 0, chroma];
  else [red, green, blue] = [chroma, 0, secondary];

  return (
    0.2126 * channelToLinear((red + offset) * 255) +
    0.7152 * channelToLinear((green + offset) * 255) +
    0.0722 * channelToLinear((blue + offset) * 255)
  );
}

function contrastRatio(first: string, second: string): number {
  const lighter = Math.max(hslToLuminance(first), hslToLuminance(second));
  const darker = Math.min(hslToLuminance(first), hslToLuminance(second));
  return (lighter + 0.05) / (darker + 0.05);
}

function hueDistance(first: number, second: number): number {
  const distance = Math.abs(first - second);
  return Math.min(distance, 360 - distance);
}

describe('hashString', () => {
  it('matches known FNV-1a 32-bit values', () => {
    expect(hashString('')).toBe(2166136261);
    expect(hashString('a')).toBe(3826002220);
    expect(hashString('foobar')).toBe(3214735720);
  });
});

describe('stringToColor', () => {
  it('is deterministic and returns a valid HSL colour', () => {
    const samples = [
      'HOOD-NVY-XL',
      'COF-ETH-250G',
      'POUR-SET-SND',
      '  Apparel  ',
      'Café',
      '茶叶',
      '',
    ];

    for (const sample of samples) {
      const first = stringToColor(sample);
      const second = stringToColor(sample);
      expect(first).toBe(second);
      expect(parseHsl(first)).toEqual({
        hue: hueFromString(sample),
        saturation: 65,
        lightness: 52,
      });
    }
  });

  it('keeps similar variant SKUs visually distinct', () => {
    const first = hueFromString('HOOD-NVY-XL');
    const second = hueFromString('HOOD-NVY-L');
    expect(first).not.toBe(second);
    expect(hueDistance(first, second)).toBeGreaterThanOrEqual(30);
  });

  it('spreads a deterministic catalog across the hue wheel', () => {
    const hundred = Array.from({ length: 100 }, (_, index) =>
      `SKU-${String(index).padStart(5, '0')}`,
    ).map(hueFromString);
    const thousand = Array.from({ length: 1000 }, (_, index) =>
      `SKU-${String(index).padStart(5, '0')}`,
    ).map(hueFromString);

    expect(new Set(hundred).size).toBeGreaterThanOrEqual(80);
    expect(new Set(thousand).size).toBeGreaterThanOrEqual(300);
  });
});

describe('stringToColorPair', () => {
  it('returns the expected light chip pair for a string', () => {
    const hue = hueFromString('Pantry');
    expect(stringToColorPair('Pantry')).toEqual(hslPairFromHue(hue));
    expect(hslPairFromHue(hue + 360)).toEqual(hslPairFromHue(hue));
  });

  it('keeps chip text readable for every hue', () => {
    for (let hue = 0; hue < 360; hue += 10) {
      const pair = hslPairFromHue(hue);
      expect(contrastRatio(pair.bg, pair.color)).toBeGreaterThan(4.5);
    }
  });
});

describe('toInitials', () => {
  it('derives up to two uppercase alphanumeric initials', () => {
    expect(toInitials('XL')).toBe('XL');
    expect(toInitials('Extra Large')).toBe('EX');
    expect(toInitials('250 g')).toBe('25');
    expect(toInitials('1 kg')).toBe('1K');
    expect(toInitials('M')).toBe('M');
    expect(toInitials('T-Shirt')).toBe('TS');
    expect(toInitials('Café')).toBe('CA');
    expect(toInitials('茶叶')).toBe('茶叶');
  });

  it('falls back to a marker for empty or non-alphanumeric input', () => {
    expect(toInitials('')).toBe('?');
    expect(toInitials('   ')).toBe('?');
    expect(toInitials('---')).toBe('?');
    expect(toInitials('🎁')).toBe('?');
  });
});

describe('skuTail', () => {
  it('returns the last meaningful SKU segment', () => {
    expect(skuTail('HOOD-NVY-XL')).toBe('XL');
    expect(skuTail('COF-ETH-250G')).toBe('250G');
    expect(skuTail('POUR-SET-SND')).toBe('SND');
    expect(skuTail('HOOD-NVY-XL-01')).toBe('XL');
    expect(skuTail('HOOD_NVY_M')).toBe('M');
    expect(skuTail('ABC123')).toBe('ABC123');
  });
});

describe('100k scale simulation', () => {
  it('resolves a large catalog within a generous smoke-test budget', () => {
    const startedAt = performance.now();
    const hues = new Set<number>();

    for (let index = 0; index < 100_000; index += 1) {
      const sku = `SKU-${String(index).padStart(6, '0')}`;
      const color = stringToColor(sku);
      const initials = toInitials(skuTail(sku));
      expect(color).toMatch(/^hsl\(\d{1,3}, 65%, 52%\)$/);
      expect(initials).toMatch(/^[A-Z0-9]{1,2}$/);
      hues.add(parseHsl(color).hue);
    }

    expect(hues.size).toBe(360);
    expect(performance.now() - startedAt).toBeLessThan(2000);
  });
});
