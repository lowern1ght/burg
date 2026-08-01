import { readFileSync } from 'node:fs';
import { NbtFile, Structure, type BlockState } from '@mattzh72/lodestone';

function blockId(state: BlockState): string {
  return state.getName().toString();
}

const target = process.argv[2];

const bytes = new Uint8Array(readFileSync(target));
const nbtFile = NbtFile.read(bytes);
const structure = Structure.fromNbt(nbtFile.root);
const size = structure.getSize();
console.log(`size: ${size[0]}x${size[1]}x${size[2]}`);
const blocks = structure.getBlocks();
console.log(`blocks: ${blocks.length}`);

const map = new Map<string, BlockState>();
for (const block of blocks) {
  const id = blockId(block.state);
  if (id === 'minecraft:air') continue;
  map.set(`${block.pos[0]},${block.pos[1]},${block.pos[2]}`, block.state);
}

const byRow = new Map<number, string[]>();
for (const [k, state] of map) {
  const [x, y, z] = k.split(',').map(Number);
  const row = byRow.get(y) ?? [];
  if (!byRow.has(y)) byRow.set(y, row);
  row.push(`${idStr(state)}@${x},${z}`);
}
for (let y = 0; y < size[1]; y++) {
  const cells = byRow.get(y) ?? [];
  console.log(`y=${y} (${cells.length}): ${cells.join(' | ')}`);
}

function idStr(state: BlockState): string {
  const id = blockId(state);
  const short = id.includes(':') ? id.slice(10) : id;
  const props: string[] = [];
  for (const [k, v] of Object.entries(state.getProperties())) {
    props.push(`${k}=${v}`);
  }
  return props.length ? `${short}[${props.join(',')}]` : short;
}
