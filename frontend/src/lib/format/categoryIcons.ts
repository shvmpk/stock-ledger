import type { TablerIcon } from '@tabler/icons-react';
import {
  IconArchive,
  IconBook,
  IconBottle,
  IconBox,
  IconBriefcase,
  IconBuildingStore,
  IconCar,
  IconCoffee,
  IconDeviceLaptop,
  IconDeviceMobile,
  IconDiamond,
  IconGridDots,
  IconHanger,
  IconHeartbeat,
  IconHome,
  IconJacket,
  IconLayoutGrid,
  IconLeaf,
  IconMusic,
  IconPackage,
  IconPalette,
  IconPaw,
  IconPlant,
  IconPuzzle,
  IconRun,
  IconShoe,
  IconSparkles,
  IconStack,
  IconTag,
  IconTool,
} from '@tabler/icons-react';
import { hashString } from './visual';

export interface ResolvedIcon {
  icon: TablerIcon;
  hue: number;
  matchedKeyword: string | null;
}

interface IconRule {
  keywords: readonly string[];
  icon: TablerIcon;
  hue: number;
}

/**
 * Ordered rules. Specific garment, footwear, and pet categories run before the
 * generic apparel and food rules so compound names resolve sensibly.
 */
const RULES: readonly IconRule[] = [
  {
    keywords: ['hoodie', 'sweatshirt', 'jacket', 'coat', 'outerwear'],
    icon: IconJacket,
    hue: 200,
  },
  {
    keywords: ['shoe', 'boot', 'sneaker', 'footwear'],
    icon: IconShoe,
    hue: 30,
  },
  {
    keywords: ['pet', 'animal'],
    icon: IconPaw,
    hue: 40,
  },
  {
    keywords: ['bag', 'backpack', 'tote', 'purse', 'wallet'],
    icon: IconBriefcase,
    hue: 250,
  },
  {
    keywords: ['coffee', 'espresso', 'cafe'],
    icon: IconCoffee,
    hue: 34,
  },
  {
    keywords: ['tea', 'matcha', 'herbal'],
    icon: IconLeaf,
    hue: 120,
  },
  {
    keywords: ['food', 'grocery', 'snack', 'drink', 'beverage', 'pantry'],
    icon: IconBottle,
    hue: 80,
  },
  {
    keywords: ['electronic', 'device', 'gadget', 'tech', 'laptop', 'computer'],
    icon: IconDeviceLaptop,
    hue: 260,
  },
  {
    keywords: ['phone', 'mobile', 'tablet'],
    icon: IconDeviceMobile,
    hue: 240,
  },
  {
    keywords: ['book', 'stationery', 'notebook', 'journal'],
    icon: IconBook,
    hue: 45,
  },
  {
    keywords: ['sportswear', 'sport', 'fitness', 'gym', 'outdoor', 'hiking'],
    icon: IconRun,
    hue: 150,
  },
  {
    keywords: ['apparel', 'clothing', 'fashion', 'wear', 'garment', 'textile'],
    icon: IconHanger,
    hue: 220,
  },
  {
    keywords: ['toy', 'game', 'kids', 'children', 'baby'],
    icon: IconPuzzle,
    hue: 300,
  },
  {
    keywords: ['beauty', 'skincare', 'cosmetic', 'makeup', 'fragrance'],
    icon: IconSparkles,
    hue: 320,
  },
  {
    keywords: ['home', 'kitchen', 'furniture', 'decor', 'living', 'homeware'],
    icon: IconHome,
    hue: 60,
  },
  {
    keywords: ['tool', 'hardware', 'equipment'],
    icon: IconTool,
    hue: 20,
  },
  {
    keywords: ['health', 'wellness', 'supplement', 'medicine'],
    icon: IconHeartbeat,
    hue: 0,
  },
  {
    keywords: ['plant', 'garden', 'flower', 'seed'],
    icon: IconPlant,
    hue: 130,
  },
  {
    keywords: ['art', 'craft', 'paint', 'canvas'],
    icon: IconPalette,
    hue: 290,
  },
  {
    keywords: ['music', 'audio', 'instrument'],
    icon: IconMusic,
    hue: 270,
  },
  {
    keywords: ['automotive', 'car', 'vehicle'],
    icon: IconCar,
    hue: 200,
  },
  {
    keywords: ['jewellery', 'jewelry', 'watch', 'accessory'],
    icon: IconDiamond,
    hue: 280,
  },
];

const FALLBACK_ICONS: readonly TablerIcon[] = [
  IconTag,
  IconBox,
  IconPackage,
  IconStack,
  IconArchive,
  IconBuildingStore,
  IconGridDots,
  IconLayoutGrid,
];

const DEFAULT_ICON: ResolvedIcon = {
  icon: IconTag,
  hue: 210,
  matchedKeyword: null,
};

const MAX_CACHE_ENTRIES = 512;
const iconCache = new Map<string, ResolvedIcon>();
let scanCount = 0;

function normalizeCategory(category: string): string {
  return category
    .trim()
    .normalize('NFKD')
    .toLowerCase()
    .replace(/[\u0300-\u036f]/g, '');
}

function singularize(token: string): string {
  if (token.endsWith('ies') && token.length > 3) {
    return `${token.slice(0, -3)}y`;
  }
  if (/(?:s|x|z|ch|sh)es$/.test(token)) {
    return token.slice(0, -2);
  }
  if (token.endsWith('s') && !token.endsWith('ss') && token.length > 3) {
    return token.slice(0, -1);
  }
  return token;
}

function scanCategory(key: string): ResolvedIcon {
  const tokens = key.split(/[^\p{L}\p{N}]+/u).filter(Boolean);
  if (tokens.length === 0) return DEFAULT_ICON;

  const candidates = new Set<string>();
  for (const token of tokens) {
    candidates.add(token);
    candidates.add(singularize(token));
  }

  for (const rule of RULES) {
    for (const keyword of rule.keywords) {
      const exactMatch = candidates.has(keyword);
      const partialMatch =
        keyword.length >= 4 && tokens.some((token) => token.includes(keyword));
      if (exactMatch || partialMatch) {
        return { icon: rule.icon, hue: rule.hue, matchedKeyword: keyword };
      }
    }
  }

  const hash = hashString(key);
  return {
    icon: FALLBACK_ICONS[hash % FALLBACK_ICONS.length],
    hue: hash % 360,
    matchedKeyword: null,
  };
}

/** Resolve an arbitrary category name to a stable icon and hue. */
export function resolveIcon(category: string): ResolvedIcon {
  const key = normalizeCategory(category);
  if (!key) return DEFAULT_ICON;

  const cached = iconCache.get(key);
  if (cached) return cached;

  scanCount += 1;
  const resolved = scanCategory(key);
  if (iconCache.size >= MAX_CACHE_ENTRIES) {
    iconCache.clear();
  }
  iconCache.set(key, resolved);
  return resolved;
}

/** Test/debug access to cache behaviour. */
export function getIconCacheStats(): { size: number; scans: number } {
  return { size: iconCache.size, scans: scanCount };
}

/** Clear the module cache, primarily for deterministic tests and hot reloads. */
export function clearIconCache(): void {
  iconCache.clear();
  scanCount = 0;
}
