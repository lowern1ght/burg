/**
 * Plan → Structure generator.
 *
 * The generator turns a build plan into a Lodestone Structure by walking each
 * floor in the plan and emitting a per-floor scaffold (ground apron, perimeter
 * walls, floor slabs, battlements/roof cap) before the device pass layers the
 * named patterns (door, window, torch, bed, furnace, ladder, ...) on top.
 *
 * Style comes from a fixed `Vocabulary` — the patterns the generator knows
 * about, indexed by the floor's primary material. The corpus supplies the
 * conventions (corner posts in oak_log, slab cap at residential storeys,
 * stair-pitch roof); the plan supplies the dimensions.
 *
 * The generator is deterministic: two runs of the same plan produce the same
 * structure. Stochastic decoration is the corpus's job, not ours.
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

export function generateStructure(plan: Plan): GenerationResult {
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
  const vocab = buildVocabulary(plan);

  const guard = new FabricGuard();

  for (const floor of plan.floors) {
    buildFloor(grid, floor, plan, vocab);
  }
  buildCornerPosts(grid, plan);
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

type Vocabulary = {
  cornerPost: MaterialId;
  cornerPostTop: MaterialId;
  porchFloor: MaterialId;
  thresholdStep: MaterialId;
};

/**
 * The fixed set of patterns the residential generator knows about. Indices
 * are picked once at plan-load time from the floor materials; the generator
 * itself works off the resolved vocabulary rather than re-deriving each cell.
 */
function buildVocabulary(plan: Plan): Vocabulary {
  const ground = plan.floors.find((f) => f.layout === 'ground');
  const top = findTopmostResidential(plan);
  return {
    cornerPost: top ?? 'oak_log',
    cornerPostTop: 'stripped_oak_log',
    porchFloor: ground?.material ?? 'dirt_path',
    thresholdStep: 'cobblestone_stairs',
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

function buildCornerPosts(grid: MutableBlockGrid, plan: Plan): void {
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
  for (let y = 1; y < h; y++) {
    for (const [x, z] of corners) {
      if (cellReservedByDevice(x, y, z, plan)) continue;
      const mat: MaterialId =
        y > topHi ? 'stripped_oak_log' : 'oak_log';
      grid.set(x, y, z, makeBlock(mat, { axis: 'y' }));
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
      written.push(...placeWindow(grid, d, plan));
      break;
    case 'torch':
      written.push(placeTorch(grid, d));
      break;
    case 'ladder':
      written.push(placeLadder(grid, d));
      break;
    case 'lever':
      written.push(placeLever(grid, d));
      break;
    case 'flower_pot':
      written.push(placeFlowerPot(grid, d));
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
      written.push(placeCandle(grid, d));
      break;
    case 'campfire':
      written.push(placeCampfire(grid, d));
      break;
    case 'furnace':
      written.push(placeFurnace(grid, d, plan));
      break;
    case 'external_stair':
      written.push(...placeExternalStair(grid, d, plan));
      break;
    case 'chimney':
      written.push(...placeChimney(grid, d, plan));
      break;
    case 'crenellation':
      written.push(...placeCrenellation(grid, d, plan));
      break;
    case 'fence_post':
      written.push(...placeFencePost(grid, d));
      break;
    case 'corner_post':
      written.push(...placeCornerPost(grid, d, plan));
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
  _vocab: Vocabulary,
): Coord[] {
  const { x, z } = perimeterCellFor(d.side, plan);
  const out: Coord[] = [];
  grid.set(x, d.floor, z, makeBlock('oak_door', {
    facing: d.side,
    half: 'lower',
    hinge: 'left',
    open: 'false',
    powered: 'false',
  }));
  out.push([x, d.floor, z]);

  const upperY = d.floor + 1;
  const h = plan.structure.height;
  if (upperY < h) {
    grid.set(x, upperY, z, makeBlock('oak_door', {
      facing: d.side,
      half: 'upper',
      hinge: 'left',
      open: 'false',
      powered: 'false',
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
): Coord[] {
  const [w, depth] = plan.structure.footprint;
  const cx = Math.floor(w / 2);
  const cz = Math.floor(depth / 2);
  const half = Math.floor((d.width ?? 1) / 2);
  const out: Coord[] = [];
  const write = (x: number, z: number): void => {
    grid.set(x, d.floor, z, makeBlock('glass_pane'));
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

function placeTorch(grid: MutableBlockGrid, d: Extract<Device, { kind: 'torch' }>): Coord {
  const [x, y, z] = d.pos;
  grid.set(x, y, z, makeBlock('torch'));
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

function placeFlowerPot(grid: MutableBlockGrid, d: Extract<Device, { kind: 'flower_pot' }>): Coord {
  const [x, y, z] = d.pos;
  grid.set(x, y, z, makeBlock('flower_pot'));
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

function placeCandle(grid: MutableBlockGrid, d: Extract<Device, { kind: 'candle' }>): Coord {
  const [x, y, z] = d.pos;
  grid.set(x, y, z, makeBlock('candle'));
  return [x, y, z];
}

function placeCampfire(grid: MutableBlockGrid, d: Extract<Device, { kind: 'campfire' }>): Coord {
  const [x, y, z] = d.pos;
  grid.set(x, y, z, makeBlock('campfire', { lit: 'true', signal_fire: 'false', waterlogged: 'false' }));
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
): Coord[] {
  const [w, depth] = plan.structure.footprint;
  const inwardAxisOffset = (i: number): number =>
    Math.floor(i / 2);
  const facing = stairFacingForExternal(d.side);
  const out: Coord[] = [];
  const h = Math.max(0, d.end_y - d.start_y);
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
    grid.set(x, d.start_y + i, z, makeBlock('oak_stairs', { facing, half: 'bottom' }));
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
): Coord[] {
  const [w, depth] = plan.structure.footprint;
  const y = d.top_y;
  const stride = d.spacing + 1;
  const out: Coord[] = [];
  for (let x = 0; x < w; x++) {
    if (x % stride === 0) {
      grid.set(x, y, 0, makeBlock('cobblestone'));
      grid.set(x, y, depth - 1, makeBlock('cobblestone'));
      out.push([x, y, 0], [x, y, depth - 1]);
    }
  }
  for (let z = 0; z < depth; z++) {
    if (z % stride === 0) {
      grid.set(0, y, z, makeBlock('cobblestone'));
      grid.set(w - 1, y, z, makeBlock('cobblestone'));
      out.push([0, y, z], [w - 1, y, z]);
    }
  }
  return out;
}

function placeFencePost(
  grid: MutableBlockGrid,
  d: Extract<Device, { kind: 'fence_post' }>,
): Coord[] {
  const [x, y, z] = d.pos;
  const out: Coord[] = [];
  for (let i = 0; i < d.height; i++) {
    grid.set(x, y + i, z, makeBlock('oak_fence'));
    out.push([x, y + i, z]);
  }
  return out;
}

function placeCornerPost(
  grid: MutableBlockGrid,
  d: Extract<Device, { kind: 'corner_post' }>,
  plan: Plan,
): Coord[] {
  const [x, y, z] = d.pos;
  const [w, depth] = plan.structure.footprint;
  const onCorner = (x === 0 || x === w - 1) && (z === 0 || z === depth - 1);
  if (!onCorner) return [];
  const out: Coord[] = [];
  for (let i = 0; i < d.height; i++) {
    grid.set(x, y + i, z, makeBlock('oak_log', { axis: 'y' }));
    out.push([x, y + i, z]);
  }
  return out;
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












