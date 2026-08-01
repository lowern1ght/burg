import { describe, expect, it } from 'vitest';
import { FabricGuard, type Coord, type Fault } from './fabric';
import type { BlockInfo } from './appearance';
import { makeGrid, block, type MutableBlockGrid } from './test-utils';

/**
 * Bicalibration selftest — port of `tools/calibrate_fabric.py` `selftest()`.
 *
 * The Python guard and this TS guard must agree on every case, otherwise the
 * two have drifted. Each case reproduces the smallest voxels the Python builds:
 *
 *   - the two bugs that shipped (leaf over slab, fence over slab) must fire;
 *   - his four legitimate stacks (cube on a post, fence on fence, block on a
 *     trapdoor, block on a pressure plate) must stay silent;
 *   - put_on refuses a half support and accepts a post;
 *   - a roof block with no support floats, with a neighbour does not.
 *
 * The Python selftest's sabotaged-driver case (build_livestock + a monkey-
 * patched pasture.planting) is out of scope here: it needs the full generator
 * pipeline, not the guard in isolation, and has no unit-test analogue.
 */

/**
 * put_on(x, z, baseY, block) — port of Python `Canvas.put_on`.
 * Inspects the support under (x, baseY+1, z); on a refusal returns null and
 * the faults, on acceptance places the block and returns the target coord.
 */
function putOn(
  guard: FabricGuard,
  grid: MutableBlockGrid,
  x: number, z: number, baseY: number,
  b: BlockInfo,
): { placed: Coord | null; faults: Fault[] } {
  const target: Coord = [x, baseY + 1, z];
  const faults = guard.checkPlacement(grid, target, b);
  if (faults.length > 0) return { placed: null, faults };
  grid.set(x, baseY + 1, z, b);
  return { placed: target, faults };
}

describe('FabricGuard — bicalibration selftest (port of calibrate_fabric.py)', () => {
  describe('the two bugs that shipped', () => {
    it('1. a leaf over a bottom slab fires "rider"', () => {
      // calibrate_fabric.py case 1: 38 leaves half a block over their slab.
      const grid = makeGrid([8, 8, 8], [
        { pos: [3, 1, 3], id: 'oak_slab', props: { type: 'bottom' } },
      ]);
      const guard = new FabricGuard();

      const faults = guard.inspect(grid, [3, 2, 3], block('oak_leaves'));

      expect(faults.map(f => f.kind)).toEqual(['rider']);
    });

    it('2. a fence over a bottom slab fires "rail-on-half"', () => {
      // calibrate_fabric.py case 2: 0 in his corpus — always wrong.
      const grid = makeGrid([8, 8, 8], [
        { pos: [5, 1, 5], id: 'oak_slab', props: { type: 'bottom' } },
      ]);
      const guard = new FabricGuard();

      const faults = guard.inspect(grid, [5, 2, 5], block('oak_fence'));

      expect(faults.map(f => f.kind)).toEqual(['rail-on-half']);
    });
  });

  describe('his four legitimate stacks (must stay silent)', () => {
    it('3. a cube over a fence post does not fault — the post fills its cell vertically', () => {
      // beam on a post: ~370 cases in his corpus.
      const grid = makeGrid([8, 8, 8], [
        { pos: [2, 1, 2], id: 'oak_fence' },
        { pos: [2, 2, 2], id: 'oak_planks' },
      ]);
      const guard = new FabricGuard();

      expect(guard.inspectAll(grid)).toHaveLength(0);
    });

    it('4. a fence over a fence does not fault', () => {
      // fence on fence: ~743 cases in his corpus.
      const grid = makeGrid([8, 8, 8], [
        { pos: [7, 1, 7], id: 'oak_fence' },
        { pos: [7, 2, 7], id: 'oak_fence' },
      ]);
      const guard = new FabricGuard();

      expect(guard.inspectAll(grid)).toHaveLength(0);
    });

    it('5. a block over a closed bottom trapdoor does not fault', () => {
      // block over a hatch: ~9 cases in his corpus.
      const grid = makeGrid([8, 8, 8], [
        { pos: [4, 1, 4], id: 'oak_trapdoor', props: { half: 'bottom', open: 'false', facing: 'north' } },
        { pos: [4, 2, 4], id: 'oak_planks' },
      ]);
      const guard = new FabricGuard();

      expect(guard.inspectAll(grid)).toHaveLength(0);
    });

    it('6. a block over a pressure plate does not fault', () => {
      // a floor fitting carries a full block above it.
      const grid = makeGrid([8, 8, 8], [
        { pos: [6, 1, 6], id: 'oak_pressure_plate' },
        { pos: [6, 2, 6], id: 'oak_planks' },
      ]);
      const guard = new FabricGuard();

      expect(guard.inspectAll(grid)).toHaveLength(0);
    });
  });

  describe('put_on — refuse a half support, accept a post', () => {
    it('7. put_on on a bottom slab refuses (rider fault, returns null)', () => {
      const grid = makeGrid([8, 8, 8], [
        { pos: [3, 1, 3], id: 'oak_slab', props: { type: 'bottom' } },
      ]);
      const guard = new FabricGuard();

      const result = putOn(guard, grid, 3, 3, 1, block('hay_block', { axis: 'y' }));

      expect(result.placed).toBeNull();
      expect(result.faults.map(f => f.kind)).toEqual(['rider']);
    });

    it('8. put_on on a fence post accepts (returns the cell above)', () => {
      const grid = makeGrid([8, 8, 8], [
        { pos: [2, 1, 2], id: 'oak_fence' },
      ]);
      const guard = new FabricGuard();

      const result = putOn(guard, grid, 2, 2, 1, block('oak_planks'));

      expect(result.placed).toEqual([2, 2, 2]);
      expect(result.faults).toEqual([]);
    });
  });

  describe('floating roof', () => {
    it('9. a roof block with nothing under or beside it floats', () => {
      const grid = makeGrid([8, 8, 8], [
        { pos: [3, 2, 3], id: 'oak_stairs', props: { facing: 'east', half: 'bottom' } },
      ]);
      const guard = new FabricGuard();
      guard.setDevice('byre roof');

      guard.finishDevice(grid, [[3, 2, 3]]);

      expect(guard.faults.map(f => f.kind)).toEqual(['floating']);
    });

    it('10. a roof block laid beside another does not float', () => {
      // a roof course laid cell by cell: each block sees a neighbour, so the
      // floating check must stay quiet — mirroring the legitimate roof course.
      const grid = makeGrid([8, 8, 8], [
        { pos: [3, 2, 3], id: 'oak_stairs', props: { facing: 'east', half: 'bottom' } },
        { pos: [4, 2, 3], id: 'oak_stairs', props: { facing: 'east', half: 'bottom' } },
      ]);
      const guard = new FabricGuard();
      guard.setDevice('byre roof');

      guard.finishDevice(grid, [[3, 2, 3], [4, 2, 3]]);

      expect(guard.faults).toEqual([]);
    });
  });
});
