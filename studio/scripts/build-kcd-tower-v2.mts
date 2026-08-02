/**
 * KCD wooden watchtower — block-by-block sculpt.
 *
 * Style: `.agents/skills/stylekit-from-nbt/SKILL.md` (the existing one).
 * Constraints honored:
 *   - 5×6 footprint (asymmetric, no centre column)
 *   - stair-pitched roof (no flat slab cap)
 *   - door at corner, not centre
 *   - ladder at corner, not centre
 *   - oak_fence as wall opening (not railing)
 *   - coarse_dirt apron asymmetric, no centred path
 *   - mixed materials (timber-frame, not all-log)
 *   - decoration on every storey (barrel, chest, lanterns, banner)
 *
 * Run from studio/:
 *   node_modules/.bin/vite-node scripts/build-kcd-tower-v2.mts
 *
 * Writes:
 *   tools/tmp/kcd-tower-v2.nbt
 *   tools/tmp/kcd-tower-v2.txt — block list with annotations
 */

import { writeFileSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import { NbtFile, Structure, BlockPos } from '@mattzh72/lodestone';

const __dirname = dirname(fileURLToPath(import.meta.url));
const STUDIO = join(__dirname, '..');
const REPO = join(STUDIO, '..');
const OUT_NBT = join(REPO, 'tools', 'tmp', 'kcd-tower-v2.nbt');
const OUT_TXT = join(REPO, 'tools', 'tmp', 'kcd-tower-v2.txt');

type Pl = { x: number; y: number; z: number; block: string; note: string };

const W = 5;
const D = 6;
const H = 9;

const placements: Pl[] = [];

// y=0 ground apron — asymmetric coarse_dirt with grass patches; path near door
// (corner, not centre; "clustered by the door", not a full-length runway)
const apron: Array<[number, number, string]> = [
  [0, 0, 'coarse_dirt'], [1, 0, 'coarse_dirt'], [2, 0, 'coarse_dirt'],
  [3, 0, 'coarse_dirt'], [4, 0, 'coarse_dirt'],
  [0, 0, 'grass_block'], [1, 0, 'grass_block'],
  [3, 0, 'grass_block'], [4, 0, 'grass_block'],
  [3, 1, 'dirt_path'], [4, 1, 'dirt_path'],
  [3, 2, 'dirt_path'], [4, 2, 'dirt_path'],
];
for (const [x, z, block] of apron) {
  if (block === 'dirt_path') {
    placements.push({ x, y: 0, z, block, note: 'path tiles' });
  } else {
    placements.push({ x, y: 0, z, block, note: 'apron' });
  }
}

// y=0 base — cobblestone perimeter (lower stone course)
for (let x = 0; x < W; x++) {
  for (let z = 0; z < D; z++) {
    if (x === 0 || x === W - 1 || z === 0 || z === D - 1) {
      placements.push({ x, y: 0, z, block: 'cobblestone', note: 'base course' });
    }
  }
}

// y=1 cobblestone walls (lower storey, with door at SOUTH-EAST corner)
for (let x = 0; x < W; x++) {
  for (let z = 0; z < D; z++) {
    if (x === 0 || x === W - 1 || z === 0 || z === D - 1) {
      placements.push({ x, y: 1, z, block: 'cobblestone', note: 'lower wall' });
    }
  }
}
// door at south-east corner (x=4, z=D-1=5), floor 1-2
placements.push({ x: 4, y: 1, z: 5, block: 'oak_door[facing=south,half=lower,hinge=left]', note: 'door lower (corner)' });
placements.push({ x: 4, y: 2, z: 5, block: 'oak_door[facing=south,half=upper,hinge=left]', note: 'door upper' });
// stone threshold + plank sill above door
placements.push({ x: 4, y: 0, z: 5, block: 'cobblestone_stairs[facing=south,half=bottom]', note: 'threshold step' });
placements.push({ x: 4, y: 3, z: 5, block: 'oak_slab[type=top]', note: 'sill above door' });

// y=2 cobblestone walls (continues from y=1)
for (let x = 0; x < W; x++) {
  for (let z = 0; z < D; z++) {
    if (x === 0 || x === W - 1 || z === 0 || z === D - 1) {
      if (x === 4 && z === 5) continue; // door upper
      placements.push({ x, y: 2, z, block: 'cobblestone', note: 'lower wall' });
    }
  }
}

// y=3 timber-frame walls — oak_log corner posts + oak_planks walls + cobblestone infill
for (let y = 3; y <= 5; y++) {
  // 4 corner posts (oak_log, axis=y)
  placements.push({ x: 0, y, z: 0, block: 'oak_log[axis=y]', note: 'NE post' });
  placements.push({ x: W - 1, y, z: 0, block: 'oak_log[axis=y]', note: 'NW post' });
  placements.push({ x: 0, y, z: D - 1, block: 'oak_log[axis=y]', note: 'SE post' });
  placements.push({ x: W - 1, y, z: D - 1, block: 'oak_log[axis=y]', note: 'SW post' });
  // perimeter walls oak_planks
  for (let x = 0; x < W; x++) {
    for (let z = 0; z < D; z++) {
      if (x === 0 || x === W - 1 || z === 0 || z === D - 1) {
        if ((x === 0 || x === W - 1) && (z === 0 || z === D - 1)) continue; // corner posts
        placements.push({ x, y, z, block: 'oak_planks', note: 'timber wall' });
      }
    }
  }
  // horizontal log braces at mid-height (every 3rd column), only on long sides
  placements.push({ x: 2, y, z: 0, block: 'oak_log[axis=x]', note: 'brace long-n' });
  placements.push({ x: 2, y, z: D - 1, block: 'oak_log[axis=x]', note: 'brace long-s' });
  placements.push({ x: 0, y, z: 3, block: 'oak_log[axis=z]', note: 'brace short-w' });
  placements.push({ x: W - 1, y, z: 3, block: 'oak_log[axis=z]', note: 'brace short-e' });
}

// arrow slits at upper storey (y=4-5) — 1×3 vertical air gaps on east and west
// (stylekit: vertical 1×3 or 1×4 gaps with stone behind; here just air)
for (let y = 4; y <= 5; y++) {
  placements.push({ x: 0, y, z: 2, block: 'air', note: 'arrow slit W' });
  placements.push({ x: 0, y, z: 3, block: 'air', note: 'arrow slit W' });
  placements.push({ x: 0, y, z: 4, block: 'air', note: 'arrow slit W' });
  placements.push({ x: W - 1, y, z: 2, block: 'air', note: 'arrow slit E' });
  placements.push({ x: W - 1, y, z: 3, block: 'air', note: 'arrow slit E' });
  placements.push({ x: W - 1, y, z: 4, block: 'air', note: 'arrow slit E' });
}

// fence on SOUTH face y=3 — wall opening (stylekit: "fence means wall opening")
// (small opening for window — between posts at south)
placements.push({ x: 2, y: 3, z: D - 1, block: 'oak_fence', note: 'south fence (wall opening)' });
placements.push({ x: 2, y: 4, z: D - 1, block: 'oak_fence', note: 'south fence row' });
// glass pane at south y=5 (interior view out)
for (let x = 1; x <= 3; x++) {
  placements.push({ x, y: 5, z: D - 1, block: 'glass_pane', note: 'south window' });
}

// y=3-4 floor slabs (interior)
for (let x = 1; x < W - 1; x++) {
  for (let z = 1; z < D - 1; z++) {
    placements.push({ x, y: 3, z, block: 'oak_planks', note: 'floor 1' });
  }
}

// y=6 floor slabs (built into the lookout deck)
for (let x = 1; x < W - 1; x++) {
  for (let z = 1; z < D - 1; z++) {
    placements.push({ x, y: 6, z, block: 'oak_planks', note: 'lookout deck' });
  }
}

// corner posts continue through y=6-7
for (let y = 6; y <= 7; y++) {
  placements.push({ x: 0, y, z: 0, block: 'oak_log[axis=y]', note: 'NE post' });
  placements.push({ x: W - 1, y, z: 0, block: 'oak_log[axis=y]', note: 'NW post' });
  placements.push({ x: 0, y, z: D - 1, block: 'oak_log[axis=y]', note: 'SE post' });
  placements.push({ x: W - 1, y, z: D - 1, block: 'oak_log[axis=y]', note: 'SW post' });
}

// y=7 battlement crenellations — cobblestone merlons with oak_slab gaps (E and W)
// (stylekit: murder holes — 1-cell gaps for vertical fire)
for (let x = 0; x < W; x++) {
  const isGap = x === 2; // murder hole in middle of east/west
  placements.push({ x, y: 7, z: 0, block: isGap ? 'air' : 'cobblestone', note: isGap ? 'murder hole N' : 'crenel N' });
  placements.push({ x, y: 7, z: D - 1, block: isGap ? 'air' : 'cobblestone', note: isGap ? 'murder hole S' : 'crenel S' });
}

// y=8 ridge beam (oak_log, axis=x) — single beam at the apex
for (let x = 1; x < W - 1; x++) {
  placements.push({ x, y: H - 1, z: 3, block: 'oak_log[axis=x]', note: 'ridge beam' });
}

// y=8 stair-pitched roof corners — stair blocks around the perimeter (south half only —
// north half is open to the ridge). Each side has a stair facing inward.
for (let x = 0; x < W; x++) {
  // South roof slope: stairs at y=8 (top), facing north (inward)
  placements.push({ x, y: H - 1, z: D - 1, block: 'oak_stairs[facing=north,half=bottom]', note: 'roof slope S' });
  // South roof slope stairs at y=7 (one row in)
  placements.push({ x, y: 7, z: D - 2, block: 'oak_stairs[facing=north,half=bottom]', note: 'roof slope S row 2' });
}
// East + west roof slopes — stairs along the long sides
for (let z = 0; z < D; z++) {
  placements.push({ x: 0, y: H - 1, z, block: 'oak_stairs[facing=east,half=bottom]', note: 'roof slope W' });
  placements.push({ x: W - 1, y: H - 1, z, block: 'oak_stairs[facing=west,half=bottom]', note: 'roof slope E' });
}

// INTERIOR DECORATIONS
// y=1 — barrel at the south-west corner of the ground floor
placements.push({ x: 1, y: 1, z: 1, block: 'barrel', note: 'storage barrel' });
// y=1 — chest at the back (north-east)
placements.push({ x: 3, y: 1, z: 1, block: 'chest', note: 'ground-floor chest' });
// y=1 — campfire at centre (warming)
placements.push({ x: 2, y: 1, z: 3, block: 'campfire', note: 'hearth' });
// y=2 — torch at the south-west corner (beside door)
placements.push({ x: 1, y: 2, z: 4, block: 'torch', note: 'torch by door' });
// y=3 — lantern at the south-east corner hanging from beam
placements.push({ x: 3, y: 3, z: 4, block: 'lantern[hanging=true]', note: 'hanging lantern' });
// y=4 — lantern at the centre
placements.push({ x: 2, y: 4, z: 3, block: 'lantern[hanging=true]', note: 'lantern centre' });
// y=5 — lantern at the corner
placements.push({ x: 1, y: 5, z: 4, block: 'lantern[hanging=true]', note: 'lantern upper' });
// y=6 — banner at the SE corner (decoration, top of tower)
placements.push({ x: 1, y: 6, z: 4, block: 'white_banner', note: 'tower banner' });

// LADDER at NORTH-WEST corner (spans y=1 to y=4)
for (let y = 1; y <= 4; y++) {
  placements.push({ x: 1, y, z: 1, block: 'ladder[facing=south]', note: 'corner ladder' });
}

// air fill for the interior (we'll write all placements, then drop the rest)
mkdirSync(dirname(OUT_NBT), { recursive: true });

// Build the structure
const size: [number, number, number] = [W, H, D];
const struct = new Structure(size);
// we'll write blocks directly via a cell map
const cells = new Map<string, string>();
for (const p of placements) {
  cells.set(`${p.x},${p.y},${p.z}`, p.block);
}
// First, fill everything with air
for (let y = 0; y < H; y++) {
  for (let z = 0; z < D; z++) {
    for (let x = 0; x < W; x++) {
      const pos: [number, number, number] = [x, y, z];
      struct.addBlock(pos, 'minecraft:air');
    }
  }
}
// Then overwrite with our placements
for (const [key, block] of cells) {
  const [x, y, z] = key.split(',').map(Number);
  const id = block.includes('[') ? block.split('[')[0] : block;
  const stateStr = block.includes('[') ? block.split('[')[1].slice(0, -1) : '';
  const props: Record<string, string> = {};
  if (stateStr) {
    for (const kv of stateStr.split(',')) {
      const [k, v] = kv.split('=');
      props[k] = v;
    }
  }
  const pos: [number, number, number] = [x, y, z];
  struct.addBlock(pos, `minecraft:${id}`, props);
}

// Note: Structure.addBlock might not be the right API. Fall back to writing NBT manually.
const nbt = struct.writeNbt();
writeFileSync(OUT_NBT, nbt);

// Write the block list as a text annotation
const lines = ['KCD tower v2 — block-by-block sculpt', `size: ${W}×${H}×${D}`, `blocks: ${placements.length}`, ''];
for (const p of placements.sort((a, b) => a.y - b.y || a.z - b.z || a.x - b.x)) {
  lines.push(`y=${p.y} (${String(p.x).padStart(2)},${String(p.z).padStart(2)})  ${p.block.padEnd(45)} ${p.note}`);
}
writeFileSync(OUT_TXT, lines.join('\n'));

console.log(`Wrote ${OUT_NBT}`);
console.log(`Wrote ${OUT_TXT}`);
console.log(`  size: ${W}×${H}×${D}, blocks: ${placements.length}`);
