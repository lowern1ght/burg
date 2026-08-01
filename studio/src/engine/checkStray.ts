/**
 * Find blocks that stick out: a solid cell with almost nothing touching it —
 * ported from tools/check_stray.py.
 *
 * This exists because the same complaint came back three times —
 * "какие-то торчащие блоки лишние" — and no gate in the repo could see it.
 * The write-time guard checks what a block *stands on*; nothing checked whether
 * a block is *attached to anything at all*. A brace log one cell away from its
 * post, a lip of slab on top of a kerb, a lone stone at the toe of a bank: each
 * is legal, supported, walkable, and reads as litter.
 *
 * Two measures, both calibrated on the author's own 125 files before believed:
 *
 *   strays(grid) — a solid cell with at most one orthogonal neighbour
 *                  (6-connected), above the ground layer. His own median is what
 *                  a free-standing fence post or a lamp gives, so ids *meant*
 *                  to stand alone (posts, lights, plants, markers) are exempt.
 *   spikes(grid) — a cell whose four horizontal neighbours are all empty at its
 *                  own height, i.e. it pokes above the skyline of its own row.
 *                  A chimney is one of these and so is a mistake, which is why
 *                  the number is a band and not a rule.
 *
 * `y == 0` is exempt in both: every stray the check found in the author's own
 * street pieces sits at y=0 (`dirt_path@(2,0,0)`, `grass_block@(5,0,7)`) because
 * the terrain those cells touch is not in the file.
 */

import { type BlockGrid, type BlockInfo } from './appearance';

/** Things whose whole job is to stand by themselves. */
const ALONE_IS_FINE = new Set([
  'jigsaw', 'torch', 'wall_torch', 'lantern', 'soul_lantern', 'chain',
  'oak_sapling', 'short_grass', 'grass', 'tall_grass', 'fern', 'poppy',
  'dandelion', 'oxeye_daisy', 'cornflower', 'azure_bluet', 'flower_pot',
  'lily_pad', 'sugar_cane', 'bamboo', 'sweet_berry_bush', 'beehive',
  'campfire', 'bell', 'item_frame', 'scaffolding', 'ladder',
]);

/** Suffixes exempt from stray checking. */
const ALONE_SUFFIX = [
  '_fence', '_fence_gate', '_sign', '_banner', '_wall_sign', '_wall_banner',
  '_carpet', '_bed', '_button', '_pressure_plate', '_trapdoor', '_door',
  '_sapling', '_leaves', '_bush', '_mushroom',
];

/** 6-connected neighbourhood offsets (±x, ±y, ±z). */
const NEIGH6: ReadonlyArray<[number, number, number]> = [
  [1, 0, 0], [-1, 0, 0], [0, 1, 0], [0, -1, 0], [0, 0, 1], [0, 0, -1],
];

/** 4-connected horizontal neighbourhood offsets (±x, ±z). */
const NEIGH4: ReadonlyArray<[number, number]> = [[1, 0], [-1, 0], [0, 1], [0, -1]];

type Pos = [number, number, number];

function standsAloneOk(b: BlockInfo): boolean {
  return ALONE_IS_FINE.has(b.id) || ALONE_SUFFIX.some(s => b.id.endsWith(s));
}

function comparePos(a: Pos, b: Pos): number {
  return a[0] - b[0] || a[1] - b[1] || a[2] - b[2];
}

function sortedBlocks(grid: BlockGrid): Array<{ pos: Pos; block: BlockInfo }> {
  return [...grid.blocks()].sort((a, b) => comparePos(a.pos, b.pos));
}

/**
 * Solid cells with at most one orthogonal neighbour, above the ground layer.
 * Exempts ids meant to stand alone and the y=0 ground row (terrain not in file).
 */
export function strays(grid: BlockGrid): string[] {
  const out: string[] = [];
  const solid = new Set<string>();
  for (const { pos } of grid.blocks()) solid.add(pos.join(','));
  for (const { pos: p, block: b } of sortedBlocks(grid)) {
    const [x, y, z] = p;
    if (standsAloneOk(b) || y === 0) continue;
    let n = 0;
    for (const [dx, dy, dz] of NEIGH6) {
      if (solid.has(`${x + dx},${y + dy},${z + dz}`)) n++;
    }
    if (n <= 1) {
      out.push(`${b.id}@(${p.join(', ')}) touches ${n} block(s)`);
    }
  }
  return out;
}

/**
 * Cells that poke above their own row: nothing beside them at their height.
 * A cell with a block directly above is part of a column, not a spike.
 */
export function spikes(grid: BlockGrid): string[] {
  const out: string[] = [];
  const solid = new Set<string>();
  for (const { pos } of grid.blocks()) solid.add(pos.join(','));
  for (const { pos: p, block: b } of sortedBlocks(grid)) {
    const [x, y, z] = p;
    if (standsAloneOk(b) || y === 0) continue;
    let beside = false;
    for (const [dx, dz] of NEIGH4) {
      if (solid.has(`${x + dx},${y},${z + dz}`)) {
        beside = true;
        break;
      }
    }
    if (beside) continue;
    if (solid.has(`${x},${y + 1},${z}`)) continue;
    out.push(`${b.id}@(${p.join(', ')}) pokes up alone`);
  }
  return out;
}

export type StrayReport = {
  strays: string[];
  spikes: string[];
};

/** Run both stray checks and return a structured result. */
export function checkStray(grid: BlockGrid): StrayReport {
  return {
    strays: strays(grid),
    spikes: spikes(grid),
  };
}
