/**
 * Plan → Structure generator.
 *
 * The generator turns a build plan into a Lodestone Structure by walking each
 * floor in the plan and emitting a per-floor scaffold (ground apron, perimeter
 * walls, floor slabs, battlements/roof cap) before the device pass layers the
 * named patterns (door, window, torch, bed, furnace, ladder, ...) on top.
 *
 * Style comes from a `Vocabulary` — the patterns the generator knows about,
 * indexed by construction role. When the caller passes a donor `BlockGrid`
 * (e.g. an existing NBT structure), `harvest()` lifts a vocabulary from it:
 * every block state the generator emits is one that already occurs in the
 * corpus, which is what keeps the composed build on-style. Without a donor,
 * the vocabulary is built from the plan's floor materials and a fixed set
 * of corpus fallbacks.
 *
 * The generator is deterministic: two runs of the same plan + vocabulary
 * produce the same structure. Stochastic decoration is the corpus's job, not
 * ours.
 */

import { Structure, BlockPos } from '@mattzh72/lodestone';
import { runChecksOnStructure, type FullReport } from '../engine';
import {
  makeGrid,
  type MutableBlockGrid,
  block as makeBlock,
} from '../engine/test-utils';
import type { Coord } from '../engine/fabric';
import { FabricGuard } from '../engine/fabric';
import type {
  Device,
  Floor,
  GenerationResult,
  MaterialId,
  Plan,
  Side,
  ValidationResult,
} from './types';
import { validatePlan } from './validate';
import {
  harvest as harvestVocabulary,
  pickFromList,
  type BlockGrid,
  type BlockState,
  type Vocabulary,
} from './vocabulary';

/** Optional inputs to `generateStructure`. */
export type GenerationOptions = {
  /**
   * Donor `BlockGrid` whose block states become the generator's vocabulary.
   * When omitted, the generator falls back to plan-driven defaults.
   */
  donor?: BlockGrid;
  /** Name recorded on the harvested vocabulary (e.g. the donor's filename). */
  donorName?: string;
};

export function generateStructure(plan: Plan, options: GenerationOptions = {}): GenerationResult {
  const validation = validatePlan(plan);
  if (!validation.ok) {
    throw new Error(
      `cannot generate structure from invalid plan: ${validation.errors
        .map((e) => e.rule + ': ' + e.message)
        .join('; ')}`,
    );
  }

  const [w, d] = plan.structure.footprint;
  const h = plan.structure.height;
  const grid = makeGrid([w, h, d]);
  const vocab = buildVocabulary(plan, options.donor, options.donorName);

  const guard = new FabricGuard();

  for (const floor of plan.floors) {
    buildFloor(grid, floor, plan, vocab);
  }
  buildCornerPosts(grid, plan, vocab);
  for (const device of plan.devices) {
    buildDevice(grid, device, plan, guard, vocab);
  }

  const size: [number, number, number] = [w, h, d];
  const blocks: Array<{
    pos: [number, number, number];
    state: { id: string; props: Record<string, string> };
  }> = [];
  for (const { pos, block } of grid.blocks()) {
    blocks.push({ pos, state: { id: block.id, props: block.props } });
  }
  const structure = buildStructure(size, blocks);
  return {
    structure,
    blockCount: blocks.length,
    bbox: size,
  };
}

export function checkGenerated(plan: Plan, gen: GenerationResult): FullReport {
  void plan;
  return runChecksOnStructure(gen.structure);
}

export function validate(plan: Plan): ValidationResult {
  return validatePlan(plan);
}

// ── vocabulary ────────────────────────────────────────────────────

/**
 * Build a vocabulary: harvested from `donor` when provided, otherwise built
 * from the plan's floor materials with corpus fallbacks. The plan-driven
 * defaults are the same fallbacks the Python's `harvest()` uses, so the
 * generator is never empty-handed.
 */
function buildVocabulary(plan: Plan, donor?: BlockGrid, donorName?: string): Vocabulary {
  const harvested = donor ? harvestVocabulary(donor, donorName ?? '') : null;
  if (harvested) {
    // Donor is authoritative for single-role items; the plan fills anything
    // the donor left empty so the generator never sees a null corner post.
    return fillFromPlan(harvested, plan);
  }
  return planDrivenVocabulary(plan);
}

/** A vocabulary filled with corpus fallbacks + the plan's floor materials. */
function planDrivenVocabulary(plan: Plan): Vocabulary {
  const top = findTopmostResidential(plan);
  const timber: BlockState = { id: top ?? 'oak_log', properties: {} };
  const stone: BlockState = { id: 'cobblestone', properties: {} };
  const floor: BlockState = { id: 'oak_planks', properties: {} };
  const post: BlockState = { id: 'oak_log', properties: { axis: 'y' } };
  const window: BlockState = {
    id: 'glass_pane',
    properties: { east: 'false', north: 'true', south: 'true', west: 'false', waterlogged: 'false' },
  };
  const fence: BlockState = {
    id: 'oak_fence',
    properties: { east: 'false', north: 'true', south: 'true', west: 'false', waterlogged: 'false' },
  };
  const door: BlockState = {
    id: 'oak_door',
    properties: { half: 'lower', hinge: 'left', facing: 'south', open: 'false', powered: 'false' },
  };
  return {
    donor: plan.name,
    apron: [
      { id: 'grass_block', properties: { snowy: 'false' } },
      { id: 'coarse_dirt', properties: {} },
      { id: 'dirt', properties: {} },
    ],
    floor: [floor],
    stone: [stone, { id: 'mossy_cobblestone', properties: {} }],
    timber: [timber],
    post,
    slab_top: { id: 'oak_slab', properties: { type: 'top', waterlogged: 'false' } },
    slab_bottom: { id: 'oak_slab', properties: { type: 'bottom', waterlogged: 'false' } },
    stone_slab_top: { id: 'cobblestone_slab', properties: { type: 'top', waterlogged: 'false' } },
    window: [window],
    fence,
    crenel: stone,
    stairs: [
      { id: 'oak_stairs', properties: { facing: 'north', half: 'bottom' } },
    ],
    light: [{ id: 'lantern', properties: { hanging: 'false', waterlogged: 'false' } }],
    door_lower: [door],
    door_upper: [{ ...door, properties: { ...door.properties, half: 'upper' } }],
    rail: [fence],
    roof_stairs: [
      { id: 'oak_stairs', properties: { facing: 'north', half: 'bottom' } },
    ],
    beam: [{ id: 'oak_slab', properties: { type: 'bottom', waterlogged: 'false' } }],
    hanging: [{ id: 'lantern', properties: { hanging: 'true', waterlogged: 'false' } }],
    vegetation: [{ id: 'oak_leaves', properties: { persistent: 'true' } }],
    decoration: [{ id: 'barrel', properties: { facing: 'up' } }],
  };
}

/**
 * Fill any null/empty vocabulary role from the plan's floor materials, so a
 * donor that is missing a role (e.g. a 1-storey building with no crenel)
 * still gives the generator something to use.
 */
function fillFromPlan(v: Vocabulary, plan: Plan): Vocabulary {
  const fallback: Vocabulary = planDrivenVocabulary(plan);
  return {
    ...v,
    timber: v.timber.length > 0 ? v.timber : fallback.timber,
    stone: v.stone.length > 0 ? v.stone : fallback.stone,
    floor: v.floor.length > 0 ? v.floor : fallback.floor,
    post: v.post ?? fallback.post,
    fence: v.fence ?? fallback.fence,
    crenel: v.crenel ?? fallback.crenel,
    slab_top: v.slab_top ?? fallback.slab_top,
    slab_bottom: v.slab_bottom ?? fallback.slab_bottom,
    stone_slab_top: v.stone_slab_top ?? fallback.stone_slab_top,
    window: v.window.length > 0 ? v.window : fallback.window,
    door_lower: v.door_lower.length > 0 ? v.door_lower : fallback.door_lower,
    door_upper: v.door_upper.length > 0 ? v.door_upper : fallback.door_upper,
    rail: v.rail.length > 0 ? v.rail : fallback.rail,
    roof_stairs: v.roof_stairs.length > 0 ? v.roof_stairs : fallback.roof_stairs,
    beam: v.beam.length > 0 ? v.beam : fallback.beam,
    light: v.light.length > 0 ? v.light : fallback.light,
    hanging: v.hanging.length > 0 ? v.hanging : fallback.hanging,
    vegetation: v.vegetation.length > 0 ? v.vegetation : fallback.vegetation,
    decoration: v.decoration.length > 0 ? v.decoration : fallback.decoration,
  };
}

function findTopmostResidential(plan: Plan): MaterialId | undefined {
  let hi: MaterialId | undefined;
  for (const f of plan.floors) {
    if (f.layout === 'residential') hi = f.material;
  }
  return hi;
}

// ── per-floor scaffold ───────────────────────────────────────────

function buildFloor(
  grid: MutableBlockGrid,
  floor: Floor,
  plan: Plan,
  vocab: Vocabulary,
): void {
  const [w, d] = plan.structure.footprint;
  const [lo, hi] = floor.range;
  switch (floor.layout) {
    case 'ground':
      buildGround(grid, plan, floor.material);
      return;
    case 'commercial':
      buildCommercial(grid, plan, lo, hi, floor.material);
      return;
    case 'residential':
      buildResidential(grid, plan, lo, hi, floor.material, vocab);
      return;
    case 'battlements':
      buildBattlements(grid, plan, lo, hi, floor.material);
      return;
    case 'roof':
      buildRoof(grid, plan, lo, hi, floor.material);
      return;
  }
  void [w, d];
}

function buildGround(
  grid: MutableBlockGrid,
  plan: Plan,
  _material: MaterialId,
): void {
  const [w, depth] = plan.structure.footprint;
  const h = plan.structure.height;

  for (let x = 0; x < w; x++) {
    for (let z = 0; z < depth; z++) {
      const onEdge = x === 0 || z === 0 || x === w - 1 || z === depth - 1;
      grid.set(x, 0, z, makeBlock(onEdge ? 'coarse_dirt' : 'grass_block'));
    }
  }

  for (let y = 1; y < h; y++) {
    const wallMat = selectGroundWallMaterial(plan, y);
    for (let x = 0; x < w; x++) {
      for (let z = 0; z < depth; z++) {
        const onEdge = x === 0 || z === 0 || x === w - 1 || z === depth - 1;
        if (!onEdge) continue;
        if (cellReservedByDevice(x, y, z, plan)) continue;
        grid.set(x, y, z, makeBlock(wallMat));
      }
    }
  }
}

function selectGroundWallMaterial(plan: Plan, y: number): MaterialId {
  const cover = plan.floors.find((f) => f.range[0] <= y && y <= f.range[1]);
  return cover?.material ?? 'cobblestone';
}

function buildResidential(
  grid: MutableBlockGrid,
  plan: Plan,
  lo: number,
  hi: number,
  material: MaterialId,
  _vocab: Vocabulary,
): void {
  const [w, depth] = plan.structure.footprint;
  for (let y = lo; y <= hi; y++) {
    for (let x = 0; x < w; x++) {
      for (let z = 0; z < depth; z++) {
        const onEdge = x === 0 || z === 0 || x === w - 1 || z === depth - 1;
        if (!onEdge) continue;
        if (cellReservedByDevice(x, y, z, plan)) continue;
        grid.set(x, y, z, makeBlock(material));
      }
    }
  }
  if (lo > 0) {
    for (let x = 0; x < w; x++) {
      for (let z = 0; z < depth; z++) {
        const onEdge = x === 0 || z === 0 || x === w - 1 || z === depth - 1;
        if (onEdge) continue;
        if (cellReservedByDevice(x, lo, z, plan)) continue;
        grid.set(x, lo, z, makeBlock(material));
      }
    }
  }
}

function buildCommercial(
  grid: MutableBlockGrid,
  plan: Plan,
  lo: number,
  hi: number,
  material: MaterialId,
): void {
  const [w, depth] = plan.structure.footprint;
  for (let y = lo; y <= hi; y++) {
    for (let x = 0; x < w; x++) {
      for (let z = 0; z < depth; z++) {
        const onEdge = x === 0 || z === 0 || x === w - 1 || z === depth - 1;
        if (!onEdge) continue;
        if (cellReservedByDevice(x, y, z, plan)) continue;
        if (x === 0 || x === w - 1) continue;
        grid.set(x, y, z, makeBlock(material));
      }
    }
  }
  if (lo > 0) {
    for (let x = 0; x < w; x++) {
      for (let z = 0; z < depth; z++) {
        const onEdge = x === 0 || z === 0 || x === w - 1 || z === depth - 1;
        if (onEdge) continue;
        if (cellReservedByDevice(x, lo, z, plan)) continue;
        grid.set(x, lo, z, makeBlock(material));
      }
    }
  }
}

function buildBattlements(
  grid: MutableBlockGrid,
  plan: Plan,
  lo: number,
  hi: number,
  material: MaterialId,
): void {
  const [w, depth] = plan.structure.footprint;
  for (let y = lo; y <= hi; y++) {
    for (let x = 0; x < w; x++) {
      for (let z = 0; z < depth; z++) {
        const onEdge = x === 0 || z === 0 || x === w - 1 || z === depth - 1;
        if (!onEdge) continue;
        if (cellReservedByDevice(x, y, z, plan)) continue;
        grid.set(x, y, z, makeBlock(material));
      }
    }
  }
  if (lo > 0) {
    for (let x = 0; x < w; x++) {
      for (let z = 0; z < depth; z++) {
        const onEdge = x === 0 || z === 0 || x === w - 1 || z === depth - 1;
        if (onEdge) continue;
        if (cellReservedByDevice(x, lo, z, plan)) continue;
        grid.set(x, lo, z, makeBlock(material));
      }
    }
  }
}

function buildRoof(
  grid: MutableBlockGrid,
  plan: Plan,
  lo: number,
  hi: number,
  material: MaterialId,
): void {
  const [w, depth] = plan.structure.footprint;
  const stairMaterial = `${material}_stairs` as MaterialId;
  for (let y = lo; y <= hi; y++) {
    for (let x = 0; x < w; x++) {
      for (let z = 0; z < depth; z++) {
        const onEdge = x === 0 || z === 0 || x === w - 1 || z === depth - 1;
        if (!onEdge) continue;
        if (cellReservedByDevice(x, y, z, plan)) continue;
        const side = sideFromEdge(x, z, w, depth);
        if (side === null) continue;
        const facing = stairFacingForSide(side);
        grid.set(x, y, z, makeBlock(stairMaterial, { facing, half: 'bottom' }));
      }
    }
  }
  if (lo > 0) {
    for (let x = 0; x < w; x++) {
      for (let z = 0; z < depth; z++) {
        const onEdge = x === 0 || z === 0 || x === w - 1 || z === depth - 1;
        if (onEdge) continue;
        if (cellReservedByDevice(x, lo, z, plan)) continue;
        grid.set(x, lo, z, makeBlock(material));
      }
    }
  }
}

function sideFromEdge(x: number, z: number, w: number, depth: number): Side | null {
  if (x === 0) return 'west';
  if (x === w - 1) return 'east';
  if (z === 0) return 'north';
  if (z === depth - 1) return 'south';
  return null;
}

function stairFacingForSide(side: Side): string {
  switch (side) {
    case 'north': return 'north';
    case 'south': return 'south';
    case 'east': return 'east';
    case 'west': return 'west';
  }
}

// ── corner posts ──────────────────────────────────────────────────

function buildCornerPosts(grid: MutableBlockGrid, plan: Plan, vocab: Vocabulary): void {
  const [w, depth] = plan.structure.footprint;
  const h = plan.structure.height;
  const corners: Array<[number, number]> = [
    [0, 0],
    [w - 1, 0],
    [0, depth - 1],
    [w - 1, depth - 1],
  ];
  const topFloor = topFloorRange(plan);
  const topHi = topFloor?.[1] ?? h - 1;
  // The harvested `post` carries the full block state (typically
  // `oak_log{axis=y}`); the cap row above the top floor in the corpus uses
  // `stripped_oak_log{axis=y}`. We keep the cap as a fixed fallback so a
  // donor that harvested something else for `post` (e.g. `birch_log`) still
  // gets the corpus's capped-silhouette on the very top cell.
  const mainPost = vocab.post ?? { id: 'oak_log', properties: { axis: 'y' } };
  const capPost = { id: 'stripped_oak_log', properties: { axis: 'y' } };
  for (let y = 1; y < h; y++) {
    for (const [x, z] of corners) {
      if (cellReservedByDevice(x, y, z, plan)) continue;
      const state = y > topHi ? capPost : mainPost;
      grid.set(x, y, z, makeBlock(state.id, state.properties));
    }
  }
}

function topFloorRange(plan: Plan): [number, number] | undefined {
  let top: Floor | undefined;
  for (const f of plan.floors) {
    if (!top || f.range[1] > top.range[1]) top = f;
  }
  return top?.range;
}

// ── device reservation table ──────────────────────────────────────

function cellReservedByDevice(x: number, y: number, z: number, plan: Plan): boolean {
  for (const d of plan.devices) {
    if (cellInDevice(x, y, z, d, plan)) return true;
  }
  return false;
}

function cellInDevice(x: number, y: number, z: number, d: Device, plan: Plan): boolean {
  const [w, depth] = plan.structure.footprint;
  switch (d.kind) {
    case 'door': {
      if (y !== d.floor && y !== d.floor + 1) return false;
      switch (d.side) {
        case 'north': return x === Math.floor(w / 2) && z === 0;
        case 'south': return x === Math.floor(w / 2) && z === depth - 1;
        case 'east': return x === w - 1 && z === Math.floor(depth / 2);
        case 'west': return x === 0 && z === Math.floor(depth / 2);
      }
      return false;
    }
    case 'window': {
      if (y !== d.floor) return false;
      const cx = Math.floor(w / 2);
      const cz = Math.floor(depth / 2);
      const half = Math.floor((d.width ?? 1) / 2);
      switch (d.side) {
        case 'north': return Math.abs(x - cx) <= half && z === 0;
        case 'south': return Math.abs(x - cx) <= half && z === depth - 1;
        case 'east': return x === w - 1 && Math.abs(z - cz) <= half;
        case 'west': return x === 0 && Math.abs(z - cz) <= half;
      }
      return false;
    }
    case 'torch':
    case 'lever':
    case 'flower_pot':
    case 'candle':
    case 'campfire': {
      const [tx, ty, tz] = d.pos;
      return tx === x && ty === y && tz === z;
    }
    case 'ladder':
    case 'fence_post':
    case 'corner_post': {
      const [tx, ty, tz] = d.pos;
      return tx === x && ty === y && tz === z;
    }
    case 'bed': {
      if (y !== d.floor) return false;
      return bedHeadFootCell(d, plan).includes(`${x},${z}`);
    }
    case 'chest':
    case 'barrel':
    case 'furnace': {
      if (y !== d.floor) return false;
      return deviceSideCell(x, z, d, plan);
    }
    default:
      return false;
  }
}

function deviceSideCell(x: number, z: number, d: Device, plan: Plan): boolean {
  if (d.kind !== 'chest' && d.kind !== 'barrel' && d.kind !== 'furnace') {
    return false;
  }
  const [w, depth] = plan.structure.footprint;
  const cx = Math.floor(w / 2);
  const cz = Math.floor(depth / 2);
  switch (d.side) {
    case 'north': return x === cx && z === 0;
    case 'south': return x === cx && z === depth - 1;
    case 'east': return x === w - 1 && z === cz;
    case 'west': return x === 0 && z === cz;
  }
  return false;
}

function bedHeadFootCell(d: Extract<Device, { kind: 'bed' }>, plan: Plan): string[] {
  const [w, depth] = plan.structure.footprint;
  const cx = Math.floor(w / 2);
  const cz = Math.floor(depth / 2);
  switch (d.side) {
    case 'north': return [`${cx},0`, `${cx - 1},0`];
    case 'south': return [`${cx},${depth - 1}`, `${cx - 1},${depth - 1}`];
    case 'east': return [`${w - 1},${cz}`, `${w - 1},${cz - 1}`];
    case 'west': return [`0,${cz}`, `0,${cz - 1}`];
  }
}

// ── devices ───────────────────────────────────────────────────────

function buildDevice(
  grid: MutableBlockGrid,
  d: Device,
  plan: Plan,
  guard: FabricGuard,
  vocab: Vocabulary,
): void {
  const written: Coord[] = [];
  guard.setDevice(d.kind);
  switch (d.kind) {
    case 'door':
      written.push(...placeDoor(grid, d, plan, vocab));
      break;
    case 'window':
      written.push(...placeWindow(grid, d, plan, vocab));
      break;
    case 'torch':
      written.push(placeTorch(grid, d, vocab));
      break;
    case 'ladder':
      written.push(placeLadder(grid, d));
      break;
    case 'lever':
      written.push(placeLever(grid, d));
      break;
    case 'flower_pot':
      written.push(placeFlowerPot(grid, d, vocab));
      break;
    case 'bed':
      written.push(...placeBed(grid, d, plan));
      break;
    case 'chest':
      written.push(placeChest(grid, d, plan));
      break;
    case 'barrel':
      written.push(placeBarrel(grid, d, plan));
      break;
    case 'candle':
      written.push(placeCandle(grid, d, vocab));
      break;
    case 'campfire':
      written.push(placeCampfire(grid, d, vocab));
      break;
    case 'furnace':
      written.push(placeFurnace(grid, d, plan));
      break;
    case 'external_stair':
      written.push(...placeExternalStair(grid, d, plan, vocab));
      break;
    case 'chimney':
      written.push(...placeChimney(grid, d, plan));
      break;
    case 'crenellation':
      written.push(...placeCrenellation(grid, d, plan, vocab));
      break;
    case 'fence_post':
      written.push(...placeFencePost(grid, d, vocab));
      break;
    case 'corner_post':
      written.push(...placeCornerPost(grid, d, plan, vocab));
      break;
  }
  for (const w of written) guard.recordOrigin(w);
  guard.finishDevice(grid, written);
}

function perimeterCellFor(side: Side, plan: Plan): { x: number; z: number } {
  const [w, depth] = plan.structure.footprint;
  const cx = Math.floor(w / 2);
  const cz = Math.floor(depth / 2);
  switch (side) {
    case 'north': return { x: cx, z: 0 };
    case 'south': return { x: cx, z: depth - 1 };
    case 'east': return { x: w - 1, z: cz };
    case 'west': return { x: 0, z: cz };
  }
}

function placeDoor(
  grid: MutableBlockGrid,
  d: Extract<Device, { kind: 'door' }>,
  plan: Plan,
  vocab: Vocabulary,
): Coord[] {
  const { x, z } = perimeterCellFor(d.side, plan);
  const out: Coord[] = [];
  const lower = pickFromList(vocab.door_lower, 0) ?? {
    id: 'oak_door',
    properties: { half: 'lower', hinge: 'left', facing: 'south', open: 'false', powered: 'false' },
  };
  const upper = pickFromList(vocab.door_upper, 0) ?? {
    id: lower.id,
    properties: { ...lower.properties, half: 'upper' },
  };
  grid.set(x, d.floor, z, makeBlock(lower.id, {
    ...lower.properties,
    facing: d.side,
    half: 'lower',
  }));
  out.push([x, d.floor, z]);

  const upperY = d.floor + 1;
  const h = plan.structure.height;
  if (upperY < h) {
    grid.set(x, upperY, z, makeBlock(upper.id, {
      ...upper.properties,
      facing: d.side,
      half: 'upper',
    }));
    out.push([x, upperY, z]);
  }
  const below = grid.get(x, 0, z)?.id;
  if (below === 'grass_block' || below === 'coarse_dirt') {
    const outward = outwardFromSide(d.side);
    const sx = x + outward[0];
    const sz = z + outward[1];
    if (sx >= 0 && sz >= 0 && sx < plan.structure.footprint[0] && sz < plan.structure.footprint[1]) {
      void [sx, sz];
    }
    grid.set(x, 0, z, makeBlock('dirt_path'));
  }
  return out;
}

function outwardFromSide(side: Side): [number, number] {
  switch (side) {
    case 'north': return [0, -1];
    case 'south': return [0, 1];
    case 'east': return [1, 0];
    case 'west': return [-1, 0];
  }
}

function placeWindow(
  grid: MutableBlockGrid,
  d: Extract<Device, { kind: 'window' }>,
  plan: Plan,
  vocab: Vocabulary,
): Coord[] {
  const [w, depth] = plan.structure.footprint;
  const cx = Math.floor(w / 2);
  const cz = Math.floor(depth / 2);
  const half = Math.floor((d.width ?? 1) / 2);
  const out: Coord[] = [];
  const win = pickFromList(vocab.window, 0) ?? {
    id: 'glass_pane',
    properties: { east: 'false', north: 'true', south: 'true', west: 'false', waterlogged: 'false' },
  };
  const write = (x: number, z: number): void => {
    grid.set(x, d.floor, z, makeBlock(win.id, win.properties));
    out.push([x, d.floor, z]);
  };
  switch (d.side) {
    case 'north':
      for (let dx = -half; dx <= half; dx++) write(cx + dx, 0);
      break;
    case 'south':
      for (let dx = -half; dx <= half; dx++) write(cx + dx, depth - 1);
      break;
    case 'east':
      for (let dz = -half; dz <= half; dz++) write(w - 1, cz + dz);
      break;
    case 'west':
      for (let dz = -half; dz <= half; dz++) write(0, cz + dz);
      break;
  }
  return out;
}

function placeTorch(grid: MutableBlockGrid, d: Extract<Device, { kind: 'torch' }>, vocab: Vocabulary): Coord {
  const [x, y, z] = d.pos;
  const torch = pickFromLight(vocab, 'torch') ?? { id: 'torch', properties: {} };
  grid.set(x, y, z, makeBlock(torch.id, torch.properties));
  return [x, y, z];
}

function placeLadder(grid: MutableBlockGrid, d: Extract<Device, { kind: 'ladder' }>): Coord {
  const [x, y, z] = d.pos;
  grid.set(x, y, z, makeBlock('ladder', { facing: oppositeSide(d.side) }));
  return [x, y, z];
}

function oppositeSide(side: Side): Side {
  switch (side) {
    case 'north': return 'south';
    case 'south': return 'north';
    case 'east': return 'west';
    case 'west': return 'east';
  }
}

function placeLever(grid: MutableBlockGrid, d: Extract<Device, { kind: 'lever' }>): Coord {
  const [x, y, z] = d.pos;
  grid.set(x, y, z, makeBlock('lever', { face: 'floor', powered: 'false', facing: 'south' }));
  return [x, y, z];
}

function placeFlowerPot(grid: MutableBlockGrid, d: Extract<Device, { kind: 'flower_pot' }>, vocab: Vocabulary): Coord {
  const [x, y, z] = d.pos;
  const pot = pickFromList(vocab.decoration, 0);
  // The decoration role is a generic catch-all; only use it for actual pots.
  const useVocab = pot !== null && pot.id === 'flower_pot';
  const block = useVocab ? pot! : { id: 'flower_pot', properties: {} };
  grid.set(x, y, z, makeBlock(block.id, block.properties));
  return [x, y, z];
}

function placeBed(
  grid: MutableBlockGrid,
  d: Extract<Device, { kind: 'bed' }>,
  plan: Plan,
): Coord[] {
  const [w, depth] = plan.structure.footprint;
  const cx = Math.floor(w / 2);
  const cz = Math.floor(depth / 2);
  const head: Coord = [0, d.floor, 0];
  const foot: Coord = [0, d.floor, 0];
  switch (d.side) {
    case 'north':
      head[0] = cx - 1; head[2] = 0;
      foot[0] = cx; foot[2] = 0;
      break;
    case 'south':
      head[0] = cx - 1; head[2] = depth - 1;
      foot[0] = cx; foot[2] = depth - 1;
      break;
    case 'east':
      head[0] = w - 1; head[2] = cz - 1;
      foot[0] = w - 1; foot[2] = cz;
      break;
    case 'west':
      head[0] = 0; head[2] = cz - 1;
      foot[0] = 0; foot[2] = cz;
      break;
  }
  grid.set(head[0], head[1], head[2], makeBlock('white_bed', {
    part: 'head', facing: d.side, occupied: 'false',
  }));
  grid.set(foot[0], foot[1], foot[2], makeBlock('white_bed', {
    part: 'foot', facing: d.side, occupied: 'false',
  }));
  return [head, foot];
}

function placeChest(
  grid: MutableBlockGrid,
  d: Extract<Device, { kind: 'chest' }>,
  plan: Plan,
): Coord {
  const { x, z } = perimeterCellFor(d.side, plan);
  grid.set(x, d.floor, z, makeBlock('chest', { facing: d.side, type: 'single' }));
  return [x, d.floor, z];
}

function placeBarrel(
  grid: MutableBlockGrid,
  d: Extract<Device, { kind: 'barrel' }>,
  plan: Plan,
): Coord {
  const { x, z } = perimeterCellFor(d.side, plan);
  grid.set(x, d.floor, z, makeBlock('barrel', { facing: d.side }));
  return [x, d.floor, z];
}

function placeCandle(grid: MutableBlockGrid, d: Extract<Device, { kind: 'candle' }>, vocab: Vocabulary): Coord {
  const [x, y, z] = d.pos;
  const candle = pickFromLight(vocab, 'candle') ?? { id: 'candle', properties: {} };
  grid.set(x, y, z, makeBlock(candle.id, candle.properties));
  return [x, y, z];
}

function placeCampfire(grid: MutableBlockGrid, d: Extract<Device, { kind: 'campfire' }>, vocab: Vocabulary): Coord {
  const [x, y, z] = d.pos;
  const fire = pickFromLight(vocab, 'campfire') ?? {
    id: 'campfire',
    properties: { lit: 'true', signal_fire: 'false', waterlogged: 'false' },
  };
  grid.set(x, y, z, makeBlock(fire.id, {
    lit: 'true',
    signal_fire: 'false',
    waterlogged: 'false',
    ...fire.properties,
  }));
  return [x, y, z];
}

function placeFurnace(
  grid: MutableBlockGrid,
  d: Extract<Device, { kind: 'furnace' }>,
  plan: Plan,
): Coord {
  const { x, z } = perimeterCellFor(d.side, plan);
  grid.set(x, d.floor, z, makeBlock('furnace', { facing: d.side, lit: 'false' }));
  return [x, d.floor, z];
}

function placeExternalStair(
  grid: MutableBlockGrid,
  d: Extract<Device, { kind: 'external_stair' }>,
  plan: Plan,
  vocab: Vocabulary,
): Coord[] {
  const [w, depth] = plan.structure.footprint;
  const inwardAxisOffset = (i: number): number =>
    Math.floor(i / 2);
  const facing = stairFacingForExternal(d.side);
  const out: Coord[] = [];
  const h = Math.max(0, d.end_y - d.start_y);
  // The harvested `roof_stairs` carries an actual `facing` value from the
  // donor. The device's `side` overrides it: the stair the device lays
  // climbs INWARD, so its `facing` is the opposite of the side it sits on.
  const stair = pickFromList(vocab.roof_stairs, 0) ?? { id: 'oak_stairs', properties: { facing, half: 'bottom' } };
  for (let i = 0; i < h; i++) {
    const inward = inwardAxisOffset(i);
    let x: number;
    let z: number;
    switch (d.side) {
      case 'north': x = Math.floor(w / 2) + inward; z = 0; break;
      case 'south': x = Math.floor(w / 2) + inward; z = depth - 1; break;
      case 'east': x = w - 1; z = Math.floor(depth / 2) + inward; break;
      case 'west': x = 0; z = Math.floor(depth / 2) + inward; break;
    }
    if (x < 0 || x >= w || z < 0 || z >= depth) continue;
    grid.set(x, d.start_y + i, z, makeBlock(stair.id, {
      ...stair.properties,
      facing,
      half: 'bottom',
    }));
    out.push([x, d.start_y + i, z]);
  }
  return out;
}

function stairFacingForExternal(side: Side): string {
  switch (side) {
    case 'north': return 'south';
    case 'south': return 'north';
    case 'east': return 'west';
    case 'west': return 'east';
  }
}

function placeChimney(
  grid: MutableBlockGrid,
  d: Extract<Device, { kind: 'chimney' }>,
  _plan: Plan,
): Coord[] {
  const [ox, oz] = d.offset;
  const out: Coord[] = [];
  for (let y = d.from_floor; y < d.to_floor; y++) {
    grid.set(ox, y, oz, makeBlock('cobblestone_wall', { up: 'true' }));
    out.push([ox, y, oz]);
  }
  return out;
}

function placeCrenellation(
  grid: MutableBlockGrid,
  d: Extract<Device, { kind: 'crenellation' }>,
  plan: Plan,
  vocab: Vocabulary,
): Coord[] {
  const [w, depth] = plan.structure.footprint;
  const y = d.top_y;
  const stride = d.spacing + 1;
  const out: Coord[] = [];
  const merlon = vocab.crenel ?? { id: 'cobblestone', properties: {} };
  for (let x = 0; x < w; x++) {
    if (x % stride === 0) {
      grid.set(x, y, 0, makeBlock(merlon.id, merlon.properties));
      grid.set(x, y, depth - 1, makeBlock(merlon.id, merlon.properties));
      out.push([x, y, 0], [x, y, depth - 1]);
    }
  }
  for (let z = 0; z < depth; z++) {
    if (z % stride === 0) {
      grid.set(0, y, z, makeBlock(merlon.id, merlon.properties));
      grid.set(w - 1, y, z, makeBlock(merlon.id, merlon.properties));
      out.push([0, y, z], [w - 1, y, z]);
    }
  }
  return out;
}

function placeFencePost(
  grid: MutableBlockGrid,
  d: Extract<Device, { kind: 'fence_post' }>,
  vocab: Vocabulary,
): Coord[] {
  const [x, y, z] = d.pos;
  const out: Coord[] = [];
  const fence = vocab.fence ?? { id: 'oak_fence', properties: {} };
  for (let i = 0; i < d.height; i++) {
    grid.set(x, y + i, z, makeBlock(fence.id, fence.properties));
    out.push([x, y + i, z]);
  }
  return out;
}

function placeCornerPost(
  grid: MutableBlockGrid,
  d: Extract<Device, { kind: 'corner_post' }>,
  plan: Plan,
  vocab: Vocabulary,
): Coord[] {
  const [x, y, z] = d.pos;
  const [w, depth] = plan.structure.footprint;
  const onCorner = (x === 0 || x === w - 1) && (z === 0 || z === depth - 1);
  if (!onCorner) return [];
  const out: Coord[] = [];
  const post = vocab.post ?? { id: 'oak_log', properties: { axis: 'y' } };
  for (let i = 0; i < d.height; i++) {
    grid.set(x, y + i, z, makeBlock(post.id, post.properties));
    out.push([x, y + i, z]);
  }
  return out;
}

/** Find the first light whose id matches a kind we want (torch/candle/campfire). */
function pickFromLight(vocab: Vocabulary, kind: 'torch' | 'candle' | 'campfire'): BlockState | null {
  for (const s of vocab.light) {
    if (s.id === kind) return s;
  }
  return null;
}

// ── Structure assembly ────────────────────────────────────────────

function buildStructure(
  size: [number, number, number],
  blocks: Array<{ pos: [number, number, number]; state: { id: string; props: Record<string, string> } }>,
): Structure {
  const structure = new Structure(BlockPos.create(size[0], size[1], size[2]));
  for (const b of blocks) {
    const id = b.state.id.includes(':') ? b.state.id : `minecraft:${b.state.id}`;
    structure.addBlock(BlockPos.create(b.pos[0], b.pos[1], b.pos[2]), id, b.state.props);
  }
  return structure;
}












