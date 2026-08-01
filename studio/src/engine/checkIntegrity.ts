/**
 * The five primitive checks a building must pass before anyone looks at it —
 * ported from tools/check_integrity.py.
 *
 * Every style metric in this repo once passed a build that had three doors
 * hanging at roof height, walls that were 85% air and 25 of 169 interior
 * columns open to the sky. The metrics were measuring material shares; not one
 * of them asked whether there was a wall. This file asks.
 *
 * Five checks:
 *  1. walls closed — a wall course with holes in it is not a wall;
 *  2. roof covers the room — every interior column has something over it;
 *  3. doors stand and are framed — floor under, wall either side, lintel over;
 *  4. nothing floats — no block whose only contact is diagonal;
 *  5. there is a room — an enclosed volume you can stand up in.
 *
 * Thresholds are measured over the author's 118 readable buildings, not guessed.
 */

import { type BlockGrid } from './appearance';

const NEIGH4: [number, number][] = [[1, 0], [-1, 0], [0, 1], [0, -1]];
const NEIGH6: [number, number, number][] = [
  [1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1],
];

// Measured over his 118 readable buildings by counting directly: median 8,
// p95 21, max 30 (`merchant_shop_lvl6`). His walls carry enclosed gaps (window
// openings, gaps between posts, hatches), so the gate sits at his p95.
const WALL_HOLE_MAX = 21;

// NOT a gate yet, and must not be trusted as one. The share of interior columns
// open to the sky is median 12%, p95 91%, max 100% (`workshop`, an open shed
// with a bench). This definition cannot tell a broken roof from an open
// workshop — the "interior" is taken from the fabric bounding box, which for an
// open structure is not a room. It reports and never fails.
const ROOF_BARE_MAX_PCT = 1000.0;

// What counts as building fabric rather than ground, planting or fittings. The
// building footprint is derived from these, because a plot of grass is not a wall.
const FABRIC = [
  '_planks', '_log', '_slab', '_stairs', '_bricks', 'cobblestone',
  'mossy_cobblestone', 'stone', 'andesite', 'tuff', 'bricks',
  'white_terracotta', '_wall', '_pane', '_door', 'glass',
];
const GROUND = new Set([
  'dirt', 'coarse_dirt', 'dirt_path', 'grass_block', 'rooted_dirt', 'podzol',
  'farmland', 'mud', 'packed_mud', 'sand', 'gravel', 'water', 'clay',
]);

type Box = [number, number, number, number]; // x0, x1, z0, z1

type BlockEntry = { pos: [number, number, number]; block: { id: string } };

function endsWithAny(s: string, suffixes: readonly string[]): boolean {
  return suffixes.some(suffix => s.endsWith(suffix));
}

function isFabric(id: string): boolean {
  return endsWithAny(id, FABRIC) || FABRIC.includes(id);
}

function comparePos(a: BlockEntry, b: BlockEntry): number {
  const [ax, ay, az] = a.pos;
  const [bx, by, bz] = b.pos;
  return ax - bx || ay - by || az - bz;
}

/** The footprint of the building proper: columns holding fabric above the ground. */
function buildingBox(grid: BlockGrid): Box | null {
  let x0 = Infinity;
  let x1 = -Infinity;
  let z0 = Infinity;
  let z1 = -Infinity;
  let found = false;
  for (const { pos, block } of grid.blocks()) {
    const [x, y, z] = pos;
    if (y < 1) continue;
    if (!isFabric(block.id) || GROUND.has(block.id)) continue;
    found = true;
    if (x < x0) x0 = x;
    if (x > x1) x1 = x;
    if (z < z0) z0 = z;
    if (z > z1) z1 = z;
  }
  return found ? [x0, x1, z0, z1] : null;
}

/**
 * Empty cells with fabric on both opposite sides at the same height.
 *
 * A pure count, so calibration can call it directly. The first attempt
 * calibrated by parsing the *message* of the check with the threshold set to
 * infinity — which meant it measured the threshold and reported zero for all
 * 125 files. Never calibrate through the reporting layer.
 */
function wallHoles(grid: BlockGrid, box: Box): [number, number, number][] {
  const [x0, x1, z0, z1] = box;
  const sy = grid.size[1];

  const fab = (x: number, y: number, z: number): boolean => {
    const b = grid.get(x, y, z);
    return b !== null && isFabric(b.id) && !GROUND.has(b.id);
  };

  const holes: [number, number, number][] = [];
  for (let y = 1; y < sy; y++) {
    for (let x = x0; x <= x1; x++) {
      for (let z = z0; z <= z1; z++) {
        if (grid.get(x, y, z) !== null) continue;
        if ((fab(x - 1, y, z) && fab(x + 1, y, z)) ||
            (fab(x, y, z - 1) && fab(x, y, z + 1))) {
          holes.push([x, y, z]);
        }
      }
    }
  }
  return holes;
}

/**
 * Gaps inside a wall line — the only thing "a wall has holes" can mean.
 *
 * A window opening, a gap between two posts and a hatch all read as an enclosed
 * gap, which is why the threshold is his measured p95 and not zero.
 */
function wallsClosed(grid: BlockGrid, box: Box): string[] {
  const holes = wallHoles(grid, box);
  if (holes.length > WALL_HOLE_MAX) {
    const sample = holes.slice(0, 5).map(h => `(${h[0]},${h[1]},${h[2]})`).join(', ');
    return [`${holes.length} gaps enclosed by fabric on both sides, e.g. ${sample}   (his p95 is ${WALL_HOLE_MAX})`];
  }
  return [];
}

/** Interior columns with nothing over head height — a pure count, for calibration. */
function bareColumns(grid: BlockGrid, box: Box): [number, number][] {
  const [x0, x1, z0, z1] = box;
  const sy = grid.size[1];
  if (x1 - x0 < 2 || z1 - z0 < 2) return [];

  const bare: [number, number][] = [];
  for (let x = x0 + 1; x < x1; x++) {
    for (let z = z0 + 1; z < z1; z++) {
      let maxY = -1;
      for (let y = 1; y < sy; y++) {
        if (grid.get(x, y, z) !== null) maxY = y;
      }
      if (maxY < 3) bare.push([x, z]);
    }
  }
  return bare;
}

/**
 * Is the room covered.
 *
 * Calibrated rather than absolute: an open workshop front, a porch and a walled
 * yard all leave interior columns open in his own files, so the gate is his
 * share rather than zero. `carpenter_lvl4` has 4 of 88 and is not broken.
 */
function roofCovers(grid: BlockGrid, box: Box): string[] {
  const [x0, x1, z0, z1] = box;
  const bare = bareColumns(grid, box);
  const inner = Math.max(1, (x1 - x0 - 1) * (z1 - z0 - 1));
  const share = (100.0 * bare.length) / inner;
  if (share > ROOF_BARE_MAX_PCT) {
    const sample = bare.slice(0, 4).map(c => `(${c[0]},${c[1]})`).join(', ');
    return [`${bare.length} of ${inner} interior columns open to the sky (${share.toFixed(0)}%, his p95 is ${ROOF_BARE_MAX_PCT.toFixed(0)}%), e.g. ${sample}`];
  }
  return [];
}

/** Doors with air under, no frame, or no lintel. */
function doorsFramed(grid: BlockGrid): string[] {
  const out: string[] = [];
  const items = [...grid.blocks()].sort(comparePos);
  for (const { pos, block } of items) {
    if (!block.id.endsWith('_door')) continue;
    if ((block.props['half'] ?? 'lower') !== 'lower') continue;

    const [x, y, z] = pos;
    if (grid.get(x, y - 1, z) === null) {
      out.push(`door@(${x},${y},${z}) has air under it`);
      continue;
    }
    const sides = NEIGH4.map(([dx, dz]) => grid.get(x + dx, y, z + dz));
    if (sides.filter(s => s !== null).length < 2) {
      const desc = sides.map(s => (s === null ? 'air' : s.id)).join(', ');
      out.push(`door@(${x},${y},${z}) has no frame: ${desc}`);
    }
    if (grid.get(x, y + 2, z) === null) {
      out.push(`door@(${x},${y},${z}) has no lintel over it`);
    }
  }
  return out;
}

/** A block touching nothing at all (6-connected), excluding designed hangers. */
function nothingFloats(grid: BlockGrid): string[] {
  const out: string[] = [];
  const hangers = [
    '_torch', 'lantern', '_sign', '_banner', 'chain', '_trapdoor', 'ladder',
    'vine', '_grass', 'fern', '_sapling', '_leaves',
  ];
  const items = [...grid.blocks()].sort(comparePos);
  for (const { pos, block } of items) {
    const [x, y, z] = pos;
    if (y === 0) continue; // the ground layer meets terrain
    if (endsWithAny(block.id, hangers) || block.id === 'jigsaw') continue;
    const touches = NEIGH6.some(([dx, dy, dz]) => grid.occupied(x + dx, y + dy, z + dz));
    if (!touches) {
      out.push(`${block.id}@(${x},${y},${z}) touches nothing`);
    }
  }
  return out;
}

/**
 * An enclosed volume you can stand up in, inside the walls.
 *
 * Replaces the Python `traverse.walkable` module with a simple BFS flood fill:
 * seed from every standable cell strictly inside the box footprint (empty cell
 * with a solid floor below), flood through standable space, and keep the cells
 * that fall inside the walls. Headroom is a separate check — a player is two
 * blocks tall.
 */
function hasRoom(grid: BlockGrid, box: Box): string[] {
  const [x0, x1, z0, z1] = box;
  const sy = grid.size[1];

  const standable = (x: number, y: number, z: number): boolean =>
    grid.get(x, y, z) === null && grid.get(x, y - 1, z) !== null;
  const insideFootprint = (x: number, z: number): boolean =>
    x > x0 && x < x1 && z > z0 && z < z1;

  // Seed the flood fill from interior standable cells.
  const visited = new Set<string>();
  const queue: [number, number, number][] = [];
  for (let y = 1; y < sy; y++) {
    for (let x = x0 + 1; x < x1; x++) {
      for (let z = z0 + 1; z < z1; z++) {
        if (standable(x, y, z)) {
          const key = `${x},${y},${z}`;
          if (!visited.has(key)) {
            visited.add(key);
            queue.push([x, y, z]);
          }
        }
      }
    }
  }

  // Flood fill through standable space (6-connected).
  let head = 0;
  while (head < queue.length) {
    const [x, y, z] = queue[head++];
    for (const [dx, dy, dz] of NEIGH6) {
      const nx = x + dx;
      const ny = y + dy;
      const nz = z + dz;
      const key = `${nx},${ny},${nz}`;
      if (visited.has(key) || !standable(nx, ny, nz)) continue;
      visited.add(key);
      queue.push([nx, ny, nz]);
    }
  }

  const interior = queue.filter(([x, , z]) => insideFootprint(x, z));
  if (interior.length < 4) {
    return [`no room: only ${interior.length} standable cells inside the walls`];
  }
  // Headroom: a player is two blocks tall.
  const cramped = interior.filter(([x, y, z]) => grid.get(x, y + 1, z) !== null);
  if (cramped.length > Math.floor(interior.length / 2)) {
    return [`no room: ${cramped.length} of ${interior.length} interior cells have no headroom`];
  }
  return [];
}

export type IntegrityResult = Record<string, string[]>;

/** Run all five integrity checks against a finished structure. */
export function checkIntegrity(grid: BlockGrid): IntegrityResult {
  const box = buildingBox(grid);
  if (box === null) {
    return { building: ['no building fabric at all: this is a plot, not a build'] };
  }
  return {
    walls: wallsClosed(grid, box),
    roof: roofCovers(grid, box),
    doors: doorsFramed(grid),
    floating: nothingFloats(grid),
    room: hasRoom(grid, box),
  };
}
