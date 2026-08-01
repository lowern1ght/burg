/**
 * Plan validation — structural rules + engine-level guard rules.
 *
 * Two layers:
 *
 *   1. Structural — check the plan shape itself (footprint is a 2-tuple of
 *      positive integers, every floor's range is contiguous and within
 *      [0, height-1], every device carries its kind's required fields).
 *
 *   2. Compliance — semantic rules that don't need a structure: floors are
 *      contiguous (no gaps), footprint is constant across floors (ADR-0001
 *      says the box stays the same — only the building inside spreads), and
 *      materials "get heavier as we go up" (the corpus measured in
 *      `docs/05-craft/STYLE.md`).
 *
 * Guard rules (floating roof, slab rider, hanging roof, cantilever, orphan
 * fence) require a generated structure to test, so they live in `generate.ts`
 * where the Structure is available. This file produces the structural +
 * compliance result; `generate.ts` augments it with the guard result.
 */

import type {
  ComplianceRule,
  Device,
  Floor,
  Plan,
  ValidationError,
  ValidationResult,
  ValidationWarning,
} from './types';

const ALL_GUARD_RULES = new Set([
  'no_floating_roof',
  'no_slab_rider',
  'no_hanging_roof',
  'no_cantilever',
  'no_orphan_fence',
]);

const ALL_COMPLIANCE_RULES = new Set<ComplianceRule>([
  'constant_footprint',
  'material_ladder',
  'contiguous_floors',
]);

/** Material "hardness" — higher number is harder. The corpus measures
 *  timber walls with cobble ground and stone upper storeys; a plan whose
 *  top floor is `oak_planks` and ground floor is `stone_bricks` reads wrong.
 *  The ladder check warns (not errors) when a higher floor is softer. */
const HARDNESS: Record<string, number> = {
  oak_planks: 1,
  oak_log: 1,
  oak_slab: 1,
  oak_stairs: 1,
  oak_fence: 1,
  oak_leaves: 1,
  stripped_oak_log: 1,
  glass_pane: 1,
  iron_bars: 1,
  bookshelf: 1,
  chiseled_bookshelf: 1,
  bamboo_block: 1,
  bamboo_planks: 1,
  bamboo_mosaic: 1,
  moss_block: 1,
  moss_carpet: 1,
  hanging_roots: 1,
  white_wool: 1,
  yellow_wool: 1,
  red_wool: 1,
  brown_wool: 1,
  lime_wool: 1,
  composter: 1,
  barrel: 1,
  crafting_table: 1,
  cartography_table: 1,
  fletching_table: 1,
  smithing_table: 1,
  loom: 1,
  jukebox: 1,
  note_block: 1,
  bell: 1,
  flower_pot: 1,
  candle: 0,
  torch: 0,
  soul_torch: 0,
  lantern: 0,
  soul_lantern: 0,
  hay_block: 1,
  dirt: 0,
  coarse_dirt: 0,
  dirt_path: 0,
  grass_block: 0,
  farmland: 0,
  mud: 0,
  podzol: 0,
  rooted_dirt: 0,
  smooth_stone: 3,
  white_terracotta: 1,
  cobblestone: 2,
  mossy_cobblestone: 2,
  cobblestone_slab: 2,
  cobblestone_stairs: 2,
  cobblestone_wall: 2,
  stone: 3,
  stone_slab: 3,
  stone_stairs: 3,
  stone_bricks: 4,
  stone_brick_slab: 4,
  stone_brick_stairs: 4,
  stone_brick_wall: 4,
  andesite: 4,
};

export function validatePlan(plan: Plan): ValidationResult {
  const errors: ValidationError[] = [];
  const warnings: ValidationWarning[] = [];

  validateStructure(plan, errors);
  validateFloors(plan, errors);
  validateDevices(plan, errors);
  validateRules(plan, errors);

  for (const rule of plan.rules.compliance) {
    if (!ALL_COMPLIANCE_RULES.has(rule)) {
      errors.push({ rule, message: `unknown compliance rule "${rule}"` });
    }
  }

  if (plan.rules.compliance.includes('contiguous_floors')) {
    checkContiguousFloors(plan, errors);
  }
  if (plan.rules.compliance.includes('constant_footprint')) {
    // Footprint is constant by construction (every plan has one footprint).
    // The rule's meaning is "do not let a future extension drop it"; check
    // it's present and rectangular rather than re-stating the obvious.
    if (plan.structure.footprint.length !== 2) {
      errors.push({
        rule: 'constant_footprint',
        message: 'footprint must be exactly two integers [width, depth]',
      });
    }
  }
  if (plan.rules.compliance.includes('material_ladder')) {
    checkMaterialLadder(plan, warnings);
  }

  return { ok: errors.length === 0, errors, warnings };
}

// ── structural checks ──────────────────────────────────────────────────

function validateStructure(plan: Plan, errors: ValidationError[]): void {
  const [w, d] = plan.structure.footprint;
  if (!Number.isInteger(w) || !Number.isInteger(d) || w < 1 || d < 1) {
    errors.push({
      rule: 'structure.footprint',
      message: `footprint [width, depth] must be positive integers, got [${w}, ${d}]`,
    });
  }
  if (!Number.isInteger(plan.structure.height) || plan.structure.height < 1) {
    errors.push({
      rule: 'structure.height',
      message: `height must be a positive integer, got ${plan.structure.height}`,
    });
  }
  const [ox, oy, oz] = plan.structure.origin;
  if (!Number.isInteger(ox) || !Number.isInteger(oy) || !Number.isInteger(oz)) {
    errors.push({
      rule: 'structure.origin',
      message: `origin must be three integers, got [${ox}, ${oy}, ${oz}]`,
    });
  }
}

function validateFloors(plan: Plan, errors: ValidationError[]): void {
  if (plan.floors.length === 0) {
    errors.push({ rule: 'floors', message: 'at least one floor is required' });
    return;
  }
  const max = plan.structure.height - 1;
  const sorted = [...plan.floors].sort((a, b) => a.range[0] - b.range[0]);
  for (let i = 0; i < sorted.length; i++) {
    const f = sorted[i];
    const [lo, hi] = f.range;
    if (!Number.isInteger(lo) || !Number.isInteger(hi) || lo < 0 || hi > max) {
      errors.push({
        rule: 'floors.range',
        message: `floor "${f.layout}" range [${lo}, ${hi}] outside [0, ${max}]`,
      });
    }
    if (lo > hi) {
      errors.push({
        rule: 'floors.range',
        message: `floor "${f.layout}" has lo > hi (${lo} > ${hi})`,
      });
    }
    if (HARDNESS[f.material] === undefined) {
      errors.push({
        rule: 'floors.material',
        message: `unknown material "${f.material}" on floor "${f.layout}"`,
      });
    }
    if (i > 0) {
      const prev = sorted[i - 1];
      if (prev.range[1] + 1 !== f.range[0]) {
        errors.push({
          rule: 'floors.contiguous',
          message: `floor "${f.layout}" starts at ${f.range[0]} but previous floor ended at ${prev.range[1]}`,
        });
      }
    }
  }
  if (sorted.length > 0) {
    if (sorted[0].range[0] !== 0) {
      errors.push({
        rule: 'floors.coverage',
        message: `first floor must start at 0, got ${sorted[0].range[0]}`,
      });
    }
    if (sorted[sorted.length - 1].range[1] !== max) {
      errors.push({
        rule: 'floors.coverage',
        message: `last floor must end at ${max}, got ${sorted[sorted.length - 1].range[1]}`,
      });
    }
  }
}

function validateDevices(plan: Plan, errors: ValidationError[]): void {
  for (const d of plan.devices) {
    validateDevice(d, plan, errors);
  }
}

function validateDevice(
  d: Device,
  plan: Plan,
  errors: ValidationError[],
): void {
  const [w, depth] = plan.structure.footprint;
  const max = plan.structure.height;
  const inBox = (x: number, z: number): boolean =>
    Number.isInteger(x) && Number.isInteger(z) && x >= 0 && x < w && z >= 0 && z < depth;

  switch (d.kind) {
    case 'door':
      if (d.floor < 0 || d.floor >= max) {
        errors.push({
          rule: 'device.door.floor',
          message: `door on floor ${d.floor}, must be in [0, ${max - 1}]`,
        });
      }
      break;
    case 'window':
      if (d.floor < 0 || d.floor >= max) {
        errors.push({
          rule: 'device.window.floor',
          message: `window on floor ${d.floor}, must be in [0, ${max - 1}]`,
        });
      }
      if (d.width !== undefined && d.width < 1) {
        errors.push({
          rule: 'device.window.width',
          message: `window width must be ≥ 1, got ${d.width}`,
        });
      }
      break;
    case 'torch': {
      const [x, y, z] = d.pos;
      if (!inBox(x, z)) {
        errors.push({
          rule: 'device.torch.pos',
          message: `torch pos [${x}, ${y}, ${z}] outside footprint`,
          pos: d.pos,
        });
      }
      if (y < 1 || y >= max) {
        errors.push({
          rule: 'device.torch.pos',
          message: `torch pos [${x}, ${y}, ${z}] y outside [1, ${max - 1}]`,
          pos: d.pos,
        });
      }
      break;
    }
    case 'ladder': {
      const [x, y, z] = d.pos;
      if (!inBox(x, z)) {
        errors.push({
          rule: 'device.ladder.pos',
          message: `ladder pos [${x}, ${y}, ${z}] outside footprint`,
          pos: d.pos,
        });
      }
      if (y < 1 || y >= max) {
        errors.push({
          rule: 'device.ladder.pos',
          message: `ladder pos [${x}, ${y}, ${z}] y outside [1, ${max - 1}]`,
          pos: d.pos,
        });
      }
      if (d.side !== 'north' && d.side !== 'south' && d.side !== 'east' && d.side !== 'west') {
        errors.push({
          rule: 'device.ladder.side',
          message: `ladder side must be a compass direction, got ${String(d.side)}`,
        });
      }
      break;
    }
    case 'lever': {
      const [x, y, z] = d.pos;
      if (!inBox(x, z)) {
        errors.push({
          rule: 'device.lever.pos',
          message: `lever pos [${x}, ${y}, ${z}] outside footprint`,
          pos: d.pos,
        });
      }
      if (y < 1 || y >= max) {
        errors.push({
          rule: 'device.lever.pos',
          message: `lever pos [${x}, ${y}, ${z}] y outside [1, ${max - 1}]`,
          pos: d.pos,
        });
      }
      break;
    }
    case 'flower_pot': {
      const [x, y, z] = d.pos;
      if (!inBox(x, z)) {
        errors.push({
          rule: 'device.flower_pot.pos',
          message: `flower_pot pos [${x}, ${y}, ${z}] outside footprint`,
          pos: d.pos,
        });
      }
      if (y < 0 || y >= max) {
        errors.push({
          rule: 'device.flower_pot.pos',
          message: `flower_pot pos [${x}, ${y}, ${z}] y outside [0, ${max - 1}]`,
          pos: d.pos,
        });
      }
      break;
    }
    case 'bed': {
      if (d.floor < 1 || d.floor >= max) {
        errors.push({
          rule: 'device.bed.floor',
          message: `bed on floor ${d.floor}, must be in [1, ${max - 1}]`,
        });
      }
      break;
    }
    case 'chest': {
      if (d.floor < 1 || d.floor >= max) {
        errors.push({
          rule: 'device.chest.floor',
          message: `chest on floor ${d.floor}, must be in [1, ${max - 1}]`,
        });
      }
      break;
    }
    case 'barrel': {
      if (d.floor < 1 || d.floor >= max) {
        errors.push({
          rule: 'device.barrel.floor',
          message: `barrel on floor ${d.floor}, must be in [1, ${max - 1}]`,
        });
      }
      break;
    }
    case 'candle': {
      const [x, y, z] = d.pos;
      if (!inBox(x, z)) {
        errors.push({
          rule: 'device.candle.pos',
          message: `candle pos [${x}, ${y}, ${z}] outside footprint`,
          pos: d.pos,
        });
      }
      if (y < 1 || y >= max) {
        errors.push({
          rule: 'device.candle.pos',
          message: `candle pos [${x}, ${y}, ${z}] y outside [1, ${max - 1}]`,
          pos: d.pos,
        });
      }
      break;
    }
    case 'campfire': {
      const [x, y, z] = d.pos;
      if (!inBox(x, z)) {
        errors.push({
          rule: 'device.campfire.pos',
          message: `campfire pos [${x}, ${y}, ${z}] outside footprint`,
          pos: d.pos,
        });
      }
      if (y < 1 || y >= max) {
        errors.push({
          rule: 'device.campfire.pos',
          message: `campfire pos [${x}, ${y}, ${z}] y outside [1, ${max - 1}]`,
          pos: d.pos,
        });
      }
      break;
    }
    case 'furnace': {
      if (d.floor < 1 || d.floor >= max) {
        errors.push({
          rule: 'device.furnace.floor',
          message: `furnace on floor ${d.floor}, must be in [1, ${max - 1}]`,
        });
      }
      break;
    }
    case 'external_stair':
      if (d.start_y >= d.end_y) {
        errors.push({
          rule: 'device.external_stair',
          message: `external_stair start_y ${d.start_y} ≥ end_y ${d.end_y}`,
        });
      }
      if (d.end_y > max) {
        errors.push({
          rule: 'device.external_stair',
          message: `external_stair end_y ${d.end_y} > height ${max}`,
        });
      }
      break;
    case 'chimney': {
      const [ox, oz] = d.offset;
      if (!inBox(ox, oz)) {
        errors.push({
          rule: 'device.chimney.offset',
          message: `chimney offset [${ox}, ${oz}] outside footprint`,
        });
      }
      if (d.from_floor >= d.to_floor) {
        errors.push({
          rule: 'device.chimney',
          message: `chimney from_floor ${d.from_floor} ≥ to_floor ${d.to_floor}`,
        });
      }
      if (d.to_floor > max) {
        errors.push({
          rule: 'device.chimney',
          message: `chimney to_floor ${d.to_floor} > height ${max}`,
        });
      }
      break;
    }
    case 'crenellation':
      if (d.top_y < 1 || d.top_y > max) {
        errors.push({
          rule: 'device.crenellation',
          message: `crenellation top_y ${d.top_y} outside [1, ${max}]`,
        });
      }
      if (d.spacing < 1) {
        errors.push({
          rule: 'device.crenellation',
          message: `crenellation spacing must be ≥ 1, got ${d.spacing}`,
        });
      }
      break;
    case 'fence_post': {
      const [x, y, z] = d.pos;
      if (!inBox(x, z)) {
        errors.push({
          rule: 'device.fence_post.pos',
          message: `fence_post pos [${x}, ${y}, ${z}] outside footprint`,
          pos: d.pos,
        });
      }
      if (d.height < 1) {
        errors.push({
          rule: 'device.fence_post',
          message: `fence_post height must be ≥ 1, got ${d.height}`,
        });
      }
      break;
    }
    case 'corner_post': {
      const [x, y, z] = d.pos;
      if (!inBox(x, z)) {
        errors.push({
          rule: 'device.corner_post.pos',
          message: `corner_post pos [${x}, ${y}, ${z}] outside footprint`,
          pos: d.pos,
        });
      }
      if (d.height < 1) {
        errors.push({
          rule: 'device.corner_post',
          message: `corner_post height must be ≥ 1, got ${d.height}`,
        });
      }
      break;
    }
  }
}

function validateRules(plan: Plan, errors: ValidationError[]): void {
  for (const r of plan.rules.guard) {
    if (!ALL_GUARD_RULES.has(r)) {
      errors.push({ rule: `rules.guard.${r}`, message: `unknown guard rule "${r}"` });
    }
  }
}

// ── compliance checks ─────────────────────────────────────────────────

function checkContiguousFloors(plan: Plan, errors: ValidationError[]): void {
  // Already covered in validateFloors via per-floor adjacency check.
  // This is a no-op re-statement so the rule shows up explicitly in the
  // report — no extra work needed.
  void plan;
  void errors;
}

function checkMaterialLadder(plan: Plan, warnings: ValidationWarning[]): void {
  const sorted: Floor[] = [...plan.floors].sort((a, b) => a.range[0] - b.range[0]);
  for (let i = 1; i < sorted.length; i++) {
    const prev = sorted[i - 1];
    const cur = sorted[i];
    const p = HARDNESS[prev.material] ?? 0;
    const c = HARDNESS[cur.material] ?? 0;
    if (c < p) {
      warnings.push({
        rule: 'material_ladder',
        message: `floor "${cur.layout}" (${cur.material}, hardness ${c}) is softer than the floor below it "${prev.layout}" (${prev.material}, hardness ${p})`,
      });
    }
  }
}
