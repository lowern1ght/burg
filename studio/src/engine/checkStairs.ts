/**
 * Which way does a stair face? — ported from tools/check_stairs.py.
 *
 * `facing` names the **tall** half of a stair, not the direction it descends.
 * Measured, not remembered: on `plains/houses/house_lvl6` the ridge stands at
 * x=4, the west slope carries `facing=east` and the east slope `facing=west` —
 * both pointing at the ridge. Every comment in the old `wall.py` had said the
 * opposite, so every stair the generator placed was mirrored.
 *
 * The rule this checker enforces: on a roof slope the tall half points uphill.
 * A stair whose tall half points away from the rise leaves a step in the pitch
 * and reads as a notch cut out of the roof.
 *
 * Calibration is not optional in spirit — three of five tests in `check_fabric`
 * were wrong the first time and the author's own files are what proved it. A
 * metric that is noisy on his work is a broken metric, not a finding.
 * CORPUS_RESIDUAL is what the metric still reports on his 125 NBTs: one cell in
 * `wheat_farm_lvl3` (5, 4, 11), where three stairs meet at a hip-roof corner so
 * "which way is uphill" has no single answer at the cell where they meet.
 * Anything above this is a real finding.
 */

import { type BlockGrid } from './appearance';

const STEP: Record<string, [number, number]> = {
  north: [0, -1],
  south: [0, 1],
  west: [-1, 0],
  east: [1, 0],
};

// What counts as evidence that the build continues upward on one side. It has to
// be structure, not furniture — and this is the whole calibration. The first
// version accepted any block and reported 22 hits on the author's own files;
// every one was a stair used as a CHAIR, flagged because the thing "uphill" of
// it was the pressure plate, flower pot or cobblestone-wall table it was drawn
// up to. A chair has no pitch to get backwards.
const NOT_STRUCTURE = [
  '_pressure_plate', '_pot', '_wall', '_fence', '_fence_gate', '_pane',
  '_torch', '_sign', '_button', '_carpet', '_rail', '_door', '_trapdoor',
  '_bed', '_candle', '_head', '_banner', '_sapling', '_bush', '_grass',
  '_flower', '_mushroom', '_crop', '_stem', 'lantern', 'ladder', 'vine',
  'chain', 'campfire', 'cauldron', 'lever', 'tripwire', 'snow', 'water',
];

// One cell in 125 files (see module docstring). Anything above this is real.
export const CORPUS_RESIDUAL = 1;

function endsWithAny(s: string, suffixes: readonly string[]): boolean {
  return suffixes.some(suffix => s.endsWith(suffix));
}

function inBounds(grid: BlockGrid, x: number, y: number, z: number): boolean {
  const [sx, sy, sz] = grid.size;
  return x >= 0 && x < sx && y >= 0 && y < sy && z >= 0 && z < sz;
}

/** A block that can carry a roof plane or a flight of steps. */
function structural(grid: BlockGrid, x: number, y: number, z: number): boolean {
  if (!inBounds(grid, x, y, z)) return false;
  const b = grid.get(x, y, z);
  return b !== null && !endsWithAny(b.id, NOT_STRUCTURE);
}

/** Anything at all — used only to ask whether a stair is buried. */
function solid(grid: BlockGrid, x: number, y: number, z: number): boolean {
  if (!inBounds(grid, x, y, z)) return false;
  const b = grid.get(x, y, z);
  if (b === null) return false;
  return !endsWithAny(b.id, ['_torch', 'ladder', '_sign', 'lantern', 'vine', '_pane']);
}

export type StairsResult = {
  downhill: string[];
  count: number;
  residual: number;
};

/**
 * Stairs whose tall half points downhill. Roof slopes only.
 *
 * Algorithm:
 *  1. For each `_stairs` block with `half=bottom`:
 *  2. Skip if buried (the block above is solid) — not a visible pitch.
 *  3. Look in the facing direction for structural blocks above-forward and
 *     above-behind.
 *  4. For above-behind, require the course to be at least two cells wide across
 *     the run — a lone block above a flat cornice must not read as a rise.
 *  5. If above-behind has structure AND above-forward does NOT, the stair faces
 *     downhill.
 */
export function checkStairs(grid: BlockGrid): StairsResult {
  const downhill: string[] = [];

  for (const { pos, block } of grid.blocks()) {
    if (!block.id.endsWith('_stairs')) continue;
    if ((block.props['half'] ?? 'bottom') !== 'bottom') {
      continue; // inverted stairs corbel, they do not pitch
    }
    const facing = block.props['facing'];
    if (facing === undefined || !(facing in STEP)) continue;

    const [x, y, z] = pos;
    if (solid(grid, x, y + 1, z)) continue; // buried: not a visible pitch

    const [dx, dz] = STEP[facing];
    const upForward = structural(grid, x + dx, y + 1, z + dz);
    let upBehind = structural(grid, x - dx, y + 1, z - dz);

    // A slope is a PLANE, not a point: the course above-behind has to be at
    // least two cells wide across the run. Without this, one stray block
    // sitting diagonally above a flat cornice course reads as a rise and the
    // cornice's stairs are all reported backwards.
    if (upBehind) {
      const bx = x - dx;
      const bz = z - dz;
      upBehind =
        structural(grid, bx + dz, y + 1, bz + dx) ||
        structural(grid, bx - dz, y + 1, bz - dx);
    }

    if (upBehind && !upForward) {
      downhill.push(`${block.id}@(${x},${y},${z}) faces ${facing} (tall half points downhill)`);
    }
  }

  return { downhill, count: downhill.length, residual: CORPUS_RESIDUAL };
}
