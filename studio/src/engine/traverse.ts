/**
 * Can a player actually walk through this structure? — ported from
 * tools/structures/traverse.py.
 *
 * Every judgement in the style checkers is about how a build *looks*. None of
 * them ask whether the thing works, and that was the gap that mattered: the
 * first wall set rendered well and was impassable. A solid corner bastion, a
 * gate blocked by its own flanking piers and a tower with no way up all passed
 * the style gate without complaint.
 *
 * So this module models a walking player and answers three questions:
 *
 *   walkable(grid)      — which cells can be stood in
 *   reachable(...)      — what can be reached from a starting cell
 *   checkRoute(...)     — is there a route from A to B, and if not, why not
 *
 * The movement rules follow Minecraft, and one of them is the whole point:
 *
 *  - A player occupies TWO cells of height, so a surface with only one clear
 *    cell above it is not walkable however solid it looks.
 *  - Stepping up half a block — onto a bottom slab or a stair — needs no jump.
 *  - Stepping up a FULL block does need a jump. The requirement is a route with
 *    no jumping, so a full-block rise is treated as a wall, not as a step.
 *  - Fences, walls and fence gates are 1.5 blocks tall: they block movement and
 *    cannot be stood on. That is what makes them railings.
 *  - Ladders are climbable in both directions, which is how a tower gets a top.
 */

import { type BlockGrid, type BlockInfo } from './appearance';

// ── block classification ───────────────────────────────────────────────────

// Blocks a player walks straight through.
const PASSABLE = new Set([
  'air', 'cave_air', 'void_air', 'short_grass', 'tall_grass', 'grass',
  'fern', 'large_fern', 'dead_bush', 'torch', 'wall_torch', 'soul_torch',
  'redstone_torch', 'lantern', 'chain', 'rail', 'tripwire', 'vine',
  'sugar_cane', 'wheat', 'carrots', 'potatoes', 'beetroots', 'melon_stem',
  'pumpkin_stem', 'lily_pad', 'snow', 'light', 'sunflower', 'poppy',
  'dandelion', 'cornflower', 'azure_bluet', 'oxeye_daisy', 'allium',
  'blue_orchid', 'white_tulip', 'red_tulip', 'pink_tulip', 'orange_tulip',
  'lilac', 'rose_bush', 'peony', 'sweet_berry_bush', 'water',
]);

// Climbable in both directions.
const CLIMBABLE = new Set([
  'ladder', 'vine', 'scaffolding', 'twisting_vines', 'weeping_vines',
]);

// 1.5 blocks tall: an obstacle you cannot stand on. This is the point of a rail.
const RAILING_SUFFIX = ['_wall', '_fence', '_fence_gate'];

export const BLOCK_HEIGHT = 1.0;
export const SLAB_HEIGHT = 0.5;

// ── key helpers ────────────────────────────────────────────────────────────

/** Encode (x, y, z) as a string map key: "x,y,z". */
export function coordKey(x: number, y: number, z: number): string {
  return `${x},${y},${z}`;
}

/** Decode a "x,y,z" key back into a coordinate tuple. */
export function parseKey(key: string): [number, number, number] {
  const parts = key.split(',');
  return [Number(parts[0]), Number(parts[1]), Number(parts[2])];
}

function endsWithAny(s: string, suffixes: readonly string[]): boolean {
  return suffixes.some(suffix => s.endsWith(suffix));
}

// ── passability ────────────────────────────────────────────────────────────

/**
 * True if a player's body can occupy the same cell as `b`.
 *
 * `null` (no block) is passable — that is air.
 */
export function isPassable(b: BlockInfo | null): boolean {
  if (b === null) return true;
  const n = b.id;
  if (PASSABLE.has(n) || CLIMBABLE.has(n)) return true;
  if (n.endsWith('_leaves')) return false; // leaves are solid to walk into
  if (n.endsWith('_door')) {
    // A wooden door counts as passable even when closed: a player — and a
    // villager — can open it, so it is a doorway, not a wall. Iron needs a
    // signal, so it stays shut.
    return !n.startsWith('iron_');
  }
  if (n.endsWith('_fence_gate')) return true;
  if (n.endsWith('_trapdoor') && b.props['open'] === 'true') {
    // An open trapdoor stands vertical: passable to walk through, though it is
    // really a wall on one side. Close enough for routing.
    return true;
  }
  if (n === 'jigsaw') return true; // replaced by final_state on placement
  return false;
}

// ── surface elevation ──────────────────────────────────────────────────────

/**
 * Elevation of the walkable top of the block at (x, y, z), or null if you
 * cannot stand on it.
 */
export function surface(grid: BlockGrid, x: number, y: number, z: number): number | null {
  const b = grid.get(x, y, z);
  if (b === null) return null;
  const n = b.id;
  if (n === 'jigsaw') {
    // A connector is not what stands there in the world: placement replaces it
    // with its `final_state`, which is `dirt_path` everywhere in this repo.
    // The TS BlockGrid does not expose block NBT, so we cannot read the
    // final_state directly — but every connector in this corpus resolves to a
    // solid block, so treating it as standable matches the game.
    return y + BLOCK_HEIGHT;
  }
  if (PASSABLE.has(n) || CLIMBABLE.has(n)) return null;
  if (endsWithAny(n, RAILING_SUFFIX)) return null; // too tall to step onto
  if (n.endsWith('_slab')) {
    if (b.props['type'] === 'bottom') return y + SLAB_HEIGHT;
    return y + BLOCK_HEIGHT; // top and double are full height
  }
  if (n.endsWith('_stairs')) {
    // Standing on a stair puts you on its upper half.
    return y + BLOCK_HEIGHT;
  }
  if (n.endsWith('_trapdoor') || n.endsWith('_door')) return null;
  if (n.endsWith('_leaves')) return y + BLOCK_HEIGHT;
  if (n.endsWith('_carpet') || n === 'snow') return null;
  return y + BLOCK_HEIGHT;
}

// ── standable ──────────────────────────────────────────────────────────────

/**
 * If a player can occupy cell (x, y, z), the height their feet are at — else
 * null.
 *
 * Two cells of clearance, because a player is two blocks tall. This is the
 * check a one-block-headroom walkway fails.
 */
export function standable(grid: BlockGrid, x: number, y: number, z: number): number | null {
  if (!isPassable(grid.get(x, y, z))) return null;
  if (!isPassable(grid.get(x, y + 1, z))) return null;
  // Hanging on a ladder needs no floor. Requiring one made every ladder cell a
  // non-node, so a tower with a perfectly good ladder in it measured as having
  // no way up — the ladder was there and the graph could not see it.
  const here = grid.get(x, y, z);
  if (here !== null && CLIMBABLE.has(here.id)) return y;
  const s = surface(grid, x, y - 1, z);
  if (s === null) return null;
  return s;
}

/**
 * Every cell a player can stand in, mapped to its surface height.
 *
 * The key is "x,y,z" (use `parseKey` to recover the coordinates).
 */
export function walkable(grid: BlockGrid): Map<string, number> {
  const [sx, sy, sz] = grid.size;
  const out = new Map<string, number>();
  for (let x = 0; x < sx; x++) {
    for (let z = 0; z < sz; z++) {
      for (let y = 1; y < sy; y++) {
        const s = standable(grid, x, y, z);
        if (s !== null) {
          out.set(coordKey(x, y, z), s);
        }
      }
    }
  }
  return out;
}

// ── the walk graph ─────────────────────────────────────────────────────────

/**
 * A ladder lets you move straight up or down from (x, y, z).
 */
function climbLinks(grid: BlockGrid, x: number, y: number, z: number): Array<[number, number, number]> {
  const here = grid.get(x, y, z);
  if (here !== null && CLIMBABLE.has(here.id)) {
    return [[x, y + 1, z], [x, y - 1, z]];
  }
  return [];
}

/**
 * How far you may climb to stand in the target cell, in blocks.
 *
 * Half a block normally. A stair allows a full block, because you step onto its
 * lower half first and then walk up its upper half — which is the whole reason
 * a staircase of stairs is walkable and a staircase of full blocks is not.
 */
function allowedRise(grid: BlockGrid, tx: number, ty: number, tz: number, base: number): number {
  const sup = grid.get(tx, ty - 1, tz);
  if (sup !== null && sup.id.endsWith('_stairs')) return BLOCK_HEIGHT;
  return base;
}

/**
 * Cells reachable from (x, y, z) in one step, without jumping.
 *
 * `cells` is the walkable map (from `walkable()`). The four horizontal
 * directions are each explored at dy = 0, 1, -1, -2, -3 — the first viable
 * target in that column wins (flat first, then up, then down). A rise above
 * `maxRise` (default half a block, or a full block onto a stair) is a wall.
 * A drop beyond 3 blocks is too far to walk off safely.
 */
export function neighbours(
  grid: BlockGrid,
  cells: Map<string, number>,
  x: number,
  y: number,
  z: number,
  maxRise: number = SLAB_HEIGHT,
): Array<[number, number, number]> {
  const out: Array<[number, number, number]> = [];
  const here = cells.get(coordKey(x, y, z));
  if (here === undefined) return out;

  const STEPS: ReadonlyArray<[number, number]> = [[1, 0], [-1, 0], [0, 1], [0, -1]];
  const DYS: ReadonlyArray<number> = [0, 1, -1, -2, -3];

  for (const [dx, dz] of STEPS) {
    for (const dy of DYS) {
      const qx = x + dx;
      const qy = y + dy;
      const qz = z + dz;
      const qKey = coordKey(qx, qy, qz);
      const qElev = cells.get(qKey);
      if (qElev === undefined) continue;
      const rise = qElev - here;
      // Falling is free; climbing is limited by what you step onto.
      if (rise > allowedRise(grid, qx, qy, qz, maxRise) + 1e-6) continue;
      if (rise < -3.0) continue; // too far to drop safely
      out.push([qx, qy, qz]);
      break;
    }
  }

  for (const [qx, qy, qz] of climbLinks(grid, x, y, z)) {
    if (cells.has(coordKey(qx, qy, qz))) {
      out.push([qx, qy, qz]);
    }
  }
  // A ladder in the cell above or below links vertically too.
  for (const dy of [1, -1]) {
    const qy = y + dy;
    const q = grid.get(x, qy, z);
    if (q !== null && CLIMBABLE.has(q.id) && cells.has(coordKey(x, qy, z))) {
      out.push([x, qy, z]);
    }
  }
  return out;
}

// ── flood fill ─────────────────────────────────────────────────────────────

/**
 * Flood fill from `start` over walkable cells. Returns the set of reachable
 * cell keys ("x,y,z" strings).
 */
export function reachable(
  grid: BlockGrid,
  start: Iterable<[number, number, number]>,
  maxRise: number = SLAB_HEIGHT,
): Set<string> {
  const cells = walkable(grid);
  const seen = new Set<string>();
  const queue: Array<[number, number, number]> = [];
  for (const [x, y, z] of start) {
    const key = coordKey(x, y, z);
    if (cells.has(key) && !seen.has(key)) {
      seen.add(key);
      queue.push([x, y, z]);
    }
  }
  let head = 0;
  while (head < queue.length) {
    const [x, y, z] = queue[head++];
    for (const [nx, ny, nz] of neighbours(grid, cells, x, y, z, maxRise)) {
      const key = coordKey(nx, ny, nz);
      if (!seen.has(key)) {
        seen.add(key);
        queue.push([nx, ny, nz]);
      }
    }
  }
  return seen;
}

// ── route checking ─────────────────────────────────────────────────────────

export type Route = {
  ok: boolean;
  reason: string;
  /** All cell keys reachable from the start (empty on start/goal failure). */
  reached: Set<string>;
};

/**
 * Is any goal cell reachable from any start cell without jumping?
 *
 * Returns a `Route` describing the outcome. When both sides have standable
 * cells but no path connects them, `reached` holds the full flood fill from the
 * start so the caller can inspect how far the path got.
 */
export function checkRoute(
  grid: BlockGrid,
  start: Array<[number, number, number]>,
  goal: Array<[number, number, number]>,
  label = '',
): Route {
  const cells = walkable(grid);
  const liveStart = start.filter(([x, y, z]) => cells.has(coordKey(x, y, z)));
  const liveGoal = goal.filter(([x, y, z]) => cells.has(coordKey(x, y, z)));
  if (liveStart.length === 0) {
    return {
      ok: false,
      reason: `${label}: no standable start cell among ${JSON.stringify(start.slice(0, 4))}`,
      reached: new Set(),
    };
  }
  if (liveGoal.length === 0) {
    return {
      ok: false,
      reason: `${label}: no standable goal cell among ${JSON.stringify(goal.slice(0, 4))}`,
      reached: new Set(),
    };
  }
  const seen = reachable(grid, liveStart);
  const hit = liveGoal.some(([x, y, z]) => seen.has(coordKey(x, y, z)));
  if (hit) {
    return { ok: true, reason: `${label}: reachable`, reached: seen };
  }
  return {
    ok: false,
    reason: `${label}: ${liveGoal.length} standable goal cell(s) but none reachable from the start`,
    reached: seen,
  };
}

// ── column utility ─────────────────────────────────────────────────────────

/** Highest occupied y in a column, or -1. */
export function columnTop(grid: BlockGrid, x: number, z: number): number {
  let best = -1;
  const sy = grid.size[1];
  for (let y = 0; y < sy; y++) {
    if (grid.occupied(x, y, z)) best = y;
  }
  return best;
}
