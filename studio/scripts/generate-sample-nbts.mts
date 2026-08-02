import { mkdirSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildFromSpec } from '../src/dsl/building-generator';

const __dirname = dirname(fileURLToPath(import.meta.url));
const STUDIO = join(__dirname, '..');
const REPO = join(STUDIO, '..');
const OUT_DIR = join(REPO, 'tools', 'tmp');

const SAMPLES: Array<{ name: string; spec: Parameters<typeof buildFromSpec>[0] }> = [
  {
    name: 'gen-watchtower-9x8x18',
    spec: {
      family: 'watchtower',
      footprint: [9, 8],
      height: 18,
      decor: 'medium',
      seed: 17,
    },
  },
  {
    name: 'gen-house-9x11x9',
    spec: {
      family: 'house',
      footprint: [9, 11],
      height: 9,
      decor: 'dense',
      seed: 42,
    },
  },
  {
    name: 'gen-wall-segment-8x10x12',
    spec: {
      family: 'wall_segment',
      footprint: [8, 10],
      height: 12,
      decor: 'medium',
      seed: 91,
    },
  },
];

mkdirSync(OUT_DIR, { recursive: true });

const reports: Array<{ name: string; size: string; blocks: number; warnings: string[] }> = [];

for (const sample of SAMPLES) {
  const result = buildFromSpec(sample.spec);
  const [w, h, d] = result.structure.getSize() as [number, number, number];
  const bytes = result.structure.writeNbt({ name: sample.name });
  const nbtPath = join(OUT_DIR, `${sample.name}.nbt`);
  writeFileSync(nbtPath, bytes);
  const blockCount = result.structure.getBlocks().length;
  reports.push({
    name: sample.name,
    size: `${w}x${h}x${d}`,
    blocks: blockCount,
    warnings: result.warnings,
  });
  console.log(`wrote ${nbtPath} (${w}x${h}x${d}, ${blockCount} blocks)`);
}

console.log('\nsummary:');
for (const r of reports) {
  console.log(`  ${r.name}: ${r.size}, ${r.blocks} blocks, ${r.warnings.length} warnings`);
  for (const w of r.warnings) console.log(`    - ${w}`);
}
