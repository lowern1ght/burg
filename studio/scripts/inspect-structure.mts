import { readFileSync } from 'node:fs';
import { NbtFile, Structure } from '@mattzh72/lodestone';

const path = process.argv[2] ?? '../tools/tmp/kcd-tower-v2.nbt';
const bytes = new Uint8Array(readFileSync(path));
const nbt = NbtFile.read(bytes);
const s = Structure.fromNbt(nbt.root);
const [w, h, d] = s.getSize();
const blocks = s.getBlocks();
const counts = new Map<string, number>();
for (const b of blocks) {
  const id = b.state.getName().toString();
  counts.set(id, (counts.get(id) ?? 0) + 1);
}
const sorted = [...counts.entries()].sort((a, b) => b[1] - a[1]);
console.log(`Structure: ${w}×${h}×${d}`);
console.log(`Total blocks: ${blocks.length}`);
console.log('Block counts:');
for (const [id, n] of sorted) console.log(`  ${n.toString().padStart(4)} ${id}`);
