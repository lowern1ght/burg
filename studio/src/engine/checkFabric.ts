/**
 * Is the fence actually connected, and is the roof actually a roof? — ported
 * from tools/check_fabric.py.
 *
 * Five checks, each taking a BlockGrid and returning arrays of human-readable
 * finding strings:
 *
 *   fenceFaults(grid)               — fence/pane/bars/wall cells whose connection
 *                                     booleans disagree with the actual grid, plus
 *                                     posts that connect to nothing at all.
 *   roofFaults(grid, minY = 3)      — hanging roof blocks, holes in a roof plane,
 *                                     enclosed floor cells open to the sky.
 *   lineFaults(grid, y = 1)         — diagonal fence steps, duplicate parallel runs.
 *   slabFaults(grid)                — cubes/rails sitting in the empty upper half
 *                                     of a bottom slab's cell (FabricGuard).
 *   cantileverFaults(grid, minY = 2) — roof blocks with no support column within
 *                                     3 cells.
 *
 * Calibrated over the author's 115 building-like NBTs before any of it was
 * believed (see tools/check_fabric.py docstring). The thresholds that gate:
 *
 *   roof blocks hanging ........... HANGING_MAX = 0  (zero across every author file)
 *   gaps in a roof plane .......... HOLES_MAX  = 2  (his worst case, granary_lvl5..7)
 *   slab riders ................... SLAB_RIDERS_MAX = 0
 *   wrong fence props ............. hard fault for generated files only; the author
 *                                   leaves stale states in 29 of his hand-built files.
 *
 * Counted for information, never failed:
 *   rails connecting to nothing — his own idiom (36 in merchant_shop_lvl6);
 *   enclosed cells open to sky  — courtyards, open work bays.
 */

import { type BlockGrid, type BlockInfo } from './appearance';
import { FabricGuard, type Coord } from './fabric';

// ── constants (from pasture.py, measured off the author's 121 files) ──────

/** Blocks a fence connects to. */
const STURDY = new Set([
  'oak_planks', 'oak_log', 'cobblestone', 'mossy_cobblestone', 'stone',
  'stripped_oak_log', 'hay_block', 'white_terracotta', 'crafting_table',
  'andesite', 'stone_bricks',
]);

/** Direction → (dx, dz). */
const VEC: Record<string, [number, number]> = {
  north: [0, -1], south: [0, 1], west: [-1, 0], east: [1, 0],
};

/** Facing → axis label (only equality/inequality is ever tested). */
const AXIS_OF: Record<string, string> = {
  north: 'ns', south: 'ns', west: 'ew', east: 'ew',
};

const RAIL_SUFFIX = ['_fence', '_pane', '_bars'];
const DIRECTIONS = ['north', 'south', 'east', 'west'] as const;
const NEIGH4: ReadonlyArray<[number, number]> = [[1, 0], [-1, 0], [0, 1], [0, -1]];
const DIAGONALS: ReadonlyArray<[number, number]> = [[1, 1], [1, -1], [-1, 1], [-1, -1]];
const PARALLEL: ReadonlyArray<[number, number]> = [[2, 0], [0, 2]];

/** Too thin or too small to bridge a corner or fill a dead cell. */
const SIDE_ATTACHED_SOFT = new Set([
  'short_grass', 'grass', 'oak_sapling', 'flower_pot', 'torch',
  'wall_torch', 'lantern', 'chain', 'dandelion', 'poppy',
]);

// Thresholds (measured over the author's corpus; only three of the five gate).
const HANGING_MAX = 0;
const HOLES_MAX = 2;
const SLAB_RIDERS_MAX = 0;

function compareCoord(a: Coord, b: Coord): number {
  return a[0] - b[0] || a[1] - b[1] || a[2] - b[2];
}

function endsWithAny(s: string, suffixes: readonly string[]): boolean {
  return suffixes.some(suffix => s.endsWith(suffix));
}

function isRoofMaterialId(id: string): boolean {
  return id.endsWith('_slab') || id.endsWith('_stairs');
}

/** A roof neighbour for the hole-ring check: slabs, stairs, planks, hay. */
function isRoofBlock(b: BlockInfo | null): boolean {
  return b !== null && (isRoofMaterialId(b.id) || b.id === 'oak_planks' || b.id === 'hay_block');
}

/** Sorted view of grid.blocks() by (x, y, z) — deterministic output order. */
function sortedBlocks(grid: BlockGrid): Array<{ pos: Coord; block: BlockInfo }> {
  return [...grid.blocks()].sort((a, b) => compareCoord(a.pos, b.pos));
}

// ── fences ────────────────────────────────────────────────────────────────

/**
 * Would a rail at `p` connect toward `direction` in the actual game?
 *
 * Same rule `pasture.reconnect` derives from, measured off the author's corpus —
 * including the part that says a fence and a `*_wall` block do NOT connect.
 */
function links(grid: BlockGrid, p: Coord, direction: string, family: 'rail' | 'wall'): boolean {
  const [dx, dz] = VEC[direction];
  const nb = grid.get(p[0] + dx, p[1], p[2] + dz);
  if (nb === null) return false;
  const n = nb.id;
  if (STURDY.has(n)) return true;
  if (n.endsWith('_fence_gate')) {
    return AXIS_OF[nb.props['facing'] ?? 'north'] !== AXIS_OF[direction];
  }
  if (family === 'rail') {
    return endsWithAny(n, RAIL_SUFFIX);
  }
  return n.endsWith('_wall');
}

/**
 * Cells whose connection props are wrong, and posts connecting to nothing.
 * A rail-to-nothing post is the author's own idiom — reported, not failed.
 */
export function fenceFaults(grid: BlockGrid): { wrong: string[]; stumps: string[] } {
  const wrong: string[] = [];
  const stumps: string[] = [];
  for (const { pos: p, block: b } of sortedBlocks(grid)) {
    const n = b.id;
    if (!(endsWithAny(n, RAIL_SUFFIX) || n.endsWith('_wall'))) continue;
    const family: 'rail' | 'wall' = n.endsWith('_wall') ? 'wall' : 'rail';
    const want: Record<string, boolean> = {};
    const have: Record<string, boolean> = {};
    for (const d of DIRECTIONS) {
      want[d] = links(grid, p, d, family);
      have[d] = n.endsWith('_wall')
        ? (b.props[d] ?? 'none') !== 'none'
        : (b.props[d] ?? 'false') === 'true';
    }
    if (DIRECTIONS.some(d => have[d] !== want[d])) {
      const bad = DIRECTIONS.filter(d => have[d] !== want[d]);
      const hasList = DIRECTIONS.filter(d => have[d]);
      const wantList = DIRECTIONS.filter(d => want[d]);
      wrong.push(
        `${n}@(${p.join(', ')}) ${bad.join(',')}: has [${hasList.join(', ')}] wants [${wantList.join(', ')}]`,
      );
    }
    if (!DIRECTIONS.some(d => want[d])) {
      stumps.push(`${n}@(${p.join(', ')})`);
    }
  }
  return { wrong, stumps };
}

// ── roofs ─────────────────────────────────────────────────────────────────

/** Hanging blocks, holes in a plane, and enclosed floor cells open to the sky. */
export function roofFaults(grid: BlockGrid, minY = 3): {
  hanging: string[];
  holed: string[];
  sky: string[];
} {
  const hanging: string[] = [];
  const holed: string[] = [];
  const sky: string[] = [];

  const solid = new Set<string>();
  for (const { pos } of grid.blocks()) solid.add(`${pos[0]},${pos[1]},${pos[2]}`);
  const inSolid = (x: number, y: number, z: number): boolean => solid.has(`${x},${y},${z}`);

  for (const { pos: p, block: b } of sortedBlocks(grid)) {
    if (p[1] < minY || !isRoofMaterialId(b.id)) continue;
    if (inSolid(p[0], p[1] - 1, p[2])) continue;
    if (NEIGH4.some(([dx, dz]) => inSolid(p[0] + dx, p[1], p[2] + dz))) continue;
    const stepped = NEIGH4.some(
      ([dx, dz]) => inSolid(p[0] + dx, p[1] - 1, p[2] + dz) || inSolid(p[0] + dx, p[1] + 1, p[2] + dz),
    );
    if (stepped) continue;
    hanging.push(`${b.id}@(${p.join(', ')})`);
  }

  // A hole: empty cell with roof on three or more sides at its own height.
  const [sx, sy, sz] = grid.size;
  for (let y = minY; y < sy; y++) {
    for (let x = 1; x < sx - 1; x++) {
      for (let z = 1; z < sz - 1; z++) {
        if (inSolid(x, y, z)) continue;
        let ring = 0;
        for (const [dx, dz] of NEIGH4) {
          if (isRoofBlock(grid.get(x + dx, y, z + dz))) ring++;
        }
        if (ring >= 3 && !grid.occupied(x, y + 1, z)) {
          holed.push(`gap@(${x}, ${y}, ${z}) roof on ${ring} sides`);
        }
      }
    }
  }

  // Enclosed floor cells with nothing above them. Normal in the corpus —
  // courtyards, open work bays — so counted rather than failed.
  for (let x = 1; x < sx - 1; x++) {
    for (let z = 1; z < sz - 1; z++) {
      if (grid.occupied(x, 1, z)) continue;
      const walled = NEIGH4.every(([dx, dz]) => grid.occupied(x + dx, 1, z + dz));
      let roofed = false;
      for (let y = 2; y < sy; y++) {
        if (grid.occupied(x, y, z)) {
          roofed = true;
          break;
        }
      }
      if (walled && !roofed) {
        sky.push(`enclosed cell open to the sky@(${x}, 1, ${z})`);
      }
    }
  }
  return { hanging, holed, sky };
}

// ── the boundary line ─────────────────────────────────────────────────────

/**
 * Two ways a fence line reads as broken even when it holds animals:
 *   - diagonal step — two rails meeting only at a corner;
 *   - duplicate run — two parallel lines one cell apart with a dead cell between.
 *
 * Anything solid in the corner cell bridges the step visually (a slab, a
 * trapdoor, a bed). Requiring a *barrier* there reported 14 false positives
 * inside the author's own house; the question is whether the run looks broken,
 * not whether a cow could squeeze through.
 */
export function lineFaults(grid: BlockGrid, y = 1): { diagonal: string[]; duplicate: string[] } {
  const diagonal: string[] = [];
  const duplicate: string[] = [];
  const rails = new Set<string>();
  const barrier = new Set<string>();
  for (const { pos: p, block: b } of grid.blocks()) {
    if (p[1] !== y) continue;
    const id = b.id;
    if (id.endsWith('_fence') || id.endsWith('_fence_gate') || id.endsWith('_wall')) {
      rails.add(`${p[0]},${p[2]}`);
    }
    if (!SIDE_ATTACHED_SOFT.has(id)) {
      barrier.add(`${p[0]},${p[2]}`);
    }
  }
  const railCoords = [...rails]
    .map(s => s.split(',').map(Number) as [number, number])
    .sort((a, b) => a[0] - b[0] || a[1] - b[1]);

  for (const [x, z] of railCoords) {
    for (const [dx, dz] of DIAGONALS) {
      const qx = x + dx;
      const qz = z + dz;
      if (!rails.has(`${qx},${qz}`)) continue;
      // A shared orthogonal neighbour makes the two part of one run.
      if (barrier.has(`${x + dx},${z}`) || barrier.has(`${x},${z + dz}`)) continue;
      diagonal.push(`(${x}, ${z}) meets (${qx}, ${qz}) only diagonally`);
    }
  }

  const seen = new Set<string>();
  for (const [x, z] of railCoords) {
    for (const [dx, dz] of PARALLEL) {
      const qx = x + dx;
      const qz = z + dz;
      const mx = x + Math.floor(dx / 2);
      const mz = z + Math.floor(dz / 2);
      const midKey = `${mx},${mz}`;
      const seenKey = `${midKey}|${qx},${qz}`;
      if (rails.has(`${qx},${qz}`) && !barrier.has(midKey) && !seen.has(seenKey)) {
        seen.add(seenKey);
        duplicate.push(
          `(${x}, ${z}) and (${qx}, ${qz}) run parallel with (${mx}, ${mz}) dead between`,
        );
      }
    }
  }
  return { diagonal, duplicate };
}

// ── half blocks ───────────────────────────────────────────────────────────

/**
 * Cubes and rails sitting in the empty upper half of a bottom slab's cell.
 *
 * Delegated to `FabricGuard`, which is the same code that refuses the write in
 * the first place — one authority, so the writer and the checker cannot drift.
 */
export function slabFaults(grid: BlockGrid): string[] {
  const guard = new FabricGuard();
  return guard.inspectAll(grid).map(f => `${f.kind}: ${f.detail} at (${f.pos.join(', ')})`);
}

// ── cantilevers ───────────────────────────────────────────────────────────

/**
 * Roof blocks too far from anything holding them up.
 *
 * Measured over the author's 121 files: a roof block with no column under it is
 * 1 cell from a supported column 2076 times, 2 cells 355 times, 3 cells ten
 * times and 4 cells once (`oven.nbt`). A deep eave is his idiom; a shelf
 * floating four cells out is not. Here we report blocks with NO supported
 * column within 3 cells.
 */
export function cantileverFaults(grid: BlockGrid, minY = 2): string[] {
  const out: string[] = [];
  const solid = new Set<string>();
  for (const { pos } of grid.blocks()) solid.add(`${pos[0]},${pos[1]},${pos[2]}`);
  const inSolid = (x: number, y: number, z: number): boolean => solid.has(`${x},${y},${z}`);
  const supported = (x: number, z: number, y: number): boolean => {
    for (let yy = 1; yy < y; yy++) {
      if (inSolid(x, yy, z)) return true;
    }
    return false;
  };

  for (const { pos: p, block: b } of sortedBlocks(grid)) {
    const [x, y, z] = p;
    if (y < minY || !isRoofMaterialId(b.id)) continue;
    if (supported(x, z, y)) continue;
    let reach: number | null = null;
    search: for (let r = 1; r <= 3; r++) {
      for (let dx = -r; dx <= r; dx++) {
        const dz = r - Math.abs(dx);
        const cands: ReadonlyArray<[number, number]> = [[x + dx, z + dz], [x + dx, z - dz]];
        for (const [cx, cz] of cands) {
          if (inSolid(cx, y, cz) && supported(cx, cz, y)) {
            reach = r;
            break search;
          }
        }
      }
    }
    if (reach === null) {
      out.push(`${b.id}@(${p.join(', ')}) has no support within 3 cells`);
    }
  }
  return out;
}

// ── combined report ───────────────────────────────────────────────────────

export type FabricFaultBucket = { label: string; items: string[] };

export type FabricReport = {
  fence: { wrong: string[]; stumps: string[] };
  roof: { hanging: string[]; holed: string[]; sky: string[] };
  line: { diagonal: string[]; duplicate: string[] };
  slab: string[];
  cantilever: string[];
  /** Buckets that exceed their measured threshold — the actual failures. */
  faults: FabricFaultBucket[];
};

/**
 * Run all five fabric checks and return a structured report. `faults` holds
 * only the buckets whose count crosses its measured threshold; the raw arrays
 * are kept on the report for the UI and for information-only counts.
 */
export function checkFabric(grid: BlockGrid): FabricReport {
  const fence = fenceFaults(grid);
  const roof = roofFaults(grid);
  const line = lineFaults(grid);
  const slab = slabFaults(grid);
  const cantilever = cantileverFaults(grid);

  const faults: FabricFaultBucket[] = [];
  if (cantilever.length > 0) faults.push({ label: 'cantilever', items: cantilever });
  if (slab.length > SLAB_RIDERS_MAX) faults.push({ label: 'slab-rider', items: slab });
  // Wrong fence props are a hard fault for generated files only; the author
  // leaves stale states in 29 of his own hand-built files.
  if (fence.wrong.length > 0) faults.push({ label: 'fence-props', items: fence.wrong });
  if (roof.hanging.length > HANGING_MAX) faults.push({ label: 'roof-hanging', items: roof.hanging });
  if (roof.holed.length > HOLES_MAX) faults.push({ label: 'roof-holes', items: roof.holed });

  return { fence, roof, line, slab, cantilever, faults };
}
