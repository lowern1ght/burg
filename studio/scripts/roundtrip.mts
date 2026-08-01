/**
 * Round-trip test — NBT → YAML → NBT on a sample of plains/ structures.
 *
 * Reads each NBT, recovers a Plan via the new serializer, regenerates an
 * NBT from the plan, and reports the block-level diff. The point is to
 * see how well the heuristics actually recover a hand-built structure.
 *
 * Run from studio/:
 *   node_modules/.bin/vite-node scripts/roundtrip.mts
 *
 * Inputs:  ../tools/tmp/roundtrip/*.nbt (4 files copied from plains/)
 * Outputs: prints per-file recoverable plan, warnings, and block diff
 *          vs the original; writes roundtrip_report.json next to itself.
 */

import { readFileSync, writeFileSync, statSync } from 'node:fs';
import { join, relative, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { dirname } from 'node:path';

import { NbtFile, Structure, type BlockState } from '@mattzh72/lodestone';

import {
  serializeStructureToPlan,
  serializePlanToYaml,
  loadPlan,
  generateStructure,
  type Plan,
} from '../src/dsl/index';

function blockId(state: BlockState): string {
  return state.getName().toString();
}

const __dirname = dirname(fileURLToPath(import.meta.url));
const REPO = join(__dirname, '..', '..');
const INPUT_DIR = join(REPO, 'tools', 'tmp', 'roundtrip');
const REPORT_PATH = join(__dirname, 'roundtrip_report.json');

type FileReport = {
  name: string;
  size: number;
  readable: boolean;
  reason?: string;
  recoverable: boolean;
  planValid: boolean;
  floors: number;
  devices: number;
  warnings: { rule: string; message: string }[];
  blockStats: {
    original: number;
    recovered: number;
    identical: number;
    moved: number;
    missing: number;
    extra: number;
  };
  yaml: string;
  plan: Plan;
};

function loadStructure(path: string): Structure | null {
  try {
    const bytes = new Uint8Array(readFileSync(path));
    const nbtFile = NbtFile.read(bytes);
    return Structure.fromNbt(nbtFile.root);
  } catch (err) {
    return null;
  }
}

/** Build a positional map of all non-air blocks in a structure. */
function blockMap(structure: Structure): Map<string, string> {
  const map = new Map<string, string>();
  const size = structure.getSize();
  const blocks = structure.getBlocks();
  for (const block of blocks) {
    const id = blockId(block.state);
    if (id === 'minecraft:air') continue;
    const pos = block.pos;
    const key = `${pos[0]},${pos[1]},${pos[2]}`;
    map.set(key, id);
  }
  return map;
}

function compareStructures(original: Structure, recovered: Structure) {
  const a = blockMap(original);
  const b = blockMap(recovered);
  let identical = 0;
  let moved = 0;
  let missing = 0;
  let extra = 0;
  for (const [key, id] of a) {
    const bId = b.get(key);
    if (bId === undefined) {
      missing += 1;
    } else if (bId === id) {
      identical += 1;
    } else {
      moved += 1;
    }
  }
  for (const [key, id] of b) {
    if (!a.has(key)) extra += 1;
  }
  return { original: a.size, recovered: b.size, identical, moved, missing, extra };
}

function listNbt(dir: string): string[] {
  const out: string[] = [];
  for (const entry of require('node:fs').readdirSync(dir)) {
    if (entry.endsWith('.nbt')) {
      out.push(join(dir, entry));
    }
  }
  return out.sort();
}

function main() {
  const files = listNbt(INPUT_DIR);
  if (files.length === 0) {
    console.error(`No .nbt files in ${INPUT_DIR}`);
    process.exit(1);
  }

  const reports: FileReport[] = [];

  for (const path of files) {
    const name = basename(path);
    console.log(`\n━━━ ${name} ━━━`);

    const size = statSync(path).size;
    const structure = loadStructure(path);
    if (!structure) {
      console.log(`  ⛔ could not load NBT`);
      reports.push({
        name, size, readable: false, reason: 'load_failed',
        recoverable: false, planValid: false, floors: 0, devices: 0,
        warnings: [], blockStats: { original: 0, recovered: 0, identical: 0, moved: 0, missing: 0, extra: 0 },
        yaml: '', plan: {} as Plan,
      });
      continue;
    }

    const structSize = structure.getSize();
    const originalBlockCount = structure.getBlocks().length;
    console.log(`  size: ${structSize[0]}×${structSize[1]}×${structSize[2]}, blocks: ${originalBlockCount}`);

    const result = serializeStructureToPlan(structure, { name: name.replace('.nbt', '') });
    const yaml = serializePlanToYaml(result.plan);

    console.log(`  plan: ${result.plan.floors.length} floors, ${result.plan.devices.length} devices`);
    console.log(`  valid: ${result.valid}`);
    console.log(`  warnings: ${result.warnings.length}`);
    for (const w of result.warnings.slice(0, 6)) {
      console.log(`    - [${w.rule}] ${w.message}`);
    }
    if (result.warnings.length > 6) {
      console.log(`    ... and ${result.warnings.length - 6} more`);
    }

    let regenerated: Structure | null = null;
    let roundTripOk = false;
    let diff = { original: 0, recovered: 0, identical: 0, moved: 0, missing: 0, extra: 0 };
    try {
      const reParsed = loadPlan(yaml);
      roundTripOk = JSON.stringify(reParsed) === JSON.stringify(result.plan);
      const gen = generateStructure(reParsed);
      regenerated = gen.structure;
      diff = compareStructures(structure, regenerated);
      const total = diff.identical + diff.moved + diff.missing + diff.extra;
      const pct = total > 0 ? Math.round((diff.identical / total) * 100) : 0;
      console.log(`  round-trip: ${diff.identical} identical / ${diff.moved} moved / ${diff.missing} missing / ${diff.extra} extra (${pct}% identical)`);
    } catch (err) {
      console.log(`  ⛔ regen failed: ${err instanceof Error ? err.message : String(err)}`);
    }

    reports.push({
      name,
      size,
      readable: true,
      recoverable: roundTripOk,
      planValid: result.valid,
      floors: result.plan.floors.length,
      devices: result.plan.devices.length,
      warnings: result.warnings.map(w => ({ rule: w.rule, message: w.message })),
      blockStats: diff,
      yaml,
      plan: result.plan,
    });
  }

  writeFileSync(REPORT_PATH, JSON.stringify(reports, null, 2));
  console.log(`\nWrote report: ${relative(REPO, REPORT_PATH)}`);

  console.log(`\n━━━ summary ━━━`);
  for (const r of reports) {
    const d = r.blockStats;
    const total = d.identical + d.moved + d.missing + d.extra;
    const pct = total > 0 ? Math.round((d.identical / total) * 100) : 0;
    console.log(`  ${r.name.padEnd(25)} ${r.readable ? 'ok' : 'FAIL'}  floors=${r.floors} devices=${r.devices} warnings=${r.warnings.length}  identity=${pct}%`);
  }
}

main();
