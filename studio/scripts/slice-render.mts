import { readFileSync } from 'node:fs';
import { NbtFile, Structure } from '@mattzh72/lodestone';

const path = process.argv[2] ?? '../tools/tmp/kcd-tower.nbt';
const bytes = new Uint8Array(readFileSync(path));
const nbt = NbtFile.read(bytes);
const s = Structure.fromNbt(nbt.root);
const [w, h, d] = s.getSize();
const blocks = s.getBlocks();
const grid = new Map<string, string>();
for (const b of blocks) {
  const [x, y, z] = b.pos;
  grid.set(`${x},${y},${z}`, b.state.getName().toString().replace(/^minecraft:/, ''));
}

const legend = new Map<string, string>([
  ['oak_log', '█'],
  ['oak_planks', '▓'],
  ['oak_slab', '·'],
  ['oak_stairs', '∧'],
  ['oak_fence', '║'],
  ['cobblestone', '▒'],
  ['cobblestone_stairs', 'π'],
  ['coarse_dirt', '≈'],
  ['grass_block', '"'],
  ['dirt_path', ':'],
  ['oak_door', 'D'],
  ['glass_pane', 'I'],
  ['torch', 'T'],
  ['lantern', 'L'],
  ['campfire', 'C'],
  ['ladder', 'H'],
  ['barrel', 'B'],
  ['chest', '='],
  ['white_banner', '◊'],
  ['air', ' '],
]);
const fallback = '?';

for (let y = h - 1; y >= 0; y--) {
  const rows: string[] = [];
  for (let z = 0; z < d; z++) {
    let row = '';
    for (let x = 0; x < w; x++) {
      const id = grid.get(`${x},${y},${z}`) ?? 'air';
      row += legend.get(id) ?? fallback;
    }
    rows.push(row);
  }
  console.log(`y=${y.toString().padStart(2)}  ${rows.join(' ')}`);
}
