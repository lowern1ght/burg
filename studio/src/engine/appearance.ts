/**
 * Block shape classification — ported from tools/structures/appearance.py.
 *
 * The single source of truth for how much of its cell a block fills.
 * Used by solids.ts (predicates), fabric.ts (guard), and the checkers.
 */

export type BlockInfo = {
  /** Short id without "minecraft:" prefix. */
  id: string;
  /** Block state properties (type, facing, half, etc.). */
  props: Record<string, string>;
};

export type BlockGrid = {
  size: [number, number, number];
  get(x: number, y: number, z: number): BlockInfo | null;
  occupied(x: number, y: number, z: number): boolean;
  blocks(): Iterable<{ pos: [number, number, number]; block: BlockInfo }>;
};

/** Shape kind + parameter, matching Python's shape_of() return. */
export type Shape = [kind: string, param: string];

const FULL_OVERRIDES = new Set([
  'farmland', 'hay_block', 'moss_block', 'water', 'fire', 'cobweb',
  'honey_block', 'honeycomb_block', 'bookshelf', 'chiseled_bookshelf',
  'crafting_table', 'furnace', 'smoker', 'blast_furnace', 'note_block',
  'beehive', 'bee_nest', 'barrel', 'composter', 'white_terracotta',
  'smooth_stone', 'stone_bricks', 'carved_pumpkin', 'stonecutter',
  'decorated_pot', 'chest', 'anvil', 'lectern', 'mud', 'podzol',
  'rooted_dirt',
]);

const POST_SUFFIXES = ['_fence', '_fence_gate', '_wall', '_pane', '_bars'];
const FLAT_SUFFIXES = ['_carpet', '_pressure_plate'];
const TINY_NAMES = new Set([
  'torch', 'wall_torch', 'redstone_torch', 'lantern', 'candle',
  'white_candle', 'red_candle', 'flower_pot', 'lever', 'tripwire_hook',
  'bell', 'chain', 'cauldron', 'water_cauldron', 'campfire',
]);
const TINY_SUFFIXES = ['_button', '_sapling', '_tulip', '_daisy', '_bluet', '_banner', '_sign'];
const PLANT_NAMES = new Set([
  'grass', 'short_grass', 'wheat', 'carrots', 'potatoes', 'dandelion',
  'poppy', 'allium', 'cornflower', 'seagrass', 'oak_sapling',
]);

function endsWithAny(s: string, suffixes: string[]): boolean {
  return suffixes.some(suffix => s.endsWith(suffix));
}

export function shapeOf(b: BlockInfo): Shape {
  const n = b.id;

  if (FULL_OVERRIDES.has(n)) return ['full', ''];
  if (n.startsWith('potted_')) return ['tiny', ''];
  if (n.endsWith('_slab')) {
    const t = b.props['type'] ?? 'bottom';
    if (t === 'double') return ['full', ''];
    return ['slab', t];
  }
  if (n.endsWith('_stairs')) {
    return ['stairs', `${b.props['facing'] ?? 'north'}:${b.props['half'] ?? 'bottom'}`];
  }
  if (n.endsWith('_trapdoor')) {
    if (b.props['open'] === 'true') return ['post', b.props['facing'] ?? 'north'];
    return ['plate', b.props['half'] ?? 'bottom'];
  }
  if (n.endsWith('_door')) return ['door', b.props['facing'] ?? 'north'];
  if (n.endsWith('_bed')) return ['slab', 'bottom'];
  if (endsWithAny(n, POST_SUFFIXES)) return ['post', ''];
  if (endsWithAny(n, FLAT_SUFFIXES) || n === 'lily_pad' || n === 'moss_carpet' || n === 'snow') {
    return ['flat', ''];
  }
  if (n === 'ladder') return ['post', b.props['facing'] ?? 'north'];
  if (TINY_NAMES.has(n) || endsWithAny(n, TINY_SUFFIXES)) return ['tiny', ''];
  if (PLANT_NAMES.has(n)) return ['plant', ''];
  if (n.endsWith('_leaves')) return ['full', ''];
  return ['full', ''];
}

/** Approximate top-face colour per block id (for flat renders). */
const COLOURS: Record<string, string> = {
  'grass_block': '#79a54b', 'dirt': '#8b6a47', 'coarse_dirt': '#7a5b3c',
  'dirt_path': '#9c8253', 'podzol': '#5c3f18', 'farmland': '#6b4a2a',
  'mud': '#3d3a3c', 'moss_block': '#5b7327', 'stone': '#8f8f8f',
  'smooth_stone': '#a3a3a3', 'water': '#3f5fbf',
  'oak_planks': '#b78d54', 'oak_log': '#6f5735', 'stripped_oak_log': '#b89055',
  'oak_slab': '#b78d54', 'oak_stairs': '#ac8149', 'oak_fence': '#8f6f42',
  'oak_door': '#9a7440', 'oak_trapdoor': '#8d6a3a',
  'cobblestone': '#7f7f7f', 'mossy_cobblestone': '#6f7f5f',
  'stone_bricks': '#9a9a9a', 'andesite': '#88898a', 'tuff': '#6b6b62',
  'white_terracotta': '#d1b1a1',
  'torch': '#ffcc44', 'wall_torch': '#ffcc44', 'lantern': '#f3b45f',
  'white_bed': '#e4e4e4', 'white_wool': '#e9ecec',
  'glass': '#c8e4f0', 'glass_pane': '#d4ebf5',
  'oak_leaves': '#4f7f2f', 'hay_block': '#c8ab30',
  'crafting_table': '#7f5f38', 'furnace': '#767676',
  'chest': '#9a7440', 'barrel': '#7f6238',
  'flower_pot': '#a05a3c', 'chain': '#4a4e57',
};

export function colourOf(b: BlockInfo): string {
  if (b.id in COLOURS) return COLOURS[b.id];
  for (const [suffix, key] of [
    ['_slab', 'oak_slab'], ['_stairs', 'oak_stairs'], ['_planks', 'oak_planks'],
    ['_log', 'oak_log'], ['_leaves', 'oak_leaves'], ['_fence', 'oak_fence'],
    ['_wall', 'cobblestone_wall'], ['_door', 'oak_door'], ['_bed', 'white_bed'],
    ['_wool', 'white_wool'], ['_carpet', 'white_carpet'], ['_pane', 'glass_pane'],
  ] as const) {
    if (b.id.endsWith(suffix)) return COLOURS[key] ?? '#9b8f86';
  }
  return '#9b8f86';
}
