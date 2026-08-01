/**
 * Synthetic BlockGrid builder for engine tests. Mirrors the Python
 * structures.nbtio.Voxels + Canvas combination without touching real .nbt
 * files, so the bicalibration selftest can run in the node test environment.
 */
import type { BlockGrid, BlockInfo } from './appearance';
import type { Coord } from './fabric';

export type GridEntry = {
  pos: Coord;
  id: string;
  props?: Record<string, string>;
};

/** A BlockGrid that also supports writes — what a test needs to assemble a case. */
export interface MutableBlockGrid extends BlockGrid {
  set(x: number, y: number, z: number, block: BlockInfo): void;
}

/** Build a BlockInfo with optional state properties. */
export function block(id: string, props: Record<string, string> = {}): BlockInfo {
  return { id, props };
}

/** Build a small grid pre-loaded with the given blocks. */
export function makeGrid(
  size: [number, number, number],
  entries: GridEntry[] = [],
): MutableBlockGrid {
  const map = new Map<string, BlockInfo>();
  const key = (x: number, y: number, z: number): string => `${x},${y},${z}`;
  for (const e of entries) {
    const [x, y, z] = e.pos;
    map.set(key(x, y, z), { id: e.id, props: e.props ?? {} });
  }
  return {
    size,
    get: (x, y, z) => map.get(key(x, y, z)) ?? null,
    occupied: (x, y, z) => map.has(key(x, y, z)),
    set: (x, y, z, b) => { map.set(key(x, y, z), b); },
    blocks: function* () {
      for (const [k, b] of map) {
        const [x, y, z] = k.split(',').map(Number);
        yield { pos: [x, y, z] as Coord, block: b };
      }
    },
  };
}
