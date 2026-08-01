/**
 * Write-time guard — ported from tools/structures/fabric.py.
 *
 * Checks every block placement against the shape model. Three always-wrong
 * cases are refused: cube-on-bottom-slab, rail-on-bottom-slab, roof-floating.
 *
 * In the Python pipeline this wraps Voxels as a context-managed Canvas.
 * In TS, it works on a BlockGrid + a list of edits, providing:
 *
 * - inspectAll(grid) — apply rules to a finished structure
 * - checkPlacement(grid, pos, block) — would placing this block be legal?
 * - checkDeviceFinish(grid, positions) — floating-roof check after a pass
 */

import { type BlockGrid, type BlockInfo } from './appearance';
import { fillsCell, halfStep, isRail, isRoofMaterial, sideAttached, topFace } from './solids';

export type Coord = [number, number, number];

export type Fault = {
  kind: string;
  pos: Coord;
  device: string;
  detail: string;
};

export class FabricGuard {
  readonly faults: Fault[] = [];
  private deviceName = '?';
  private origin = new Map<string, string>();

  setDevice(name: string): void {
    this.deviceName = name;
  }

  recordOrigin(pos: Coord): void {
    this.origin.set(pos.join(','), this.deviceName);
  }

  /** Check a single placement. Returns faults (empty = OK). */
  inspect(grid: BlockGrid, pos: Coord, block: BlockInfo): Fault[] {
    const [x, y, z] = pos;
    const below = grid.get(x, y - 1, z);
    const out: Fault[] = [];

    if (sideAttached(block) || y <= 1) return out;

    const gap = halfStep(below);

    if (gap && fillsCell(block)) {
      out.push({
        kind: 'rider',
        pos,
        device: this.deviceName,
        detail: `${block.id} rests on ${below!.id}, whose top is at ${topFace(below)} of its cell`,
      });
    }

    if (gap && isRail(block)) {
      out.push({
        kind: 'rail-on-half',
        pos,
        device: this.deviceName,
        detail: `${block.id} over ${below!.id}`,
      });
    }

    return out;
  }

  /** Check for floating roof blocks after a device finishes. */
  finishDevice(grid: BlockGrid, written: Coord[]): void {
    for (const pos of written) {
      const [x, y, z] = pos;
      const block = grid.get(x, y, z);
      if (!block || !isRoofMaterial(block) || y <= 1) continue;
      if (grid.get(x, y - 1, z) !== null) continue;
      const near = [
        grid.get(x + 1, y, z), grid.get(x - 1, y, z),
        grid.get(x, y, z + 1), grid.get(x, y, z - 1),
        grid.get(x + 1, y - 1, z), grid.get(x - 1, y - 1, z),
        grid.get(x, y - 1, z + 1), grid.get(x, y - 1, z - 1),
        grid.get(x + 1, y + 1, z), grid.get(x - 1, y + 1, z),
        grid.get(x, y + 1, z + 1), grid.get(x, y + 1, z - 1),
      ];
      if (near.some(b => b !== null)) continue;
      this.faults.push({
        kind: 'floating',
        pos,
        device: this.deviceName,
        detail: `${block.id} has nothing under or beside it`,
      });
    }
  }

  /** Apply write-time rules to every block already in the grid. */
  inspectAll(grid: BlockGrid): Fault[] {
    const found: Fault[] = [];
    for (const { pos, block } of grid.blocks()) {
      const faults = this.inspect(grid, pos, block);
      for (const f of faults) {
        found.push({
          ...f,
          device: this.origin.get(pos.join(',')) ?? '?',
        });
      }
    }
    return found;
  }

  /** Would placing `block` at `pos` create a fault? */
  checkPlacement(grid: BlockGrid, pos: Coord, block: BlockInfo): Fault[] {
    return this.inspect(grid, pos, block);
  }

  report(): string {
    if (this.faults.length === 0) return 'fabric: clean';
    const byDevice = new Map<string, Fault[]>();
    for (const f of this.faults) {
      const list = byDevice.get(f.device) ?? [];
      list.push(f);
      byDevice.set(f.device, list);
    }
    const lines = [`fabric: ${this.faults.length} fault(s)`];
    const sorted = [...byDevice.entries()].sort((a, b) => b[1].length - a[1].length);
    for (const [dev, fs] of sorted) {
      lines.push(`  ${dev}: ${fs.length}`);
      for (const f of fs.slice(0, 3)) {
        lines.push(`     ${f.kind} at ${f.pos} — ${f.detail}`);
      }
    }
    return lines.join('\n');
  }
}

/** Convenience: convert a Lodestone Structure to a BlockGrid. */
export function structureToGrid(getSize: () => [number, number, number], getBlocks: () => { pos: [number, number, number]; state: { getName(): { toString(): string }; getProperties(): Record<string, string>; is(id: string): boolean } }[]): BlockGrid {
  const size = getSize();
  const map = new Map<string, BlockInfo>();
  for (const pb of getBlocks()) {
    const [x, y, z] = pb.pos;
    const name = pb.state.getName().toString();
    const id = name.startsWith('minecraft:') ? name.slice(10) : name;
    // Skip air — Python's Voxels.solid_items() excludes it entirely.
    // Without this, air blocks read as full cubes and poison every neighbourhood check.
    if (id === 'air' || id === 'cave_air' || id === 'void_air') continue;
    const props: Record<string, string> = {};
    for (const [k, v] of Object.entries(pb.state.getProperties())) {
      props[k] = String(v);
    }
    map.set(`${x},${y},${z}`, { id, props });
  }
  return {
    size,
    get: (x, y, z) => map.get(`${x},${y},${z}`) ?? null,
    occupied: (x, y, z) => map.has(`${x},${y},${z}`),
    blocks: function* () {
      for (const [key, block] of map) {
        const [x, y, z] = key.split(',').map(Number);
        yield { pos: [x, y, z] as Coord, block };
      }
    },
  };
}
