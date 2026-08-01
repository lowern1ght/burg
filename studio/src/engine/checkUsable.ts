/**
 * Are the buildings enterable, and can you get upstairs? — ported from
 * tools/check_usable.py.
 *
 * Distinguishes indoor cells from roof surfaces: a cell counts as indoor only if
 * something solid roofs its column. Without that, every roof slope reads as an
 * unreachable upper floor.
 *
 * **Storeys are found, not assumed.** The first version called anything at
 * y >= 5 an upper floor and anything at y <= 2 the ground. Both are wrong per
 * building: `barracks_lvl3` puts its upper storey at stand level 4, so its one
 * real upper floor was scored as ground and the eleven attic cells above it — a
 * void with no floor and no way in — became the "upper floor" that measured
 * 1/12. So every stand elevation with real floor area is listed with its own
 * reachability, lowest first, and nothing decides on your behalf which of them
 * counts as a storey.
 *
 * The `--ladder` mode checks that equipment placed at one rung of an upgrade
 * ladder survives to the next: the author does not remove things, so a function
 * count that drops between levels (with nothing taking its place) is a finding.
 */

import { type BlockGrid } from './appearance';
import { walkable, reachable, parseKey } from './traverse';

// A storey needs room to stand and a floor with area. Fewer cells than this at
// one elevation is furniture, a landing or the void under a pitch — not a floor
// anybody is meant to walk about on.
export const FLOOR_MIN_CELLS = 8;

// Equipment whose count must never fall as a building is upgraded. The author
// does not remove things: over six of his ladders nothing that appears at one
// rung is missing from a higher one. Mechanically it matters too —
// `UpgradeAction` spawns the per-level delta, so a bench that moves is a bench
// built twice.
export const LADDER_KEEP = [
  'anvil', 'stonecutter', 'cauldron', 'furnace', 'smoker', 'crafting_table',
  'barrel', 'chest', 'bed', 'lectern', 'composter', 'loom', 'smithing_table',
  'grindstone', 'blast_furnace', 'beehive', 'bee_nest',
] as const;

// What the calibrated metric still reports on the author's own ladders, and why
// it is allowed to. Two of his fourteen ladders tidy up at the top rung:
//   carpenter  l6 -> l7: loses a chest and a composter, gains a lectern.
//   pig_farm   l3 -> l4: the furnace goes and the second smoker does not arrive
//              until l6.
// Three flagged counts across those two families. Anything above this on OUR
// output is a real finding.
export const CORPUS_LADDER_RESIDUAL = 3;

// ── indoor detection ───────────────────────────────────────────────────────

/**
 * Cells whose column is roofed by something solid.
 *
 * The +2 offset accounts for the player's two-block height: a block at y+2 or
 * above in the same column means the player standing at `y` is under a roof.
 */
export function indoor(grid: BlockGrid, cells: Map<string, number>): Set<string> {
  const sy = grid.size[1];
  const out = new Set<string>();
  for (const [key] of cells) {
    const [x, y, z] = parseKey(key);
    for (let yy = y + 2; yy < sy; yy++) {
      if (grid.occupied(x, yy, z)) {
        out.add(key);
        break;
      }
    }
  }
  return out;
}

// The void inside a pitched roof passes the `indoor` test — something solid is
// above it — but it has no floor and is not a storey. It is dropped by area
// (`FLOOR_MIN_CELLS`) rather than by geometry, which is why an attic shows up
// only when the caller lowers the threshold.

// ── storey detection ───────────────────────────────────────────────────────

/**
 * Every stand elevation with enough floor area to be a storey, lowest first.
 *
 * Deliberately NOT clustered. Merging elevations within two of each other read
 * a barracks as one enormous ground floor — its stand levels run 1,2,3,4,5 with
 * real area at each, because a stretched donor has a mezzanine and a stair
 * landing as well as two storeys, and every gap is 1. Any rule that decides
 * which of those is "a floor" is a guess. Listing them is not.
 *
 * Returns arrays of cell keys, one per storey.
 */
export function storeys(indoorKeys: Set<string>, minCells: number = FLOOR_MIN_CELLS): string[][] {
  const byY = new Map<number, string[]>();
  for (const key of indoorKeys) {
    const y = parseKey(key)[1];
    const list = byY.get(y);
    if (list !== undefined) {
      list.push(key);
    } else {
      byY.set(y, [key]);
    }
  }
  const result: string[][] = [];
  for (const y of [...byY.keys()].sort((a, b) => a - b)) {
    const list = byY.get(y)!;
    if (list.length >= minCells) result.push(list);
  }
  return result;
}

// ── per-building usability report ──────────────────────────────────────────

export type StoreyStatus = 'reached' | 'partial' | 'no-way-up' | 'enter-fail';

export type StoreyReport = {
  /** Lowest y of any cell in this storey. */
  y: number;
  /** How many cells are reachable from the entry points. */
  reachable: number;
  /** Total standable cells in this storey. */
  total: number;
  /** Reachability verdict. */
  status: StoreyStatus;
};

export type UsableReport = {
  /** Each storey's reachability, ground floor first. */
  storeys: StoreyReport[];
  /** Floors that cannot be reached (enter-fail + no-way-up). */
  unreachableFloors: number;
  /** Can the building be entered at all (ground floor reachable from outside)? */
  enterable: boolean;
  /** Is there a continuous walk path from ground to the top storey? */
  climbable: boolean;
  /** Human-readable summary matching the Python output. */
  summary: string;
};

/**
 * Check storey reachability, enterability, and climbability.
 *
 * The building is "entered" by flooding from boundary cells (edge of the
 * bounding box, not indoor) through the walk graph. Each indoor storey is then
 * scored: how many of its cells the flood reached.
 *
 * `minCells` controls the minimum floor area for a storey (default 8). Set to 1
 * to also count attic voids and landings — the Python `--attic` mode.
 */
export function checkUsable(grid: BlockGrid, minCells: number = FLOOR_MIN_CELLS): UsableReport {
  const [sx, , sz] = grid.size;
  const cells = walkable(grid);
  const ind = indoor(grid, cells);

  // Outside cells: on the boundary edge of the bounding box, not indoor. These
  // are the entry points — a player approaching from the surrounding terrain.
  const outside: Array<[number, number, number]> = [];
  for (const [key] of cells) {
    if (ind.has(key)) continue;
    const [x, y, z] = parseKey(key);
    if (x === 0 || x === sx - 1 || z === 0 || z === sz - 1) {
      outside.push([x, y, z]);
    }
  }

  const seen = reachable(grid, outside);
  const floors = storeys(ind, minCells);

  const storeyReports: StoreyReport[] = [];
  let unreachable = 0;

  for (let i = 0; i < floors.length; i++) {
    const floor = floors[i];
    let got = 0;
    let minY = Infinity;
    for (const key of floor) {
      const [, y] = parseKey(key);
      if (y < minY) minY = y;
      if (seen.has(key)) got++;
    }
    let status: StoreyStatus;
    if (got === 0) {
      status = i === 0 ? 'enter-fail' : 'no-way-up';
      unreachable++;
    } else if (got < Math.floor((floor.length * 3) / 4)) {
      status = 'partial';
    } else {
      status = 'reached';
    }
    storeyReports.push({ y: minY, reachable: got, total: floor.length, status });
  }

  const enterable = storeyReports.length === 0 || storeyReports[0].status !== 'enter-fail';
  const climbable = storeyReports.every(s => s.status !== 'no-way-up');

  const parts = storeyReports.map(s => {
    const tag =
      s.status === 'no-way-up' ? '  NO-WAY-UP' :
      s.status === 'enter-fail' ? '  ENTER-FAIL' :
      s.status === 'partial' ? '  part' : '';
    return `y${s.y} ${s.reachable}/${s.total}${tag}`;
  });

  const summary = parts.length > 0
    ? `${parts.join('  |  ')}   |   ${unreachable} unreachable floor(s)`
    : 'no floor found';

  return {
    storeys: storeyReports,
    unreachableFloors: unreachable,
    enterable,
    climbable,
    summary,
  };
}

// ── ladder monotonicity (equipment survival across upgrade rungs) ──────────

/**
 * The thing a block IS, with its colour thrown away.
 *
 * Counting exact ids reported `barracks` losing ten `white_bed` between rungs 3
 * and 4 — but it gained ten `orange_bed` in the same step. That is the author
 * re-dyeing the bedding, which he does, and it is not equipment going missing.
 * A metric that cannot tell a repaint from a removal will cry wolf on his own
 * buildings, so the colour comes off before counting.
 */
export function functionOf(id: string): string | null {
  for (const k of LADDER_KEEP) {
    if (id === k || id.endsWith('_' + k)) return k;
  }
  return null;
}

/** Per-function equipment counts for a single structure (one rung). */
export type EquipmentCounts = Map<string, number>;

/**
 * Count equipment by function across all blocks in the grid.
 * Colour/variant prefixes are stripped via `functionOf`.
 */
export function countEquipment(grid: BlockGrid): EquipmentCounts {
  const counts = new Map<string, number>();
  for (const { block } of grid.blocks()) {
    const fn = functionOf(block.id);
    if (fn !== null) {
      counts.set(fn, (counts.get(fn) ?? 0) + 1);
    }
  }
  return counts;
}

export type LadderFamilyReport = {
  name: string;
  /** Number of rungs in this family. */
  rungCount: number;
  /** Per-rung equipment counts, ordered by level (lowest first). */
  histogram: EquipmentCounts[];
  /** Functions that vanish (non-excused drop) at some step. These are findings. */
  vanishes: string[];
  /** Functions replaced by a swap (excused drop — something else rose). */
  replaced: string[];
};

export type LadderReport = {
  families: LadderFamilyReport[];
  /** Total functions that vanish across all families. */
  totalVanishes: number;
  /** Measured residual on the author's own ladders — anything above is real. */
  corpusResidual: number;
};

/**
 * Check monotonicity: a drop in a function count at step i is excused when the
 * total gained >= total lost in that same step (a workstation swap). Anything
 * else that falls is a finding.
 */
function checkMonotonicity(histogram: EquipmentCounts[]): { vanishes: string[]; replaced: string[] } {
  const kindSet = new Set<string>();
  for (const counts of histogram) {
    for (const [k] of counts) kindSet.add(k);
  }
  const kinds = [...kindSet].sort();

  // A drop at (step i, kind k) is excused if the total gained >= total lost in
  // step i — that is a workstation being replaced by a better one, which the
  // author does. What is never allowed is a kind falling with nothing taking
  // its place.
  const excused = new Set<string>();
  for (let i = 0; i < histogram.length - 1; i++) {
    let gained = 0;
    let lost = 0;
    const declines: string[] = [];
    for (const k of kinds) {
      const a = histogram[i].get(k) ?? 0;
      const b = histogram[i + 1].get(k) ?? 0;
      const delta = b - a;
      if (delta > 0) gained += delta;
      if (delta < 0) {
        lost += -delta;
        declines.push(k);
      }
    }
    if (gained >= lost && lost > 0) {
      for (const k of declines) excused.add(`${i}:${k}`);
    }
  }

  const vanishes: string[] = [];
  const replaced: string[] = [];
  for (const k of kinds) {
    let hasDrop = false;
    let hasDecline = false;
    for (let i = 0; i < histogram.length - 1; i++) {
      const a = histogram[i].get(k) ?? 0;
      const b = histogram[i + 1].get(k) ?? 0;
      if (b < a) {
        hasDecline = true;
        if (!excused.has(`${i}:${k}`)) hasDrop = true;
      }
    }
    if (hasDrop) vanishes.push(k);
    else if (hasDecline) replaced.push(k);
  }
  return { vanishes, replaced };
}

/**
 * Check that equipment counts never fall (beyond an excused swap) across each
 * family's upgrade ladder.
 *
 * Each family is an ordered list of BlockGrids — one per rung, lowest level
 * first. The function counts equipment by function (stripping colour), builds a
 * per-rung histogram, and flags any function whose count drops without
 * something else rising to replace it.
 */
export function checkLadder(families: Array<{ name: string; grids: BlockGrid[] }>): LadderReport {
  const familyReports: LadderFamilyReport[] = [];
  let totalVanishes = 0;

  for (const { name, grids } of families) {
    if (grids.length === 0) continue;
    const histogram = grids.map(g => countEquipment(g));
    const { vanishes, replaced } = checkMonotonicity(histogram);
    totalVanishes += vanishes.length;
    familyReports.push({ name, rungCount: grids.length, histogram, vanishes, replaced });
  }

  return { families: familyReports, totalVanishes, corpusResidual: CORPUS_LADDER_RESIDUAL };
}
