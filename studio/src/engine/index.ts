/**
 * Unified checker — runs all ported checks on a BlockGrid.
 * Returns a structured report for the Burg Studio UI.
 */

import { type BlockGrid } from './appearance';
import { structureToGrid } from './fabric';
import { checkFabric, type FabricReport } from './checkFabric';
import { checkStray, type StrayReport } from './checkStray';
import { checkStairs, type StairsResult } from './checkStairs';
import { checkIntegrity, type IntegrityResult } from './checkIntegrity';
import type { Structure } from '@mattzh72/lodestone';

export type CheckCategory = 'integrity' | 'fabric' | 'stray' | 'stairs';

export type CheckResult = {
  category: CheckCategory;
  label: string;
  ok: boolean;
  count: number;
  findings: string[];
};

export type FullReport = {
  results: CheckResult[];
  totalFaults: number;
  ok: boolean;
  summary: string;
};

export { structureToGrid };

export function runAllChecks(grid: BlockGrid): FullReport {
  const integrity: IntegrityResult = checkIntegrity(grid);
  const fabric: FabricReport = checkFabric(grid);
  const stray: StrayReport = checkStray(grid);
  const stairs: StairsResult = checkStairs(grid);

  const results: CheckResult[] = [];

  // Integrity (5 primitives)
  for (const [name, findings] of Object.entries(integrity)) {
    results.push({
      category: 'integrity',
      label: name,
      ok: findings.length === 0,
      count: findings.length,
      findings,
    });
  }

  // Fabric
  results.push({ category: 'fabric', label: 'roof-hanging', ok: fabric.roof.hanging.length === 0, count: fabric.roof.hanging.length, findings: fabric.roof.hanging });
  results.push({ category: 'fabric', label: 'roof-holes', ok: fabric.roof.holed.length <= 2, count: fabric.roof.holed.length, findings: fabric.roof.holed });
  results.push({ category: 'fabric', label: 'slab-riders', ok: fabric.slab.length === 0, count: fabric.slab.length, findings: fabric.slab });
  results.push({ category: 'fabric', label: 'cantilever', ok: fabric.cantilever.length === 0, count: fabric.cantilever.length, findings: fabric.cantilever });
  results.push({ category: 'fabric', label: 'fence-props', ok: fabric.fence.wrong.length === 0, count: fabric.fence.wrong.length, findings: fabric.fence.wrong });
  results.push({ category: 'fabric', label: 'line-diagonal', ok: fabric.line.diagonal.length === 0, count: fabric.line.diagonal.length, findings: fabric.line.diagonal });

  // Stray
  results.push({ category: 'stray', label: 'stray blocks', ok: stray.strays.length === 0, count: stray.strays.length, findings: stray.strays });
  results.push({ category: 'stray', label: 'spike blocks', ok: stray.spikes.length === 0, count: stray.spikes.length, findings: stray.spikes });

  // Stairs
  results.push({ category: 'stairs', label: 'downhill stairs', ok: stairs.count <= stairs.residual, count: stairs.count, findings: stairs.downhill });

  const totalFaults = results.filter(r => !r.ok).length;
  return {
    results,
    totalFaults,
    ok: totalFaults === 0,
    summary: totalFaults === 0 ? 'All checks pass.' : `${totalFaults} check(s) failed.`,
  };
}

/** Convenience: run checks directly on a Lodestone Structure. */
export function runChecksOnStructure(structure: Structure): FullReport {
  const size = structure.getSize() as [number, number, number];
  const blocks = structure.getBlocks().map(pb => ({
    pos: pb.pos as [number, number, number],
    state: {
      getName: () => ({ toString: () => pb.state.getName().toString() }),
      getProperties: () => {
        const props: Record<string, string> = {};
        for (const [k, v] of Object.entries(pb.state.getProperties())) {
          props[k] = String(v);
        }
        return props;
      },
      is: (id: string) => pb.state.is(id),
    },
  }));
  const grid = structureToGrid(() => size, () => blocks);
  return runAllChecks(grid);
}
