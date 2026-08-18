/**
 * Bicalibration — prove the TS checkers match the Python checkers on the
 * author's 125-file corpus.
 *
 * The author's own NBT files are the calibration set: every checker in this
 * repo was measured against them, and a metric that is noisy on his work is a
 * broken metric, not a finding. So the TS port must also be near-silent here.
 * Any divergence between TS and Python is a port bug.
 *
 * Run from studio/:
 *     node_modules/.bin/vite-node scripts/bicalibrate.mts
 *
 * Reads:  all .nbt files under plains/ in the author's structure corpus
 * Writes: scripts/bicalibration-report.json
 */

import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join, relative, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { dirname } from 'node:path';

import { NbtFile, Structure } from '@mattzh72/lodestone';

import { runAllChecks, structureToGrid } from '../src/engine/index';
import { checkStray } from '../src/engine/checkStray';
import { checkStairs, CORPUS_RESIDUAL } from '../src/engine/checkStairs';
import { checkFabric } from '../src/engine/checkFabric';
import { checkIntegrity } from '../src/engine/checkIntegrity';

const __dirname = dirname(fileURLToPath(import.meta.url));
const STUDIO = resolve(__dirname, '..');
const REPO = resolve(STUDIO, '..');
const CORPUS_ROOT = join(
  REPO,
  'common', 'src', 'main', 'resources', 'data', 'burg', 'structure', 'plains',
);

// ── helpers ────────────────────────────────────────────────────────────────

function resolve(...parts: string[]): string {
  let p = parts.join(sep);
  return p;
}

/** Recursively collect every .nbt file under root, sorted for deterministic order. */
function collectNbt(root: string): string[] {
  const out: string[] = [];
  function walk(dir: string): void {
    for (const entry of readdirSync(dir)) {
      const full = join(dir, entry);
      const st = statSync(full);
      if (st.isDirectory()) {
        walk(full);
      } else if (entry.endsWith('.nbt')) {
        out.push(full);
      }
    }
  }
  walk(root);
  out.sort();
  return out;
}

/** Load + parse an NBT file into a Lodestone Structure (or null if unreadable). */
function loadStructure(path: string): Structure | null {
  try {
    const bytes = new Uint8Array(readFileSync(path));
    const nbtFile = NbtFile.read(bytes);
    return Structure.fromNbt(nbtFile.root);
  } catch (err) {
    return null;
  }
}

type StrayCounts = { strays: number; spikes: number };
type FabricCounts = {
  roofHanging: number;
  roofHoles: number;
  slabRiders: number;
  cantilever: number;
  fencePropsWrong: number;
  fenceStumps: number;
  sky: number;
  lineDiagonal: number;
  lineDuplicate: number;
};
type IntegrityCounts = {
  building: boolean;
  walls: number;
  roof: number;
  doors: number;
  floating: number;
  room: number;
};

type FileReport = {
  /** Path relative to corpus root, POSIX-style. */
  name: string;
  /** Absolute path. */
  path: string;
  loaded: boolean;
  blockCount: number;
  stray: StrayCounts;
  fabric: FabricCounts;
  stairsDownhill: number;
  integrity: IntegrityCounts;
  /** Raw findings surfaced by runAllChecks (label → count + sample). */
  checks: Record<string, { ok: boolean; count: number; sample: string[] }>;
};

// ── main ───────────────────────────────────────────────────────────────────

function main(): void {
  const files = collectNbt(CORPUS_ROOT);
  console.log(`bicalibrate: ${files.length} NBT files under plains/`);

  const reports: FileReport[] = [];
  let unreadable = 0;
  let blockTotal = 0;

  for (const path of files) {
    const name = relative(CORPUS_ROOT, path).split(sep).join('/');
    const structure = loadStructure(path);
    if (structure === null) {
      unreadable += 1;
      reports.push(emptyReport(name, path));
      console.log(`  skip   ${name}: unreadable NBT`);
      continue;
    }

    const placed = structure.getBlocks();
    const blockCount = placed.length;
    blockTotal += blockCount;

    const grid = structureToGrid(
      () => structure.getSize() as [number, number, number],
      () => placed.map(pb => ({
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
      })),
    );

    // Run each checker directly so we capture the raw counts (runAllChecks
    // collapses them into ok/fail buckets; we want the underlying numbers to
    // compare against the Python calibration tables).
    const stray = checkStray(grid);
    const fabric = checkFabric(grid);
    const stairs = checkStairs(grid);
    const integrity = checkIntegrity(grid);

    // Also run the unified report so we can see the gated findings exactly as
    // the Studio UI would surface them.
    const full = runAllChecks(grid);
    const checks: FileReport['checks'] = {};
    for (const r of full.results) {
      checks[`${r.category}:${r.label}`] = {
        ok: r.ok,
        count: r.count,
        sample: r.findings.slice(0, 3),
      };
    }

    reports.push({
      name,
      path,
      loaded: true,
      blockCount,
      stray: { strays: stray.strays.length, spikes: stray.spikes.length },
      fabric: {
        roofHanging: fabric.roof.hanging.length,
        roofHoles: fabric.roof.holed.length,
        slabRiders: fabric.slab.length,
        cantilever: fabric.cantilever.length,
        fencePropsWrong: fabric.fence.wrong.length,
        fenceStumps: fabric.fence.stumps.length,
        sky: fabric.roof.sky.length,
        lineDiagonal: fabric.line.diagonal.length,
        lineDuplicate: fabric.line.duplicate.length,
      },
      stairsDownhill: stairs.count,
      integrity: {
        building: !('building' in integrity),
        walls: integrity.walls?.length ?? 0,
        roof: integrity.roof?.length ?? 0,
        doors: integrity.doors?.length ?? 0,
        floating: integrity.floating?.length ?? 0,
        room: integrity.room?.length ?? 0,
      },
      checks,
    });
  }

  // ── summary ────────────────────────────────────────────────────────────

  const readable = reports.filter(r => r.loaded);
  const sum = summarise(readable);

  console.log('');
  console.log(`readable: ${readable.length} / ${files.length} (${unreadable} unreadable)`);
  console.log(`blocks:   ${blockTotal.toLocaleString('en-US')} total across corpus`);
  console.log('');
  printSummary(sum);

  // ── write JSON report ──────────────────────────────────────────────────

  const report = {
    generated: new Date().toISOString(),
    corpusRoot: relative(REPO, CORPUS_ROOT).split(sep).join('/'),
    fileCount: files.length,
    readable: readable.length,
    unreadable,
    blockTotal,
    summary: sum,
    files: reports,
  };
  const outPath = join(__dirname, 'bicalibration-report.json');
  writeFileSync(outPath, JSON.stringify(report, null, 2) + '\n', 'utf8');
  console.log(`\nwrote ${relative(STUDIO, outPath).split(sep).join('/')}`);
}

function emptyReport(name: string, path: string): FileReport {
  return {
    name, path, loaded: false, blockCount: 0,
    stray: { strays: 0, spikes: 0 },
    fabric: {
      roofHanging: 0, roofHoles: 0, slabRiders: 0, cantilever: 0,
      fencePropsWrong: 0, fenceStumps: 0, sky: 0,
      lineDiagonal: 0, lineDuplicate: 0,
    },
    stairsDownhill: 0,
    integrity: { building: false, walls: 0, roof: 0, doors: 0, floating: 0, room: 0 },
    checks: {},
  };
}

// ── per-checker summaries ─────────────────────────────────────────────────

type CheckerSummary = {
  /** How many readable files produced any finding for this measure. */
  filesWithFindings: number;
  /** Worst single-file count. */
  worst: number;
  /** Files whose count crosses the Python calibration threshold (a discrepancy). */
  overThreshold: Array<{ name: string; count: number }>;
  /** Files with any finding, with counts (capped for readability). */
  hot: Array<{ name: string; count: number }>;
};

type Summary = {
  strayStrays: CheckerSummary;
  straySpikes: CheckerSummary;
  fabricRoofHanging: CheckerSummary;
  fabricRoofHoles: CheckerSummary;
  fabricSlabRiders: CheckerSummary;
  fabricCantilever: CheckerSummary;
  fabricFenceProps: CheckerSummary;
  stairsDownhill: CheckerSummary;
  integrityWalls: CheckerSummary;
  integrityFloating: CheckerSummary;
  /** Files where any gated runAllChecks bucket failed. */
  gatedFailures: Array<{ name: string; failed: string[] }>;
  /** Per-checker totals across the corpus. */
  totals: Record<string, number>;
};

function summarise(readable: FileReport[]): Summary {
  const tally = (
    pick: (r: FileReport) => number,
    threshold: number,
  ): CheckerSummary => {
    const withFindings = readable.filter(r => pick(r) > 0);
    const worst = withFindings.reduce((m, r) => Math.max(m, pick(r)), 0);
    const overThreshold = readable
      .filter(r => pick(r) > threshold)
      .map(r => ({ name: r.name, count: pick(r) }))
      .sort((a, b) => b.count - a.count);
    const hot = withFindings
      .map(r => ({ name: r.name, count: pick(r) }))
      .sort((a, b) => b.count - a.count)
      .slice(0, 12);
    return {
      filesWithFindings: withFindings.length,
      worst,
      overThreshold,
      hot,
    };
  };

  // Python calibration thresholds (from the docstrings + source):
  //   roof hanging ....  HANGING_MAX = 0  (zero across every author file)
  //   roof holes .......  HOLES_MAX  = 2  (his worst case, granary_lvl5..7)
  //   slab riders ......  SLAB_RIDERS_MAX = 0
  //   cantilever .......  0  (no block with NO support within 3 cells)
  //   fence props ......  not gated on author files (29 hand-built stale)
  //   stairs downhill ..  CORPUS_RESIDUAL = 1
  //   walls holes ......  WALL_HOLE_MAX = 21 (his p95)
  //   floating .........  0 (nothing floats by design; roof gate is broken)
  //   stray / spike ....  bands, not rules (worst: stray=15 spike=8)
  return {
    strayStrays: tally(r => r.stray.strays, Infinity),
    straySpikes: tally(r => r.stray.spikes, Infinity),
    fabricRoofHanging: tally(r => r.fabric.roofHanging, 0),
    fabricRoofHoles: tally(r => r.fabric.roofHoles, 2),
    fabricSlabRiders: tally(r => r.fabric.slabRiders, 0),
    fabricCantilever: tally(r => r.fabric.cantilever, 0),
    fabricFenceProps: tally(r => r.fabric.fencePropsWrong, Infinity),
    stairsDownhill: tally(r => r.stairsDownhill, CORPUS_RESIDUAL),
    integrityWalls: tally(r => r.integrity.walls, 21),
    integrityFloating: tally(r => r.integrity.floating, 0),
    gatedFailures: readable
      .map(r => {
        const failed = Object.entries(r.checks)
          .filter(([, v]) => !v.ok)
          .map(([k]) => k);
        return failed.length === 0 ? null : { name: r.name, failed };
      })
      .filter((x): x is { name: string; failed: string[] } => x !== null),
    totals: {
      strayStrays: readable.reduce((s, r) => s + r.stray.strays, 0),
      straySpikes: readable.reduce((s, r) => s + r.stray.spikes, 0),
      fabricRoofHanging: readable.reduce((s, r) => s + r.fabric.roofHanging, 0),
      fabricRoofHoles: readable.reduce((s, r) => s + r.fabric.roofHoles, 0),
      fabricSlabRiders: readable.reduce((s, r) => s + r.fabric.slabRiders, 0),
      fabricCantilever: readable.reduce((s, r) => s + r.fabric.cantilever, 0),
      fabricFenceProps: readable.reduce((s, r) => s + r.fabric.fencePropsWrong, 0),
      fabricFenceStumps: readable.reduce((s, r) => s + r.fabric.fenceStumps, 0),
      stairsDownhill: readable.reduce((s, r) => s + r.stairsDownhill, 0),
      integrityWalls: readable.reduce((s, r) => s + r.integrity.walls, 0),
      integrityFloating: readable.reduce((s, r) => s + r.integrity.floating, 0),
    },
  };
}

function printSummary(s: Summary): void {
  const line = (label: string, cs: CheckerSummary, thresh: string): void => {
    const over = cs.overThreshold.length;
    const flag = over === 0 ? 'ok  ' : 'DRIFT';
    console.log(
      `  ${flag}  ${label.padEnd(28)} files=${String(cs.filesWithFindings).padStart(3)}  ` +
      `worst=${String(cs.worst).padStart(3)}  over=${over}  (py threshold: ${thresh})`,
    );
    for (const h of cs.hot.slice(0, 6)) {
      console.log(`        ${h.name.padEnd(36)} ${h.count}`);
    }
  };

  console.log('stray:');
  line('stray blocks', s.strayStrays, 'band (py worst 15)');
  line('spike blocks', s.straySpikes, 'band (py worst 8)');
  console.log('');
  console.log('fabric:');
  line('roof-hanging', s.fabricRoofHanging, 'HANGING_MAX = 0');
  line('roof-holes', s.fabricRoofHoles, 'HOLES_MAX = 2');
  line('slab-riders', s.fabricSlabRiders, 'SLAB_RIDERS_MAX = 0');
  line('cantilever', s.fabricCantilever, '0 (no support within 3)');
  line('fence-props (info)', s.fabricFenceProps, 'not gated on author files');
  console.log('');
  console.log('stairs:');
  line('downhill stairs', s.stairsDownhill, `CORPUS_RESIDUAL = ${CORPUS_RESIDUAL}`);
  console.log('');
  console.log('integrity:');
  line('wall holes', s.integrityWalls, 'WALL_HOLE_MAX = 21');
  line('floating blocks', s.integrityFloating, '0');
  console.log('');

  console.log(`gated failures (runAllChecks !ok): ${s.gatedFailures.length} files`);
  for (const g of s.gatedFailures.slice(0, 12)) {
    console.log(`   ${g.name}: ${g.failed.join(', ')}`);
  }

  console.log('');
  console.log('corpus totals:');
  for (const [k, v] of Object.entries(s.totals)) {
    console.log(`   ${k.padEnd(24)} ${v}`);
  }
}

main();
