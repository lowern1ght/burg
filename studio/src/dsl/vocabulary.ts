/**
 * Vocabulary — a corpus-derived set of block states grouped by construction role.
 *
 * Ported from `tools/structures/compose.py:harvest()` and `merge()`. The Python
 * pipeline is called by the build tool to lift one block state per role out of a
 * hand-authored structure; the same shape is now what the studio generator can
 * consume so a new build is composed of block states that all occur in the corpus,
 * not invented.
 *
 * The Python's `analyse()` lives in `anatomy.py`; we provide a minimal port
 * (`analyse()` here) that infers just the boundaries harvest needs (ground_top,
 * wall_lo/hi, roof_lo/hi, shell). The author never trusts it on a building it
 * has not seen — the studio surfaces a `describe()`-style report for review
 * when a generator run is gated.
 *
 * Vocabulary as a value: `harvest(donor)` is pure, `merge(primary, ...others)`
 * is pure. Both return a new object. Nothing mutates the donor.
 */

import type { BlockGrid, BlockInfo } from '../engine/appearance';

export type { BlockGrid } from '../engine/appearance';

// ── types ─────────────────────────────────────────────────────────────

/**
 * One block state — id (short form, no `minecraft:` prefix) and the
 * per-state properties. Matches the shape the engine uses for `BlockInfo`
 * with `properties` instead of `props`; `toBlockState`/`fromBlockInfo`
 * bridge the two.
 */
export interface BlockState {
  id: string;
  properties: Record<string, string>;
}

/**
 * Vocabulary — every construction role the generator knows about, filled from a
 * donor structure. Each role is either a list of `BlockState` (the corpus had
 * several variants) or a single `BlockState` (the role always picks one —
 * corner post, slab cap, fence, crenel merlon).
 */
export interface Vocabulary {
  donor: string;
  /** Ground apron at y=0 — grass, coarse dirt, dirt path, etc. */
  apron: BlockState[];
  /** Interior floor slab — full block, not a bottom slab. */
  floor: BlockState[];
  /** Lower wall stone — cobblestone family, smooth stone. */
  stone: BlockState[];
  /** Upper wall timber — oak_planks and cousins. */
  timber: BlockState[];
  /** Corner post — `_log` with `axis=y`, harvested at a perimeter corner. */
  post: BlockState | null;
  /** Top slab cap for storey breaks. */
  slab_top: BlockState | null;
  /** Bottom slab cap for storey breaks. */
  slab_bottom: BlockState | null;
  /** Top stone slab (string course). */
  stone_slab_top: BlockState | null;
  /** Window — glass_pane, glass, or iron_bars on a wall cell. */
  window: BlockState[];
  /** Fence post / lookout rail. */
  fence: BlockState | null;
  /** Crenellation merlon — full stone block at the top. */
  crenel: BlockState | null;
  /** Interior stair facings (non-perimeter). */
  stairs: BlockState[];
  /** Light sources — torch, lantern, soul_torch, candle, campfire. */
  light: BlockState[];
  /** Door — lower half (`half=lower`). */
  door_lower: BlockState[];
  /** Door — upper half (`half=upper`). */
  door_upper: BlockState[];
  /** Rail / decorative fence family. */
  rail: BlockState[];
  /** Roof stair facings (perimeter face at the top floors). */
  roof_stairs: BlockState[];
  /** Ridge beam — slab along the apex. */
  beam: BlockState[];
  /** Hanging lanterns (`hanging=true`). */
  hanging: BlockState[];
  /** Vegetation — leaves, flowers, grass, saplings. */
  vegetation: BlockState[];
  /** Decoration — furniture and decor the build scatters. */
  decoration: BlockState[];
}

// ── anatomy ───────────────────────────────────────────────────────────

/** A structure's inferred construction zones. */
export interface Anatomy {
  /** Highest y dominated by terrain (apron). */
  groundTop: number;
  /** First wall layer. */
  wallLo: number;
  /** Last wall layer (inclusive). */
  wallHi: number;
  /** First roof layer. */
  roofLo: number;
  /** Last solid layer. */
  roofHi: number;
  /** Shell box — `[x0, x1, z0, z1]` of the wall ring. */
  shell: [number, number, number, number];
}

// ── constants (lifted from tools/structures/compose.py + anatomy.py) ──

const TERRAIN: ReadonlySet<string> = new Set([
  'grass_block', 'dirt', 'coarse_dirt', 'dirt_path', 'podzol', 'rooted_dirt',
  'farmland', 'mud', 'moss_block', 'stone', 'gravel', 'sand', 'water',
  'snow_block',
]);

/** Suffixes/whole ids that mean "you cannot stand on the top of this". */
const NOT_A_FLOOR_SUFFIXES: readonly string[] = [
  '_slab', '_stairs', '_wall', '_fence', '_fence_gate', '_gate',
  '_pane', '_bars', '_door', '_trapdoor', '_leaves', '_torch',
  '_sign', '_button', '_plate', '_pot', '_carpet', '_rail',
  '_banner', '_head', '_candle', '_sapling', '_bush', '_grass',
  '_flower', '_mushroom', '_crop', '_stem', '_bed',
];

const NOT_A_FLOOR_NAMES: ReadonlySet<string> = new Set([
  'lantern', 'ladder', 'vine', 'jigsaw', 'chain', 'glass', 'water', 'lava',
  'snow', 'campfire', 'cauldron', 'lever', 'tripwire',
]);

/** Non-structural cells — terrain, vegetation, jigsaws, torches. */
const NON_STRUCTURAL: ReadonlySet<string> = new Set([
  ...TERRAIN,
  'oak_leaves', 'spruce_leaves', 'birch_leaves', 'dark_oak_leaves',
  'grass', 'short_grass', 'tall_grass', 'fern', 'large_fern', 'lily_pad',
  'dandelion', 'poppy', 'allium', 'azure_bluet', 'cornflower', 'oxeye_daisy',
  'red_tulip', 'pink_tulip', 'white_tulip', 'orange_tulip', 'oak_sapling',
  'moss_carpet', 'seagrass', 'wheat', 'carrots', 'potatoes', 'sweet_berry_bush',
  'jigsaw', 'torch', 'flower_pot',
]);

// ── bridges ───────────────────────────────────────────────────────────

/** Convert a `BlockInfo` (engine shape) to a `BlockState` (vocabulary shape). */
export function toBlockState(info: BlockInfo): BlockState {
  return { id: info.id, properties: { ...info.props } };
}

/** Convert a `BlockState` back to a `BlockInfo` (engine shape). */
export function fromBlockState(s: BlockState | null): BlockInfo {
  if (s === null) {
    return { id: 'air', props: {} };
  }
  return { id: s.id, props: { ...s.properties } };
}

function short(s: BlockState): string {
  const id = s.id.startsWith('minecraft:') ? s.id.slice(10) : s.id;
  return id;
}

function endsWithAny(s: string, suffixes: readonly string[]): boolean {
  return suffixes.some((suf) => s.endsWith(suf));
}

// ── minimal anatomy ──────────────────────────────────────────────────

/**
 * Infer the construction zones of a structure. Mirrors the Python's
 * `analyse()`; deliberately overridable via the optional `wall_hi` and
 * `shell` parameters. Returns an empty anatomy for a structure with no
 * solid blocks.
 */
export function analyse(grid: BlockGrid): Anatomy {
  const [w, , d] = grid.size;

  let topY = -1;
  for (const { pos } of grid.blocks()) {
    if (pos[1] > topY) topY = pos[1];
  }
  if (topY < 0) {
    return {
      groundTop: 0,
      wallLo: 0,
      wallHi: 0,
      roofLo: 1,
      roofHi: 0,
      shell: [0, w - 1, 0, d - 1],
    };
  }

  // Ground: contiguous layers from the bottom whose terrain share is >= 0.5.
  let groundTop = -1;
  for (let y = 0; y <= topY; y++) {
    let total = 0;
    let terr = 0;
    for (const { pos, block } of grid.blocks()) {
      if (pos[1] === y) {
        total++;
        if (TERRAIN.has(block.id)) terr++;
      }
    }
    if (total > 0 && terr / total >= 0.5) {
      groundTop = y;
    } else if (groundTop >= 0) {
      break;
    }
  }

  const wallLo = Math.max(0, groundTop + 1);
  let wallHi = topY;
  // Roof begins at the first slab/stair-dominated layer (>= 0.45) that
  // leaves room for at least two wall courses below it.
  for (let y = wallLo + 2; y <= topY; y++) {
    let total = 0;
    let cover = 0;
    for (const { pos, block } of grid.blocks()) {
      if (pos[1] === y) {
        total++;
        if (block.id.endsWith('_slab') || block.id.endsWith('_stairs')) {
          cover++;
        }
      }
    }
    if (total > 0 && cover / total >= 0.45) {
      wallHi = y - 1;
      break;
    }
  }

  const roofLo = wallHi + 1;
  const roofHi = topY;

  // Shell: union of structural bounding boxes in the wall zone.
  let x0 = w, x1 = -1, z0 = d, z1 = -1;
  for (const { pos, block } of grid.blocks()) {
    if (pos[1] < wallLo || pos[1] > wallHi) continue;
    if (NON_STRUCTURAL.has(block.id)) continue;
    if (pos[0] < x0) x0 = pos[0];
    if (pos[0] > x1) x1 = pos[0];
    if (pos[2] < z0) z0 = pos[2];
    if (pos[2] > z1) z1 = pos[2];
  }
  if (x1 < 0) {
    x0 = 0; x1 = w - 1; z0 = 0; z1 = d - 1;
  }

  return {
    groundTop,
    wallLo,
    wallHi,
    roofLo,
    roofHi,
    shell: [x0, x1, z0, z1],
  };
}

// ── helpers ───────────────────────────────────────────────────────────

/** Most common state by exact (id+properties) match. */
function mostCommon(states: BlockState[]): BlockState | null {
  if (states.length === 0) return null;
  const counts = new Map<string, { count: number; state: BlockState }>();
  for (const s of states) {
    const key = stateKey(s);
    const existing = counts.get(key);
    if (existing) existing.count++;
    else counts.set(key, { count: 1, state: s });
  }
  let best = counts.values().next().value!;
  for (const v of counts.values()) {
    if (v.count > best.count) best = v;
  }
  return best.state;
}

function stateKey(s: BlockState): string {
  const props = Object.entries(s.properties)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([k, v]) => `${k}=${v}`)
    .join(',');
  return `${s.id}|${props}`;
}

function isCobbleLike(n: string): boolean {
  return n.startsWith('cobble') || n.includes('stone') || n === 'smooth_stone';
}

function isNotAFloor(n: string): boolean {
  if (NOT_A_FLOOR_NAMES.has(n)) return true;
  return endsWithAny(n, NOT_A_FLOOR_SUFFIXES);
}

function onShell(pos: [number, number, number], shell: [number, number, number, number]): boolean {
  const [x, , z] = pos;
  const [x0, x1, z0, z1] = shell;
  return (x === x0 || x === x1) || (z === z0 || z === z1);
}

function onCorner(pos: [number, number, number], shell: [number, number, number, number]): boolean {
  const [x, , z] = pos;
  const [x0, x1, z0, z1] = shell;
  return (x === x0 || x === x1) && (z === z0 || z === z1);
}

// ── harvest ───────────────────────────────────────────────────────────

/**
 * Pull one block state per construction role out of a donor structure.
 *
 * Equivalent to `compose.py:harvest()`. The donor is walked once; zones are
 * defined by the supplied `anatomy` (or one computed by `analyse()`). All
 * lists are deduped only at the (id+properties) level — `most_common` picks
 * the dominant variant when a role has several.
 */
export function harvest(grid: BlockGrid, donorName: string = '', ana?: Anatomy): Vocabulary {
  const anatomy = ana ?? analyse(grid);
  const v: Vocabulary = {
    donor: donorName,
    apron: [],
    floor: [],
    stone: [],
    timber: [],
    post: null,
    slab_top: null,
    slab_bottom: null,
    stone_slab_top: null,
    window: [],
    fence: null,
    crenel: null,
    stairs: [],
    light: [],
    door_lower: [],
    door_upper: [],
    rail: [],
    roof_stairs: [],
    beam: [],
    hanging: [],
    vegetation: [],
    decoration: [],
  };

  // First pass — block-level roles, and collect all slabs for the slab pass.
  const allSlabs: BlockState[] = [];
  for (const { pos, block } of grid.blocks()) {
    const state = toBlockState(block);
    const n = short(state);

    if (pos[1] <= anatomy.groundTop && TERRAIN.has(n)) {
      v.apron.push(state);
    }
    if (n.endsWith('_door')) {
      if (state.properties.half === 'lower') v.door_lower.push(state);
      else v.door_upper.push(state);
    }
    if (n === 'lantern' || n === 'soul_lantern') {
      v.light.push(state);
      if (state.properties.hanging === 'true') v.hanging.push(state);
    } else if (
      n === 'torch' || n === 'wall_torch' || n === 'soul_torch' ||
      n === 'candle' || n === 'campfire'
    ) {
      v.light.push(state);
    }
    if (n.endsWith('_slab')) {
      allSlabs.push(state);
      if (pos[1] === anatomy.roofHi) v.beam.push(state);
    }
    if (TERRAIN.has(n) === false && (
      n.endsWith('_leaves') || n.endsWith('_sapling') ||
      n.endsWith('_flower') || n.endsWith('_mushroom') ||
      n.endsWith('_crop') || n.endsWith('_stem') ||
      n === 'short_grass' || n === 'tall_grass' || n === 'fern' ||
      n === 'large_fern' || n === 'lily_pad' || n === 'moss_carpet' ||
      n === 'seagrass' || n === 'grass'
    )) {
      v.vegetation.push(state);
    }
  }

  // Second pass — wall zone.
  for (const { pos, block } of grid.blocks()) {
    if (pos[1] < anatomy.wallLo || pos[1] > anatomy.wallHi) continue;
    const state = toBlockState(block);
    const n = short(state);

    // post — _log with axis=y at a perimeter corner
    if (
      v.post === null &&
      n.endsWith('_log') &&
      state.properties.axis === 'y' &&
      onCorner(pos as [number, number, number], anatomy.shell)
    ) {
      v.post = state;
    }

    // stone — cobblestone family and plain stone, not slab/stair/wall
    if (
      (n.includes('cobblestone') || n === 'stone' || n === 'smooth_stone') &&
      !n.endsWith('_slab') && !n.endsWith('_stairs') && !n.endsWith('_wall')
    ) {
      v.stone.push(state);
    }

    // timber — _planks anywhere in the wall zone
    if (n.endsWith('_planks')) {
      v.timber.push(state);
    }

    // floor — first wall course, anything that isn't NOT_A_FLOOR
    if (
      pos[1] === anatomy.wallLo &&
      !isNotAFloor(n)
    ) {
      v.floor.push(state);
    }

    // window — _pane / _bars / glass on a wall cell
    if (n.endsWith('_pane') || n.endsWith('_bars') || n === 'glass') {
      v.window.push(state);
    }

    // fence — _fence, harvested both as `fence` (single) and `rail` (list)
    if (n.endsWith('_fence')) {
      v.rail.push(state);
      if (v.fence === null) v.fence = state;
    }

    // stairs / roof_stairs
    if (n.endsWith('_stairs')) {
      if (onShell(pos as [number, number, number], anatomy.shell)) {
        v.roof_stairs.push(state);
      } else {
        v.stairs.push(state);
      }
    }
  }

  // Slab pass.
  v.slab_top = mostCommon(
    allSlabs.filter((s) => s.properties.type === 'top' && !short(s).startsWith('cobble')),
  );
  v.slab_bottom = mostCommon(
    allSlabs.filter((s) => s.properties.type === 'bottom' && !short(s).startsWith('cobble')),
  );
  v.stone_slab_top = mostCommon(
    allSlabs.filter((s) => s.properties.type === 'top' && isCobbleLike(short(s))),
  );

  // crenel — first stone block (a merlon is a full stone block).
  v.crenel = v.stone[0] ?? { id: 'cobblestone', properties: {} };

  // Fallbacks — every role has a default that occurs in the corpus.
  if (v.apron.length === 0) {
    v.apron = [
      { id: 'grass_block', properties: { snowy: 'false' } },
      { id: 'coarse_dirt', properties: {} },
      { id: 'dirt', properties: {} },
    ];
  }
  if (v.stone.length === 0) {
    v.stone = [
      { id: 'cobblestone', properties: {} },
      { id: 'mossy_cobblestone', properties: {} },
    ];
  }
  if (v.timber.length === 0) v.timber = [{ id: 'oak_planks', properties: {} }];
  if (v.floor.length === 0) v.floor = [{ id: 'oak_planks', properties: {} }];
  if (v.window.length === 0) {
    v.window = [{
      id: 'glass_pane',
      properties: { east: 'false', north: 'true', south: 'true', west: 'false', waterlogged: 'false' },
    }];
  }
  if (v.post === null) v.post = { id: 'oak_log', properties: { axis: 'y' } };
  if (v.fence === null) {
    v.fence = {
      id: 'oak_fence',
      properties: { east: 'false', north: 'true', south: 'true', west: 'false', waterlogged: 'false' },
    };
  }
  if (v.slab_top === null) {
    v.slab_top = { id: 'oak_slab', properties: { type: 'top', waterlogged: 'false' } };
  }
  if (v.slab_bottom === null) {
    v.slab_bottom = { id: 'oak_slab', properties: { type: 'bottom', waterlogged: 'false' } };
  }
  if (v.stone_slab_top === null) {
    v.stone_slab_top = { id: 'cobblestone_slab', properties: { type: 'top', waterlogged: 'false' } };
  }
  if (v.light.length === 0) {
    v.light = [{ id: 'lantern', properties: { hanging: 'false', waterlogged: 'false' } }];
  }
  if (v.door_lower.length === 0) {
    const lower: BlockState = {
      id: 'oak_door',
      properties: { half: 'lower', hinge: 'left', facing: 'south', open: 'false', powered: 'false' },
    };
    v.door_lower = [lower];
    if (v.door_upper.length === 0) {
      v.door_upper = [{ ...lower, properties: { ...lower.properties, half: 'upper' } }];
    }
  } else if (v.door_upper.length === 0) {
    v.door_upper = v.door_lower.map((d) => ({
      ...d,
      properties: { ...d.properties, half: 'upper' },
    }));
  }
  if (v.rail.length === 0 && v.fence !== null) v.rail = [v.fence];
  if (v.roof_stairs.length === 0) {
    v.roof_stairs = [{ id: 'oak_stairs', properties: { facing: 'north', half: 'bottom' } }];
  }
  if (v.beam.length === 0 && v.slab_bottom !== null) v.beam = [v.slab_bottom];
  if (v.hanging.length === 0 && v.light.length > 0) {
    // No hanging lanterns harvested; keep the first light as a fallback so
    // the generator has something to use when explicitly hanging one.
    v.hanging = [v.light[0]];
  }
  if (v.vegetation.length === 0) {
    v.vegetation = [{ id: 'oak_leaves', properties: { persistent: 'true' } }];
  }
  if (v.decoration.length === 0) v.decoration = [{ id: 'barrel', properties: { facing: 'up' } }];

  return v;
}

// ── merge ─────────────────────────────────────────────────────────────

const LIST_ROLES: ReadonlyArray<keyof Vocabulary> = [
  'apron', 'floor', 'stone', 'timber', 'window', 'stairs', 'light',
  'door_lower', 'door_upper', 'rail', 'roof_stairs', 'beam',
  'hanging', 'vegetation', 'decoration',
];

const SINGLE_ROLES: ReadonlyArray<keyof Vocabulary> = [
  'post', 'slab_top', 'slab_bottom', 'stone_slab_top', 'fence', 'crenel',
];

/**
 * Fill roles missing from `primary` using later vocabularies, in order.
 *
 * Mirrors `compose.py:merge()`. Roles in `primary` always win; later
 * vocabularies only contribute when `primary` did not have that role.
 * `donor` becomes the joined `donor` string.
 */
export function merge(primary: Vocabulary, ...others: Vocabulary[]): Vocabulary {
  const out: Vocabulary = {
    donor: [primary.donor, ...others.map((o) => o.donor)].filter((d) => d.length > 0).join(' + '),
    apron: [...primary.apron],
    floor: [...primary.floor],
    stone: [...primary.stone],
    timber: [...primary.timber],
    post: primary.post,
    slab_top: primary.slab_top,
    slab_bottom: primary.slab_bottom,
    stone_slab_top: primary.stone_slab_top,
    window: [...primary.window],
    fence: primary.fence,
    crenel: primary.crenel,
    stairs: [...primary.stairs],
    light: [...primary.light],
    door_lower: [...primary.door_lower],
    door_upper: [...primary.door_upper],
    rail: [...primary.rail],
    roof_stairs: [...primary.roof_stairs],
    beam: [...primary.beam],
    hanging: [...primary.hanging],
    vegetation: [...primary.vegetation],
    decoration: [...primary.decoration],
  };

  for (const role of LIST_ROLES) {
    if ((out[role] as BlockState[]).length > 0) continue;
    for (const o of others) {
      const v = o[role] as BlockState[];
      if (v.length > 0) {
        (out as unknown as Record<string, unknown>)[role as string] = [...v];
        break;
      }
    }
  }

  for (const role of SINGLE_ROLES) {
    if (out[role] !== null) continue;
    for (const o of others) {
      if (o[role] !== null) {
        (out as unknown as Record<string, unknown>)[role as string] = o[role];
        break;
      }
    }
  }

  return out;
}

/**
 * Pick a single block state from a vocabulary list, deterministically by
 * index. Used by the generator to take one variant when several are present.
 * `rng` is a 0..1 source; we don't take a full `Random` here to keep the
 * vocabulary free of side-effecting inputs.
 */
export function pickFromList(list: BlockState[], index: number): BlockState | null {
  if (list.length === 0) return null;
  return list[((index % list.length) + list.length) % list.length] ?? null;
}

/**
 * One-line summary of a vocabulary — `donor`, role counts, single-role fills.
 * Mirrors `Vocabulary.describe()` from the Python.
 */
export function describe(v: Vocabulary): string {
  return (
    `vocab from ${v.donor || '<empty>'}: `
    + `stone=${v.stone.length} `
    + `timber=${v.timber.length} `
    + `window=${v.window.length} `
    + `roof_stairs=${v.roof_stairs.length} `
    + `post=${v.post?.id ?? 'null'} `
    + `crenel=${v.crenel?.id ?? 'null'}`
  );
}
