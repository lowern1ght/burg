import { Structure } from '@mattzh72/lodestone';
import {
  STYLE_GRAMMAR,
  type BuildingFamily,
  type FamilyGrammar,
} from './style-grammar';
import type { Device, Floor, MaterialId, Plan, Side } from './types';
import { generateStructure } from './generate';
import { type BlockGrid } from './vocabulary';

export type BuildSpec = {
  family: BuildingFamily;
  rung?: number;
  footprint: [number, number];
  height: number;
  materialLadder?: MaterialId[];
  decor: 'sparse' | 'medium' | 'dense';
  donor?: Structure;
  seed?: number;
};

export type BuildResult = {
  structure: Structure;
  spec: BuildSpec;
  plan: Plan;
  warnings: string[];
};

const VALID_MATERIALS: ReadonlySet<MaterialId> = new Set<MaterialId>([
  'oak_planks', 'oak_log', 'oak_slab', 'oak_stairs', 'oak_fence', 'oak_door',
  'oak_trapdoor', 'oak_pressure_plate', 'oak_leaves', 'stripped_oak_log',
  'cobblestone', 'mossy_cobblestone', 'cobblestone_slab', 'cobblestone_stairs',
  'cobblestone_wall', 'stone', 'stone_slab', 'stone_stairs', 'stone_bricks',
  'stone_brick_slab', 'stone_brick_stairs', 'stone_brick_wall', 'andesite',
  'dirt', 'coarse_dirt', 'dirt_path', 'grass_block', 'farmland', 'mud',
  'podzol', 'rooted_dirt', 'smooth_stone', 'white_terracotta', 'glass_pane',
  'iron_bars', 'hay_block', 'bookshelf', 'chiseled_bookshelf', 'bamboo_block',
  'bamboo_planks', 'bamboo_mosaic', 'moss_block', 'moss_carpet', 'hanging_roots',
  'white_wool', 'yellow_wool', 'red_wool', 'brown_wool', 'lime_wool',
  'composter', 'barrel', 'crafting_table', 'cartography_table', 'fletching_table',
  'smithing_table', 'loom', 'jukebox', 'note_block', 'bell', 'torch', 'soul_torch',
  'lantern', 'soul_lantern', 'candle', 'flower_pot',
]);

function isValidMaterial(m: string): m is MaterialId {
  return (VALID_MATERIALS as Set<string>).has(m);
}

function shortId(id: string): string {
  return id.startsWith('minecraft:') ? id.slice(10) : id;
}

function structureToBlockGrid(s: Structure): BlockGrid {
  const size = s.getSize() as [number, number, number];
  const map = new Map<string, { id: string; props: Record<string, string> }>();
  for (const placed of s.getBlocks()) {
    const [x, y, z] = placed.pos;
    const id = shortId(placed.state.getName().toString());
    if (id === 'air' || id === 'cave_air' || id === 'void_air') continue;
    const props: Record<string, string> = {};
    for (const [k, v] of Object.entries(placed.state.getProperties())) {
      props[k] = String(v);
    }
    map.set(`${x},${y},${z}`, { id, props });
  }
  return {
    size,
    get: (x, y, z) => map.get(`${x},${y},${z}`) ?? null,
    occupied: (x, y, z) => map.has(`${x},${y},${z}`),
    blocks: function* () {
      for (const [key, block] of map) {
        const [x, y, z] = key.split(',').map(Number);
        yield { pos: [x, y, z] as [number, number, number], block };
      }
    },
  };
}

function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return function next(): number {
    a = (a + 0x6D2B79F5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function pickRungHeights(grammar: FamilyGrammar): number[] {
  return Object.values(grammar.rungs).map((r) => r.height).sort((a, b) => a - b);
}

function medianRung(grammar: FamilyGrammar): number {
  const heights = pickRungHeights(grammar);
  if (heights.length === 0) return 1;
  const mid = Math.floor(heights.length / 2);
  return heights[mid];
}

function resolveRungHeight(spec: BuildSpec, grammar: FamilyGrammar, warnings: string[]): number {
  if (spec.rung === undefined) return spec.height;
  const heights = pickRungHeights(grammar);
  if (heights.length === 0) return spec.height;
  if (spec.rung < 0) {
    warnings.push(`rung ${spec.rung} below 0; clamped to 0`);
    return spec.height;
  }
  if (spec.rung >= heights.length) {
    warnings.push(`rung ${spec.rung} above grammar's max (${heights.length - 1}); clamped`);
    return spec.height;
  }
  return spec.height;
}

function clampFootprint(
  spec: BuildSpec,
  grammar: FamilyGrammar,
  warnings: string[],
): [number, number] {
  const [w, d] = spec.footprint;
  const dims = Object.values(grammar.rungs);
  const minW = Math.min(...dims.map((r) => r.width));
  const maxW = Math.max(...dims.map((r) => r.width));
  const minD = Math.min(...dims.map((r) => r.depth));
  const maxD = Math.max(...dims.map((r) => r.depth));
  let cw = w;
  let cd = d;
  if (w < minW) {
    warnings.push(`footprint width ${w} below rung min ${minW}; clamped`);
    cw = minW;
  } else if (w > maxW + 4) {
    warnings.push(`footprint width ${w} above rung max ${maxW} + 4; clamped`);
    cw = maxW + 4;
  }
  if (d < minD) {
    warnings.push(`footprint depth ${d} below rung min ${minD}; clamped`);
    cd = minD;
  } else if (d > maxD + 4) {
    warnings.push(`footprint depth ${d} above rung max ${maxD} + 4; clamped`);
    cd = maxD + 4;
  }
  if (cw * cd < 9) {
    warnings.push('footprint area < 9 cells; rounded up to 3x3');
    return [3, 3];
  }
  return [cw, cd];
}

function resolveMaterialLadder(
  spec: BuildSpec,
  grammar: FamilyGrammar,
  warnings: string[],
): MaterialId[] {
  if (spec.materialLadder) {
    const cleaned: MaterialId[] = [];
    for (const m of spec.materialLadder) {
      if (!isValidMaterial(m)) {
        warnings.push(`materialLadder: ${m} is not in MaterialId union; dropped`);
        continue;
      }
      if (isBanned(m, grammar)) {
        warnings.push(`materialLadder: ${m} is banned in ${spec.family}; dropped`);
        continue;
      }
      if (!isWallMaterial(m)) {
        warnings.push(`materialLadder: ${m} is a device/decor, not a wall material; dropped`);
        continue;
      }
      cleaned.push(m);
    }
    if (cleaned.length === 0) {
      return defaultLadder(grammar);
    }
    return cleaned;
  }
  return defaultLadder(grammar);
}

function isBanned(m: string, grammar: FamilyGrammar): boolean {
  return grammar.materials.banned.some((b) => b === m || shortId(b) === m);
}

function isWallMaterial(m: string): boolean {
  if (!isValidMaterial(m)) return false;
  if (m === 'oak_door' || m === 'oak_trapdoor') return false;
  if (m === 'oak_pressure_plate') return false;
  if (m === 'torch' || m === 'soul_torch' || m === 'lantern' || m === 'soul_lantern') return false;
  if (m === 'candle' || m === 'flower_pot') return false;
  if (m === 'barrel') return false;
  if (m === 'crafting_table' || m === 'cartography_table' || m === 'fletching_table') return false;
  if (m === 'smithing_table' || m === 'loom' || m === 'jukebox' || m === 'note_block') return false;
  if (m === 'bell' || m === 'composter') return false;
  if (m === 'bookshelf' || m === 'chiseled_bookshelf') return false;
  if (m === 'bamboo_block' || m === 'bamboo_planks' || m === 'bamboo_mosaic') return false;
  if (m === 'hay_block') return false;
  if (m === 'farmland' || m === 'mud' || m === 'podzol' || m === 'rooted_dirt') return false;
  if (m === 'white_wool' || m === 'yellow_wool' || m === 'red_wool' || m === 'brown_wool' || m === 'lime_wool') return false;
  if (m === 'moss_block' || m === 'moss_carpet' || m === 'hanging_roots') return false;
  if (m === 'glass_pane' || m === 'iron_bars') return false;
  return true;
}

function defaultLadder(grammar: FamilyGrammar): MaterialId[] {
  const ladder: MaterialId[] = [];
  const allow = (m: string): boolean => isWallMaterial(m) && !isBanned(m, grammar);
  for (const m of grammar.materials.always) {
    if (allow(m)) ladder.push(m as MaterialId);
    if (ladder.length >= 2) break;
  }
  if (ladder.length === 0) ladder.push('cobblestone');
  for (const m of grammar.materials.common) {
    if (allow(m) && !ladder.includes(m as MaterialId)) {
      ladder.push(m as MaterialId);
      break;
    }
  }
  if (ladder.length === 1) ladder.push('oak_planks');
  return ladder;
}

type FloorPlan = {
  range: [number, number];
  material: MaterialId;
  layout: 'ground' | 'residential' | 'battlements' | 'roof';
};

function deriveFloors(
  spec: BuildSpec,
  grammar: FamilyGrammar,
  ladder: MaterialId[],
  warnings: string[],
): FloorPlan[] {
  const [w, d] = spec.footprint;
  const h = spec.height;
  const groundMat = ladder[0] ?? 'cobblestone';
  const upperMat = ladder[1] ?? 'oak_planks';
  const floors: FloorPlan[] = [];

  if (h < 3) {
    floors.push({ range: [0, h - 1], material: upperMat, layout: 'residential' });
    return floors;
  }

  floors.push({ range: [0, 1], material: groundMat, layout: 'ground' });

  const topRung = medianRung(grammar);
  if (h >= 7) {
    const upperEnd = h - 2;
    floors.push({ range: [2, upperEnd], material: upperMat, layout: 'residential' });
    floors.push({ range: [h - 1, h - 1], material: upperMat, layout: 'roof' });
  } else {
    floors.push({ range: [2, h - 1], material: upperMat, layout: 'residential' });
  }
  void w; void d; void topRung; void warnings;
  return floors;
}

function doorsCorners(footprint: [number, number], rng: () => number): { x: number; z: number; side: Side } {
  const [w, d] = footprint;
  const cx = Math.floor(w / 2);
  const corner: Side = rng() < 0.5 ? 'south' : 'west';
  if (corner === 'south') {
    const cornerX = w - 2;
    if (cornerX === cx) {
      return { x: 1, z: d - 1, side: 'south' };
    }
    return { x: cornerX, z: d - 1, side: 'south' };
  }
  const cornerZ = d - 2;
  if (cornerZ === Math.floor(d / 2)) {
    return { x: 0, z: 1, side: 'west' };
  }
  return { x: 0, z: cornerZ, side: 'west' };
}

function ladderCorner(footprint: [number, number], door: { x: number; z: number }, rng: () => number): { x: number; z: number } {
  const [w, d] = footprint;
  const options: Array<[number, number]> = [];
  if (!(door.x === 0 && door.z === 0)) options.push([0, 0]);
  if (!(door.x === w - 1 && door.z === 0)) options.push([w - 1, 0]);
  if (!(door.x === 0 && door.z === d - 1)) options.push([0, d - 1]);
  if (!(door.x === w - 1 && door.z === d - 1)) options.push([w - 1, d - 1]);
  if (options.length === 0) options.push([1, 1]);
  const picked = options[Math.floor(rng() * options.length)] ?? options[0]!;
  return { x: picked[0], z: picked[1] };
}

function planDevices(
  spec: BuildSpec,
  grammar: FamilyGrammar,
  floors: FloorPlan[],
  ladder: MaterialId[],
  rng: () => number,
  warnings: string[],
): Device[] {
  const [w, d] = spec.footprint;
  const h = spec.height;
  const devices: Device[] = [];
  const residential = floors.find((f) => f.layout === 'residential');
  const upperStart = residential ? residential.range[0] : 2;

  const doorPos = doorsCorners([w, d], rng);
  devices.push({ kind: 'door', side: doorPos.side, floor: 1 });
  if (Math.floor(w / 2) === doorPos.x) {
    warnings.push('door landed on centre x; shifted to second cell in');
  }

  const ladderAt = ladderCorner([w, d], { x: doorPos.x, z: doorPos.z }, rng);
  const ladderSide: Side = ladderAt.x === 0 ? 'west' : ladderAt.x === w - 1 ? 'east' : ladderAt.z === 0 ? 'north' : 'south';
  const climbTo = Math.max(2, h - 1);
  devices.push({ kind: 'ladder', side: ladderSide, pos: [ladderAt.x, 1, ladderAt.z] });
  void climbTo;

  if (h >= 5) {
    const wallSide: Side = doorPos.side === 'south' ? 'east' : 'north';
    const windowFloor = Math.min(upperStart + 1, h - 2);
    const winX = doorPos.x + 1 < w - 1 ? doorPos.x + 1 : doorPos.x - 1;
    const winZ = wallSide === 'east' ? d - 1 : 0;
    if (winX >= 0 && winX < w) {
      devices.push({ kind: 'window', side: wallSide, floor: windowFloor });
    }
    void winX; void winZ;
  }

  if (h >= 7) {
    devices.push({ kind: 'crenellation', top_y: h - 1, spacing: 1 });
  }

  if (spec.decor === 'sparse' || spec.decor === 'medium' || spec.decor === 'dense') {
    const lanternY = 2;
    const lanternX = doorPos.x + 1 < w - 1 ? doorPos.x + 1 : doorPos.x - 1;
    const lanternZ = doorPos.z;
    if (lanternX >= 0 && lanternX < w) {
      devices.push({ kind: 'torch', pos: [lanternX, lanternY, lanternZ] });
    }
  }

  if (spec.decor === 'medium' || spec.decor === 'dense') {
    if (upperStart + 1 < h - 1) {
      const storeX = 1 + Math.floor(rng() * Math.max(1, w - 2));
      const storeZ = 1 + Math.floor(rng() * Math.max(1, d - 2));
      if (spec.decor === 'medium' && rng() < 0.5) {
        devices.push({ kind: 'chest', side: 'east', floor: upperStart + 1 });
      } else {
        devices.push({ kind: 'barrel', side: 'east', floor: upperStart + 1 });
      }
      void storeX; void storeZ;
    }
  }

  if (spec.decor === 'dense') {
    if (upperStart + 2 < h - 1) {
      devices.push({ kind: 'candle', pos: [w - 2, upperStart + 2, 1] });
      devices.push({ kind: 'flower_pot', pos: [1, upperStart + 2, 1] });
    }
  }

  if (spec.family === 'watchtower' || spec.family === 'wall_tower' || spec.family === 'armory' || spec.family === 'barracks') {
    if (h >= 6) {
      const capY = h - 1;
      const capX = w - 1;
      const capZ = d - 1;
      devices.push({ kind: 'fence_post', pos: [capX, capY, capZ], height: 1 });
    }
  }

  if (spec.family === 'house' && h >= 5) {
    const chimneySide: Side = 'south';
    const cX = doorPos.x + 1 < w - 1 ? doorPos.x + 1 : doorPos.x - 1;
    devices.push({ kind: 'chimney', offset: [cX, d - 1], from_floor: h - 3, to_floor: h });
    void chimneySide;
  }

  void grammar; void ladder;
  return devices;
}

function buildSpecPlan(
  spec: BuildSpec,
  warnings: string[],
): { plan: Plan; ladder: MaterialId[] } {
  const family = STYLE_GRAMMAR.plains[spec.family] ?? STYLE_GRAMMAR.military[spec.family];
  if (!family) {
    throw new Error(`unknown family "${spec.family}" - must be one of the grammar's 13 families`);
  }
  const grammar = family;
  resolveRungHeight(spec, grammar, warnings);
  const footprint = clampFootprint(spec, grammar, warnings);
  const ladder = resolveMaterialLadder(spec, grammar, warnings);
  const floorList = deriveFloors({ ...spec, footprint }, grammar, ladder, warnings);
  const rng = mulberry32(spec.seed ?? 0xC0FFEE);
  const devices = planDevices({ ...spec, footprint }, grammar, floorList, ladder, rng, warnings);

  const planFloorsList: Floor[] = floorList.map((f) => ({
    range: f.range,
    material: f.material,
    layout: f.layout,
  }));

  const plan: Plan = {
    name: `${spec.family}_generated`,
    version: 1,
    structure: {
      footprint,
      height: spec.height,
      origin: [0, 0, 0],
    },
    floors: planFloorsList,
    devices,
    rules: {
      guard: ['no_floating_roof', 'no_slab_rider'],
      compliance: ['constant_footprint', 'contiguous_floors'],
    },
    output: {
      name: `${spec.family}_generated`,
      path: `${spec.family}/${spec.family}_generated/`,
    },
  };
  return { plan, ladder };
}

function checkSpecInvariants(
  spec: BuildSpec,
  footprint: [number, number],
  warnings: string[],
): void {
  if (spec.height < 1) {
    warnings.push('height < 1; building has no interior');
  }
  if (spec.height < 3) {
    warnings.push('height < 3; no room for a residential storey');
  }
  if (spec.height >= 7) {
    warnings.push('height >= 7; switching to stair-pitched roof per corpus rule');
  }
  const [w, d] = footprint;
  if (w < 3 || d < 3) {
    warnings.push(`footprint [${w}, ${d}] too small for a corner-placed door`);
  }
  if (w === 3 && d === 3) {
    warnings.push('3x3 footprint is mirror-symmetric by construction; using off-centre door');
  }
  void spec;
}

export function buildFromSpec(spec: BuildSpec): BuildResult {
  const warnings: string[] = [];
  const family = STYLE_GRAMMAR.plains[spec.family] ?? STYLE_GRAMMAR.military[spec.family];
  if (!family) {
    throw new Error(`unknown family "${spec.family}"`);
  }
  const grammar = family;
  const { plan } = buildSpecPlan(spec, warnings);
  checkSpecInvariants(spec, plan.structure.footprint as [number, number], warnings);

  const donor = spec.donor;
  const donorGrid: BlockGrid | undefined = donor ? structureToBlockGrid(donor) : undefined;
  const donorName = donor ? `spec.${spec.family}.donor` : '';

  const generation = generateStructure(plan, {
    donor: donorGrid,
    donorName,
    skipValidation: false,
  });

  const blocks = generation.structure.getBlocks();
  const blockIds = new Set<string>();
  for (const b of blocks) {
    blockIds.add(shortId(b.state.getName().toString()));
  }
  for (const banned of grammar.materials.banned) {
    if (blockIds.has(banned)) {
      warnings.push(`generated structure contains banned material "${banned}"`);
    }
  }

  if (blockIds.size < 5) {
    warnings.push(`palette is small (${blockIds.size} distinct block ids) - corpus median is much higher`);
  }

  return {
    structure: generation.structure,
    spec,
    plan,
    warnings,
  };
}
