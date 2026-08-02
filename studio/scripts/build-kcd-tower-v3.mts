import { writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import { Structure } from '@mattzh72/lodestone';

const __dirname = dirname(fileURLToPath(import.meta.url));
const STUDIO = join(__dirname, '..');
const REPO = join(STUDIO, '..');
const OUT_NBT = join(REPO, 'tools', 'tmp', 'kcd-tower-v3.nbt');
const OUT_TXT = join(REPO, 'tools', 'tmp', 'kcd-tower-v3.txt');

type Pl = { x: number; y: number; z: number; block: string; note: string };

const W = 5;
const D = 6;
const H = 11;

const placements: Pl[] = [];

const push = (x: number, y: number, z: number, block: string, note: string): void => {
  placements.push({ x, y, z, block, note });
};

const isWall = (x: number, z: number): boolean =>
  x === 0 || x === W - 1 || z === 0 || z === D - 1;

const isCorner = (x: number, z: number): boolean =>
  (x === 0 || x === W - 1) && (z === 0 || z === D - 1);

const DOOR_X = 4;
const DOOR_Z = 5;

const apron: Array<[number, number, string]> = [
  [0, 0, 'coarse_dirt'], [1, 0, 'coarse_dirt'], [2, 0, 'coarse_dirt'],
  [3, 0, 'coarse_dirt'], [4, 0, 'coarse_dirt'],
  [0, 1, 'coarse_dirt'], [1, 1, 'coarse_dirt'],
  [3, 1, 'coarse_dirt'], [4, 1, 'coarse_dirt'],
  [0, 0, 'grass_block'], [2, 0, 'grass_block'],
  [1, 2, 'grass_block'], [2, 2, 'grass_block'],
  [3, 0, 'dirt_path'], [4, 0, 'dirt_path'],
  [3, 1, 'dirt_path'], [4, 1, 'dirt_path'],
  [3, 2, 'dirt_path'], [4, 2, 'dirt_path'],
];
for (const [x, z, block] of apron) {
  push(x, 0, z, block, block === 'dirt_path' ? 'path cluster by door' : 'apron');
}

for (let x = 0; x < W; x++) {
  for (let z = 0; z < D; z++) {
    if (isWall(x, z)) {
      push(x, 0, z, 'cobblestone', 'plinth base');
    }
  }
}

for (let x = 0; x < W; x++) {
  for (let z = 0; z < D; z++) {
    if (isWall(x, z) && !(x === DOOR_X && z === DOOR_Z)) {
      push(x, 1, z, 'mossy_cobblestone', 'stone shaft y=1 (mossy foot)');
    }
  }
}

for (let x = 0; x < W; x++) {
  for (let z = 0; z < D; z++) {
    if (!isWall(x, z)) {
      push(x, 1, z, 'cobblestone', 'stone shaft interior y=1');
    }
  }
}

for (let x = 0; x < W; x++) {
  for (let z = 0; z < D; z++) {
    if (isWall(x, z)) {
      if (x === DOOR_X && z === DOOR_Z) continue;
      if (x === 0 && (z === 2 || z === 3)) continue;
      if (x === W - 1 && (z === 2 || z === 3)) continue;
      push(x, 2, z, 'cobblestone', 'stone shaft y=2');
    }
  }
}

for (let x = 0; x < W; x++) {
  for (let z = 0; z < D; z++) {
    if (!isWall(x, z)) {
      push(x, 2, z, 'cobblestone', 'stone shaft interior y=2');
    }
  }
}

const isDoor = (x: number, z: number, y: number): boolean =>
  x === DOOR_X && z === DOOR_Z && (y === 1 || y === 2);

for (let x = 0; x < W; x++) {
  for (let z = 0; z < D; z++) {
    if (isWall(x, z)) {
      if (isDoor(x, z, 3)) continue;
      push(x, 3, z, 'cobblestone', 'stone shaft y=3');
    }
  }
}

for (let x = 0; x < W; x++) {
  for (let z = 0; z < D; z++) {
    if (!isWall(x, z)) {
      push(x, 3, z, 'cobblestone', 'stone shaft interior y=3');
    }
  }
}

for (let x = 0; x < W; x++) {
  for (let z = 0; z < D; z++) {
    if (isWall(x, z)) {
      const useStone = (x + z) % 2 === 0;
      push(x, 4, z, useStone ? 'stone' : 'cobblestone', 'stone shaft y=4 (gradient mix)');
    }
  }
}

for (let x = 0; x < W; x++) {
  for (let z = 0; z < D; z++) {
    if (!isWall(x, z)) {
      push(x, 4, z, 'cobblestone', 'stone shaft interior y=4');
    }
  }
}

for (let x = 0; x < W; x++) {
  for (let z = 0; z < D; z++) {
    if (isWall(x, z)) {
      push(x, 5, z, 'cobblestone', 'stone shaft y=5 (top course)');
    }
  }
}

for (let x = 0; x < W; x++) {
  for (let z = 0; z < D; z++) {
    if (!isWall(x, z)) {
      push(x, 5, z, 'cobblestone', 'stone shaft interior y=5');
    }
  }
}

for (let x = 0; x < W; x++) {
  for (let z = 0; z < D; z++) {
    if (isWall(x, z)) {
      push(x, 6, z, 'cobblestone_slab[type=bottom]', 'dressed walk surface (oversail)');
    }
  }
}

for (let x = 0; x < W; x++) {
  for (let z = 0; z < D; z++) {
    if (!isWall(x, z)) {
      push(x, 6, z, 'oak_planks', 'deck plank (interior)');
    }
  }
}

const corners: Array<[number, number]> = [
  [0, 0], [W - 1, 0], [0, D - 1], [W - 1, D - 1],
];
for (const [cx, cz] of corners) {
  for (let y = 7; y <= 9; y++) {
    push(cx, y, cz, 'oak_log[axis=y]', 'deck corner post');
  }
}

for (let x = 1; x < W - 1; x++) {
  push(x, 9, 0, 'oak_log[axis=x]', 'parapet beam N');
  push(x, 9, D - 1, 'oak_log[axis=x]', 'parapet beam S');
}
for (let z = 1; z < D - 1; z++) {
  push(0, 9, z, 'oak_log[axis=z]', 'parapet beam W');
  push(W - 1, 9, z, 'oak_log[axis=z]', 'parapet beam E');
}

for (let x = 0; x < W; x++) {
  const isMerlonN = x === 0 || x === 2 || x === 4;
  push(x, 9, 0, isMerlonN ? 'cobblestone' : 'air', isMerlonN ? 'merlon N' : 'murder hole N');
  const isMerlonS = x === 1 || x === 3 || x === W - 1;
  push(x, 9, D - 1, isMerlonS ? 'cobblestone' : 'air', isMerlonS ? 'merlon S' : 'murder hole S');
}

for (let z = 0; z < D; z++) {
  const isMerlonW = z === 0 || z === 2 || z === 4;
  push(0, 9, z, isMerlonW ? 'cobblestone' : 'air', isMerlonW ? 'merlon W' : 'murder hole W');
  const isMerlonE = z === 1 || z === 3 || z === D - 1;
  push(W - 1, 9, z, isMerlonE ? 'cobblestone' : 'air', isMerlonE ? 'merlon E' : 'murder hole E');
}

for (let x = 1; x < W - 1; x++) {
  for (let z = 1; z < D - 1; z++) {
    push(x, 9, z, 'oak_planks', 'walk plate (interior of merlon ring)');
  }
}

for (let x = 0; x < W; x++) {
  push(x, 10, D - 1, 'oak_stairs[facing=north,half=bottom]', 'roof step S');
}
for (let x = 1; x < W - 1; x++) {
  push(x, 10, D - 2, 'oak_stairs[facing=north,half=bottom]', 'roof step S inner');
}
for (let z = 0; z < D; z++) {
  push(0, 10, z, 'oak_stairs[facing=east,half=bottom]', 'roof step W');
  push(W - 1, 10, z, 'oak_stairs[facing=west,half=bottom]', 'roof step E');
}
for (let z = 1; z < D - 1; z++) {
  push(1, 10, z, 'oak_stairs[facing=east,half=bottom]', 'roof step W inner');
  push(W - 2, 10, z, 'oak_stairs[facing=west,half=bottom]', 'roof step E inner');
}
for (let x = 1; x < W - 1; x++) {
  push(x, 10, 0, 'oak_stairs[facing=south,half=bottom]', 'roof step N');
}
for (let x = 1; x < W - 1; x++) {
  for (let z = 2; z <= 3; z++) {
    push(x, 10, z, 'oak_slab[type=top]', 'ridge cap');
  }
}

for (let y = 1; y <= 7; y++) {
  push(1, y, 1, 'ladder[facing=south]', 'corner ladder (NW column)');
}

push(DOOR_X, 1, DOOR_Z, 'oak_door[facing=south,half=lower,hinge=left]', 'door lower (SE corner)');
push(DOOR_X, 2, DOOR_Z, 'oak_door[facing=south,half=upper,hinge=left]', 'door upper');
push(DOOR_X, 0, DOOR_Z, 'cobblestone_stairs[facing=south,half=bottom]', 'threshold step');
push(DOOR_X, 3, DOOR_Z, 'oak_slab[type=top]', 'lintel above door');

push(0, 2, 2, 'glass_pane', 'arrow slit W (eye height y=2)');
push(0, 3, 2, 'glass_pane', 'arrow slit W (eye height y=3)');
push(0, 2, 3, 'glass_pane', 'arrow slit W (eye height y=2)');
push(0, 3, 3, 'glass_pane', 'arrow slit W (eye height y=3)');

push(W - 1, 4, 2, 'glass_pane', 'arrow slit E (eye height y=4)');
push(W - 1, 5, 2, 'glass_pane', 'arrow slit E (eye height y=5)');

push(2, 2, 0, 'oak_fence', 'wall opening N (loophole)');
push(2, 3, 0, 'oak_fence', 'wall opening N (loophole)');

push(W - 1, 3, 4, 'red_wall_banner', 'east face banner (prestige)');
push(W - 1, 4, 4, 'red_wall_banner', 'east face banner upper');

push(1, 1, 4, 'barrel', 'ground-floor barrel');
push(3, 1, 1, 'chest', 'ground-floor chest (back)');
push(2, 1, 3, 'campfire', 'hearth (centre of ground floor)');
push(3, 1, 4, 'barrel', 'ground-floor barrel 2');
push(1, 2, 4, 'torch', 'torch by door');
push(3, 3, 3, 'lantern[hanging=true]', 'hanging lantern interior');
push(2, 4, 3, 'lantern[hanging=true]', 'hanging lantern mid');

push(1, 7, 4, 'lantern[hanging=true]', 'lantern corner post W');
push(W - 2, 7, 4, 'lantern[hanging=true]', 'lantern corner post E');
push(1, 8, 1, 'barrel', 'deck barrel (storage on platform)');

mkdirSync(dirname(OUT_NBT), { recursive: true });

const size: [number, number, number] = [W, H, D];
const struct = new Structure(size);

const cells = new Map<string, string>();
for (const p of placements) {
  cells.set(`${p.x},${p.y},${p.z}`, p.block);
}

for (let y = 0; y < H; y++) {
  for (let z = 0; z < D; z++) {
    for (let x = 0; x < W; x++) {
      const pos: [number, number, number] = [x, y, z];
      struct.addBlock(pos, 'minecraft:air');
    }
  }
}

for (const [key, block] of cells) {
  const parts = key.split(',');
  const x = Number(parts[0]);
  const y = Number(parts[1]);
  const z = Number(parts[2]);
  const id = block.includes('[') ? (block.split('[')[0] ?? block) : block;
  const stateStr = block.includes('[') ? ((block.split('[')[1] ?? '').slice(0, -1)) : '';
  const props: Record<string, string> = {};
  if (stateStr) {
    for (const kv of stateStr.split(',')) {
      const eq = kv.indexOf('=');
      if (eq < 0) continue;
      const k = kv.slice(0, eq);
      const v = kv.slice(eq + 1);
      props[k] = v;
    }
  }
  const pos: [number, number, number] = [x, y, z];
  struct.addBlock(pos, `minecraft:${id}`, props);
}

const nbt = struct.writeNbt();
writeFileSync(OUT_NBT, Buffer.from(nbt));

const lines: string[] = [
  'KCD tower v3 — block-by-block sculpt (lvl3, defensible stone)',
  `size: ${W}×${H}×${D}`,
  `blocks: ${placements.length}`,
  '',
];
for (const p of placements.sort((a, b) => a.y - b.y || a.z - b.z || a.x - b.x)) {
  lines.push(
    `y=${String(p.y).padStart(2)} (${String(p.x).padStart(2)},${String(p.z).padStart(2)})  ${p.block.padEnd(48)} ${p.note}`,
  );
}
writeFileSync(OUT_TXT, lines.join('\n'));

console.log(`Wrote ${OUT_NBT}`);
console.log(`Wrote ${OUT_TXT}`);
console.log(`  size: ${W}×${H}×${D}, blocks: ${placements.length}`);
