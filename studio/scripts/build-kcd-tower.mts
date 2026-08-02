/**
 * Build a KCD-style wooden watchtower.
 *
 * Bohemian medieval, 15th century — Kingdom Come: Deliverance vibe.
 * Vertical log walls, thatched roof (slab pitch), corner posts, lookout deck.
 * Skips validation deliberately: the plan violates the corpus's structural
 * measures on purpose (skipped floors, bare-bones building), and we want
 * to see what the generator actually produces.
 *
 * Run from studio/:
 *   node_modules/.bin/vite-node scripts/build-kcd-tower.mts
 *
 * Writes: tools/tmp/kcd-tower.nbt
 */

import { writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import { generateStructure, type Plan } from '../src/dsl/index';

const __dirname = dirname(fileURLToPath(import.meta.url));
const STUDIO = join(__dirname, '..');
const REPO = join(STUDIO, '..');
const OUT = join(REPO, 'tools', 'tmp', 'kcd-tower.nbt');

const plan: Plan = {
  name: 'kcd_watchtower',
  version: 1,
  structure: { footprint: [5, 5], height: 8, origin: [0, 0, 0] },
  floors: [
    { range: [0, 0], material: 'cobblestone', layout: 'ground' },
    { range: [1, 2], material: 'oak_log', layout: 'residential' },
    { range: [3, 5], material: 'oak_planks', layout: 'residential' },
    { range: [6, 7], material: 'oak_slab', layout: 'battlements' },
  ],
  devices: [
    { kind: 'door', side: 'south', floor: 1 },
    { kind: 'ladder', side: 'north', pos: [2, 2, 1] },
    { kind: 'torch', pos: [2, 1, 2] },
    { kind: 'torch', pos: [2, 4, 2] },
    { kind: 'crenellation', top_y: 7, spacing: 1 },
  ],
  rules: { guard: [], compliance: [] },
  output: { name: 'kcd_watchtower', path: 'recovered/' },
};

mkdirSync(dirname(OUT), { recursive: true });
const result = generateStructure(plan, { skipValidation: true });
const nbt = result.structure.writeNbt();
writeFileSync(OUT, Buffer.from(nbt));

console.log(`Wrote ${OUT}`);
console.log(`  size: ${result.bbox.join('×')}`);
console.log(`  blocks: ${result.blockCount}`);
