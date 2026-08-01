/**
 * The build sheet — ported from tools/describe.py.
 *
 * `describe.py` prints every number beside the author's measured band so "normal"
 * and "wrong" can be told apart without taste. This port carries the grid-derivable
 * measurements into the studio engine: block counts, vocabulary, the visible-face
 * family shares, the rotation conventions, and the workstation trade signature.
 *
 * Two structures are compared by their own functions:
 *  - similarity(a, b)   cosine of block-state counts, terrain layer (y < 2) excluded;
 *  - growth(a, b)       kept / changed / added / removed between two rungs.
 *
 * The corpus-band comparison (`corpus_bands.json`, written by the Python tool over
 * the 125 plains files) is intentionally not reproduced here: those bands are an
 * offline measurement of files the TS engine does not see. This module reports the
 * raw numbers; the verdict-against-his-band layer stays Python-side for now.
 */

import { type BlockGrid, type BlockInfo } from './appearance';

// ── families, in the order a build earns them ──────────────────────
// Grouping exists because "cobblestone 31" means nothing on its own, while
// "stone 44% of visible faces" is a finding.

const FAMILY: Record<string, readonly string[]> = {
  earth: [
    'dirt', 'coarse_dirt', 'dirt_path', 'grass_block', 'rooted_dirt',
    'farmland', 'mud', 'packed_mud', 'podzol', 'gravel', 'sand', 'clay',
  ],
  stone: [
    'cobblestone', 'mossy_cobblestone', 'stone', 'andesite', 'tuff',
    'stone_bricks', 'bricks', 'polished_andesite', 'smooth_stone',
  ],
  timber: [
    'oak_log', 'spruce_log', 'stripped_oak_log', 'stripped_spruce_log',
    'oak_planks', 'spruce_planks', 'oak_fence', 'spruce_fence',
  ],
  plant: [
    'oak_leaves', 'short_grass', 'fern', 'oak_sapling', 'lily_pad',
    'sugar_cane', 'wheat', 'hay_block',
  ],
  water: ['water'],
  light: ['torch', 'wall_torch', 'lantern', 'chain', 'campfire'],
};

// [suffix, forced] — if forced, the family is the suffix's; else strip the suffix
// and keep classifying by the base id.
const SUFFIX_FAMILY: ReadonlyArray<readonly [string, string | null]> = [
  ['_slab', null],
  ['_stairs', null],
  ['_wall', 'stone'],
  ['_fence', 'timber'],
  ['_fence_gate', 'timber'],
  ['_trapdoor', 'timber'],
  ['_door', 'timber'],
  ['_planks', 'timber'],
  ['_log', 'timber'],
  ['_leaves', 'plant'],
];

const WOOD_SPECIES = [
  'oak', 'spruce', 'birch', 'jungle', 'acacia', 'dark_oak',
  'mangrove', 'cherry', 'bamboo', 'crimson', 'warped',
];

const NEIGH6: [number, number, number][] = [
  [1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1],
];

const ORTH4: [number, number][] = [[1, 0], [-1, 0], [0, 1], [0, -1]];

const FACING_STEP: ReadonlyMap<string, [number, number]> = new Map([
  ['north', [0, -1]],
  ['south', [0, 1]],
  ['west', [-1, 0]],
  ['east', [1, 0]],
]);

// The blocks that make a building a workplace. From his 196 workstations in
// `plains/jobs/**`: crafting_table in 67 files, furnace 41, smoker 17, cauldron 7,
// anvil zero, grindstone zero, smithing_table zero.
const WORKSTATIONS = new Set([
  'crafting_table', 'furnace', 'smoker', 'blast_furnace', 'cauldron', 'barrel',
  'chest', 'loom', 'cartography_table', 'fletching_table', 'smithing_table',
  'stonecutter', 'grindstone', 'anvil', 'beehive', 'bee_nest', 'composter',
  'brewing_stand', 'lectern', 'bookshelf', 'bell', 'campfire',
]);

const DOOR_SUFFIXES = ['_door', '_fence_gate'];

// ── exported types ─────────────────────────────────────────────────

export type BlockCount = {
  id: string;
  count: number;
};

/** Share of visible faces by family, percent. Zero when the family is absent. */
export type FamilyShares = {
  earth: number;
  stone: number;
  timber: number;
  plant: number;
  water: number;
  light: number;
  other: number;
};

/**
 * Rotation conventions measured off the author's files.
 * Stair `facing` names the tall half: 648 of his runs climb toward their facing,
 * none against. A beam that lies should be stripped.
 */
export type RotationReport = {
  stairsUp: number;
  stairsDown: number;
  logsStanding: number;
  logsFlat: number;
  trapdoorsTotal: number;
  trapdoorsShutter: number;
};

/** A workstation with the two numbers his placements are measured by. */
export type Workstation = {
  id: string;
  pos: [number, number, number];
  facing: string | null;
  /** Free orthogonal neighbours (his 54% have exactly one — a walled niche). */
  freeNeighbours: number;
  /** Manhattan distance to the nearest door/gate, -1 when there are none. */
  doorDistance: number;
};

export type BuildSheet = {
  size: [number, number, number];
  solidCount: number;
  /** Distinct block ids. */
  vocabulary: number;
  topId: string;
  topIdCount: number;
  /** Every id, sorted by count descending (ties broken by id). */
  blockCounts: BlockCount[];
  top10: BlockCount[];
  families: FamilyShares;
  rotation: RotationReport;
  /** Trade signature: workstations and how they sit. */
  workstations: Workstation[];
};

export type GrowthReport = {
  kept: number;
  changed: number;
  added: number;
  removed: number;
  keptCount: number;
  changedCount: number;
  addedCount: number;
  removedCount: number;
  /** Size of the base grid (A) — the denominator for all four percentages. */
  base: number;
};

// ── helpers ────────────────────────────────────────────────────────

function familyOf(short: string): string {
  let base = short;
  for (const [suffix, forced] of SUFFIX_FAMILY) {
    if (short.endsWith(suffix)) {
      if (forced) return forced;
      base = short.slice(0, -suffix.length);
      break;
    }
  }
  for (const [name, ids] of Object.entries(FAMILY)) {
    if (ids.includes(base) || ids.includes(short)) return name;
  }
  if (WOOD_SPECIES.some(w => base.startsWith(w))) return 'timber';
  if (base === 'cobblestone' || base === 'stone' || base === 'andesite' || base === 'tuff') {
    return 'stone';
  }
  return 'other';
}

/** Canonical block-state key: id plus properties sorted by key. */
function stateKey(b: BlockInfo): string {
  const keys = Object.keys(b.props).sort();
  if (keys.length === 0) return b.id;
  const body = keys.map(k => `${k}=${b.props[k]}`).join(',');
  return `${b.id}[${body}]`;
}

function comparePos(a: [number, number, number], b: [number, number, number]): number {
  return a[0] - b[0] || a[1] - b[1] || a[2] - b[2];
}

/** Count faces exposed to air per block id — the honest denominator for "look". */
function visibleFaces(grid: BlockGrid): Map<string, number> {
  const solid = new Set<string>();
  for (const { pos } of grid.blocks()) solid.add(pos.join(','));
  const out = new Map<string, number>();
  for (const { pos: p, block: b } of grid.blocks()) {
    const [x, y, z] = p;
    let n = 0;
    for (const [dx, dy, dz] of NEIGH6) {
      if (!solid.has(`${x + dx},${y + dy},${z + dz}`)) n++;
    }
    if (n > 0) out.set(b.id, (out.get(b.id) ?? 0) + n);
  }
  return out;
}

function familyShares(faces: Map<string, number>): FamilyShares {
  let total = 0;
  for (const n of faces.values()) total += n;
  if (total === 0) total = 1;
  const agg = new Map<string, number>();
  for (const [short, n] of faces) {
    const fam = familyOf(short);
    agg.set(fam, (agg.get(fam) ?? 0) + n);
  }
  const out: FamilyShares = {
    earth: 0, stone: 0, timber: 0, plant: 0, water: 0, light: 0, other: 0,
  };
  for (const [k, v] of agg) out[k as keyof FamilyShares] = (100.0 * v) / total;
  return out;
}

function blockCounts(grid: BlockGrid): {
  counts: BlockCount[];
  total: number;
} {
  const m = new Map<string, number>();
  for (const { block } of grid.blocks()) {
    m.set(block.id, (m.get(block.id) ?? 0) + 1);
  }
  const counts = [...m.entries()]
    .map(([id, count]) => ({ id, count }))
    .sort((a, b) => b.count - a.count || a.id.localeCompare(b.id));
  const total = counts.reduce((s, c) => s + c.count, 0);
  return { counts, total };
}

function rotationReport(grid: BlockGrid): RotationReport {
  const solid = new Set<string>();
  for (const { pos } of grid.blocks()) solid.add(pos.join(','));

  let stairsUp = 0;
  let stairsDown = 0;
  let logsStanding = 0;
  let logsFlat = 0;
  let trapdoorsTotal = 0;
  let trapdoorsShutter = 0;

  for (const { pos: p, block: b } of grid.blocks()) {
    const [x, y, z] = p;
    const n = b.id;
    if (n.endsWith('_stairs')) {
      const step = FACING_STEP.get(b.props['facing'] ?? '');
      if (step !== undefined) {
        const [dx, dz] = step;
        const ahead = solid.has(`${x + dx},${y},${z + dz}`);
        const behind = solid.has(`${x - dx},${y},${z - dz}`);
        if (ahead && !behind) stairsUp++;
        else if (behind && !ahead) stairsDown++;
      }
    } else if (n.endsWith('_log') || n.endsWith('_stem')) {
      if (b.props['axis'] === 'y') logsStanding++;
      else logsFlat++;
    } else if (n.endsWith('_trapdoor')) {
      trapdoorsTotal++;
      if (b.props['half'] === 'top' && b.props['open'] === 'true') trapdoorsShutter++;
    }
  }

  return {
    stairsUp, stairsDown, logsStanding, logsFlat, trapdoorsTotal, trapdoorsShutter,
  };
}

function workstationList(grid: BlockGrid): Workstation[] {
  const solid = new Set<string>();
  const doorCells: [number, number, number][] = [];
  for (const { pos, block } of grid.blocks()) {
    solid.add(pos.join(','));
    if (DOOR_SUFFIXES.some(s => block.id.endsWith(s))) doorCells.push(pos);
  }

  const sorted = [...grid.blocks()].sort((a, b) => comparePos(a.pos, b.pos));
  const out: Workstation[] = [];
  for (const { pos: p, block: b } of sorted) {
    if (!WORKSTATIONS.has(b.id)) continue;
    const [x, y, z] = p;
    let free = 0;
    for (const [dx, dz] of ORTH4) {
      if (!solid.has(`${x + dx},${y},${z + dz}`)) free++;
    }
    const doorDistance = doorCells.length === 0
      ? -1
      : Math.min(...doorCells.map(d => Math.abs(x - d[0]) + Math.abs(z - d[2])));
    out.push({
      id: b.id,
      pos: p,
      facing: b.props['facing'] ?? null,
      freeNeighbours: free,
      doorDistance,
    });
  }
  return out;
}

function stateVector(grid: BlockGrid, minY: number): Map<string, number> {
  const c = new Map<string, number>();
  for (const { pos, block } of grid.blocks()) {
    if (pos[1] < minY) continue;
    const k = stateKey(block);
    c.set(k, (c.get(k) ?? 0) + 1);
  }
  return c;
}

// ── public API ─────────────────────────────────────────────────────

/** The full build sheet for a single structure. */
export function describe(grid: BlockGrid): BuildSheet {
  const { counts, total } = blockCounts(grid);
  const top = counts[0];
  return {
    size: grid.size,
    solidCount: total,
    vocabulary: counts.length,
    topId: top ? top.id : '',
    topIdCount: top ? top.count : 0,
    blockCounts: counts,
    top10: counts.slice(0, 10),
    families: familyShares(visibleFaces(grid)),
    rotation: rotationReport(grid),
    workstations: workstationList(grid),
  };
}

/**
 * Cosine similarity of block-state counts with the terrain layer excluded.
 *
 * Ground is 60% of a plot and hides the difference, so y < minY is dropped
 * (default 2). His bands are ~0.79 mean across the house ladder; a generated
 * set at 0.93 is one building three times. Returns 0 when either side is empty.
 */
export function similarity(a: BlockGrid, b: BlockGrid, minY = 2): number {
  const va = stateVector(a, minY);
  const vb = stateVector(b, minY);
  const keys = new Set<string>([...va.keys(), ...vb.keys()]);
  let dot = 0;
  for (const k of keys) dot += (va.get(k) ?? 0) * (vb.get(k) ?? 0);
  let na = 0;
  for (const v of va.values()) na += v * v;
  let nb = 0;
  for (const v of vb.values()) nb += v * v;
  na = Math.sqrt(na);
  nb = Math.sqrt(nb);
  return na && nb ? dot / (na * nb) : 0.0;
}

/**
 * kept / changed / added / removed between two rungs.
 *
 * `a` is the previous level, `b` the new one. For each cell in their union:
 * kept = same state in both, changed = different state, added = in b not a,
 * removed = in a not b. Percentages are over max(1, |a|) — his 75% keep law.
 */
export function growth(a: BlockGrid, b: BlockGrid): GrowthReport {
  const am = new Map<string, string>();
  const bm = new Map<string, string>();
  for (const { pos, block } of a.blocks()) am.set(pos.join(','), stateKey(block));
  for (const { pos, block } of b.blocks()) bm.set(pos.join(','), stateKey(block));

  let kept = 0;
  let changed = 0;
  for (const [k, v] of am) {
    const bv = bm.get(k);
    if (bv === undefined) continue;
    if (bv === v) kept++;
    else changed++;
  }
  const removed = am.size - kept - changed;
  const added = bm.size - kept - changed;
  const denom = Math.max(1, am.size);

  return {
    kept: (100.0 * kept) / denom,
    changed: (100.0 * changed) / denom,
    added: (100.0 * added) / denom,
    removed: (100.0 * removed) / denom,
    keptCount: kept,
    changedCount: changed,
    addedCount: added,
    removedCount: removed,
    base: am.size,
  };
}
