import { beforeEach, describe, expect, it } from 'vitest';
import {
  IconArchive,
  IconBottle,
  IconBox,
  IconBuildingStore,
  IconCoffee,
  IconDiamond,
  IconGridDots,
  IconHanger,
  IconHome,
  IconJacket,
  IconLayoutGrid,
  IconPackage,
  IconPaw,
  IconRun,
  IconShoe,
  IconStack,
  IconTag,
} from '@tabler/icons-react';
import {
  clearIconCache,
  getIconCacheStats,
  resolveIcon,
} from './categoryIcons';
import { hashString } from './visual';

const fallbackIcons = [
  IconTag,
  IconBox,
  IconPackage,
  IconStack,
  IconArchive,
  IconBuildingStore,
  IconGridDots,
  IconLayoutGrid,
];

describe('resolveIcon', () => {
  beforeEach(() => {
    clearIconCache();
  });

  it('resolves known categories through ordered keyword rules', () => {
    expect(resolveIcon('Apparel')).toMatchObject({
      icon: IconHanger,
      hue: 220,
      matchedKeyword: 'apparel',
    });
    expect(resolveIcon('Coffee & Tea')).toMatchObject({
      icon: IconCoffee,
      hue: 34,
      matchedKeyword: 'coffee',
    });
    expect(resolveIcon('Pantry')).toMatchObject({
      icon: IconBottle,
      hue: 80,
      matchedKeyword: 'pantry',
    });
    expect(resolveIcon('Homeware')).toMatchObject({
      icon: IconHome,
      hue: 60,
      matchedKeyword: 'home',
    });
    expect(resolveIcon('Pet Supplies')).toMatchObject({
      icon: IconPaw,
      hue: 40,
      matchedKeyword: 'pet',
    });
    expect(resolveIcon('Outerwear')).toMatchObject({
      icon: IconJacket,
      hue: 200,
      matchedKeyword: 'outerwear',
    });
    expect(resolveIcon('Sportswear')).toMatchObject({
      icon: IconRun,
      hue: 150,
      matchedKeyword: 'sportswear',
    });
  });

  it('singularizes plural category names before matching', () => {
    expect(resolveIcon('Sports Shoes')).toMatchObject({
      icon: IconShoe,
      hue: 30,
    });
    expect(resolveIcon('Electronics')).toMatchObject({
      icon: expect.any(Object),
      matchedKeyword: 'electronic',
    });
    expect(resolveIcon('Watches')).toMatchObject({
      icon: IconDiamond,
      hue: 280,
      matchedKeyword: 'watch',
    });
  });

  it('normalizes case, whitespace, and accents', () => {
    const apparel = resolveIcon('Apparel');
    expect(resolveIcon(' APPAREL ')).toBe(apparel);
    expect(resolveIcon('Café')).toMatchObject({
      icon: IconCoffee,
      matchedKeyword: 'cafe',
    });
  });

  it('uses the generic hash pool for unknown categories', () => {
    const resolved = resolveIcon('Vintage Ceramics');
    const key = 'vintage ceramics';
    expect(resolved.matchedKeyword).toBeNull();
    expect(fallbackIcons).toContain(resolved.icon);
    expect(resolved.icon).toBe(fallbackIcons[hashString(key) % fallbackIcons.length]);
    expect(resolved.hue).toBe(hashString(key) % 360);
    expect(resolveIcon('Vintage Ceramics')).toBe(resolved);
  });

  it('falls back to IconTag for blank categories without scanning', () => {
    expect(resolveIcon('')).toEqual({
      icon: IconTag,
      hue: 210,
      matchedKeyword: null,
    });
    expect(resolveIcon('   ')).toEqual({
      icon: IconTag,
      hue: 210,
      matchedKeyword: null,
    });
    expect(getIconCacheStats()).toEqual({ size: 0, scans: 0 });
  });

  it('scans exactly once for 10,000 repeated normalized calls', () => {
    const first = resolveIcon('Apparel');
    for (let index = 0; index < 10_000; index += 1) {
      expect(resolveIcon(' APPAREL ')).toBe(first);
    }
    expect(getIconCacheStats()).toEqual({ size: 1, scans: 1 });
  });
});
