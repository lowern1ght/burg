import type { Structure } from '@mattzh72/lodestone';
import type { Device, Floor, LayoutHint, MaterialId, Plan, Side } from './types';
import { validatePlan } from './validate';

export type SerializationWarningRule =
  | 'unknown_block'
  | 'ambiguous_floor'
  | 'unparseable_device'
  | 'gap_in_floors'
  | 'mixed_material_row'
  | 'validator';

export type SerializationWarning = {
  rule: SerializationWarningRule;
  message: string;
  pos?: [number, number, number];
};

export type SerializationOptions = {
  name?: string;
  outputPath?: string;
};

export type StructureSerializationResult = {
  plan: Plan;
  warnings: SerializationWarning[];
  valid: boolean;
};

type Coord = [number, number, number];

type Cell = {
  pos: Coord;
  id: string;
  properties: Record<string, string>;
  material: MaterialId;
};

type RowSummary = {
  material: MaterialId | null;
  hasSlabOrStair: boolean;
};

type ExposedCell = {
  cell: Cell;
  side: Side;
};

const GUARD_RULES: Plan['rules']['guard'] = [
  'no_floating_roof',
  'no_slab_rider',
  'no_hanging_roof',
  'no_cantilever',
  'no_orphan_fence',
];

const COMPLIANCE_RULES: Plan['rules']['compliance'] = [
  'constant_footprint',
  'material_ladder',
  'contiguous_floors',
];

const SIDE_ORDER: Side[] = ['south', 'north', 'west', 'east'];
const TORCH_IDS = new Set(['torch', 'wall_torch', 'lantern', 'soul_lantern', 'soul_torch']);
const CANDLE_IDS = new Set(['candle', 'white_candle', 'red_candle', 'black_candle', 'blue_candle',
  'brown_candle', 'cyan_candle', 'gray_candle', 'green_candle', 'light_blue_candle',
  'light_gray_candle', 'lime_candle', 'magenta_candle', 'orange_candle', 'pink_candle',
  'purple_candle', 'yellow_candle']);
const CAMPFIRE_IDS = new Set(['campfire', 'soul_campfire']);
const FURNACE_IDS = new Set(['furnace', 'lit_furnace', 'smoker', 'lit_smoker', 'blast_furnace', 'lit_blast_furnace']);
const BED_IDS = new Set(['white_bed', 'orange_bed', 'magenta_bed', 'light_blue_bed', 'yellow_bed',
  'lime_bed', 'pink_bed', 'gray_bed', 'light_gray_bed', 'cyan_bed', 'purple_bed', 'blue_bed',
  'brown_bed', 'green_bed', 'red_bed', 'black_bed']);
const POT_IDS = new Set(['flower_pot']);
const POTTED_PREFIX = 'potted_';
const IGNORED_IDS = new Set([
  'air', 'cave_air', 'void_air',
  'water',
  'jigsaw',
  'barrier', 'light', 'structure_block',
]);

export function serializeStructureToPlan(
  structure: Structure,
  options: SerializationOptions = {},
): StructureSerializationResult {
  const rawSize = structure.getSize();
  const size: [number, number, number] = [rawSize[0], rawSize[1], rawSize[2]];
  const warnings: SerializationWarning[] = [];
  const unknownIds = new Set<string>();
  const cells: Cell[] = [];
  for (const placed of structure.getBlocks()) {
    const pos: Coord = [placed.pos[0], placed.pos[1], placed.pos[2]];
    const id = stripNamespace(placed.state.getName().toString());
    if (classifyBlock(id) === 'ignored') continue;
    const properties: Record<string, string> = {};
    for (const [key, value] of Object.entries(placed.state.getProperties())) {
      properties[key] = String(value);
    }
    const material = materialForCell(id, pos, warnings, unknownIds);
    cells.push({ pos, id, properties, material });
  }
  cells.sort(compareCells);

  const grid = new Map<string, Cell>();
  for (const cell of cells) {
    grid.set(cellKey(cell.pos), cell);
  }

  const rows = summarizeRows(cells, size, warnings);
  const floors = buildFloors(rows, size[1]);
  const devices = extractDevices(cells, grid, size, warnings);
  const plan: Plan = {
    name: options.name ?? 'recovered',
    version: 1,
    structure: {
      footprint: [size[0], size[2]],
      height: size[1],
      origin: [0, 0, 0],
    },
    floors,
    devices,
    rules: {
      guard: [...GUARD_RULES],
      compliance: [...COMPLIANCE_RULES],
    },
    output: {
      name: options.name ?? 'recovered',
      path: options.outputPath ?? 'recovered/',
    },
  };
  const validation = validatePlan(plan);
  for (const finding of validation.errors) {
    warnings.push({
      rule: 'validator',
      message: `${finding.rule}: ${finding.message}`,
      pos: finding.pos,
    });
  }

  return { plan, warnings, valid: validation.ok };
}

export function serializePlanToYaml(plan: Plan): string {
  const lines: string[] = [
    `name: ${yamlScalar(plan.name)}`,
    'version: 1',
    'structure:',
    `  footprint: ${inlineArray(plan.structure.footprint)}`,
    `  height: ${plan.structure.height}`,
    `  origin: ${inlineArray(plan.structure.origin)}`,
  ];

  if (plan.floors.length === 0) {
    lines.push('floors: []');
  } else {
    lines.push('floors:');
    for (const floor of plan.floors) {
      lines.push(`  - range: ${inlineArray(floor.range)}`);
      lines.push(`    material: ${yamlScalar(floor.material)}`);
      lines.push(`    layout: ${yamlScalar(floor.layout)}`);
    }
  }

  if (plan.devices.length === 0) {
    lines.push('devices: []');
  } else {
    lines.push('devices:');
    for (const device of plan.devices) {
      lines.push(`  - kind: ${yamlScalar(device.kind)}`);
      appendDeviceYaml(lines, device);
    }
  }

  lines.push(`rules:`);
  lines.push(`  guard: ${inlineArray(plan.rules.guard)}`);
  lines.push(`  compliance: ${inlineArray(plan.rules.compliance)}`);
  lines.push('output:');
  lines.push(`  name: ${yamlScalar(plan.output.name)}`);
  lines.push(`  path: ${yamlScalar(plan.output.path)}`);
  return `${lines.join('\n')}\n`;
}

function appendDeviceYaml(lines: string[], device: Device): void {
  switch (device.kind) {
    case 'door':
      lines.push(`    side: ${yamlScalar(device.side)}`);
      lines.push(`    floor: ${device.floor}`);
      break;
    case 'window':
      lines.push(`    side: ${yamlScalar(device.side)}`);
      lines.push(`    floor: ${device.floor}`);
      if (device.width !== undefined) lines.push(`    width: ${device.width}`);
      break;
    case 'torch':
      lines.push(`    pos: ${inlineArray(device.pos)}`);
      break;
    case 'ladder':
      lines.push(`    side: ${yamlScalar(device.side)}`);
      lines.push(`    pos: ${inlineArray(device.pos)}`);
      break;
    case 'lever':
      lines.push(`    pos: ${inlineArray(device.pos)}`);
      break;
    case 'flower_pot':
      lines.push(`    pos: ${inlineArray(device.pos)}`);
      break;
    case 'bed':
      lines.push(`    side: ${yamlScalar(device.side)}`);
      lines.push(`    floor: ${device.floor}`);
      break;
    case 'chest':
      lines.push(`    side: ${yamlScalar(device.side)}`);
      lines.push(`    floor: ${device.floor}`);
      break;
    case 'barrel':
      lines.push(`    side: ${yamlScalar(device.side)}`);
      lines.push(`    floor: ${device.floor}`);
      break;
    case 'candle':
      lines.push(`    pos: ${inlineArray(device.pos)}`);
      break;
    case 'campfire':
      lines.push(`    pos: ${inlineArray(device.pos)}`);
      break;
    case 'furnace':
      lines.push(`    side: ${yamlScalar(device.side)}`);
      lines.push(`    floor: ${device.floor}`);
      break;
    case 'external_stair':
      lines.push(`    side: ${yamlScalar(device.side)}`);
      lines.push(`    start_y: ${device.start_y}`);
      lines.push(`    end_y: ${device.end_y}`);
      break;
    case 'chimney':
      lines.push(`    offset: ${inlineArray(device.offset)}`);
      lines.push(`    from_floor: ${device.from_floor}`);
      lines.push(`    to_floor: ${device.to_floor}`);
      break;
    case 'crenellation':
      lines.push(`    top_y: ${device.top_y}`);
      lines.push(`    spacing: ${device.spacing}`);
      break;
    case 'fence_post':
      lines.push(`    pos: ${inlineArray(device.pos)}`);
      lines.push(`    height: ${device.height}`);
      break;
    case 'corner_post':
      lines.push(`    pos: ${inlineArray(device.pos)}`);
      lines.push(`    height: ${device.height}`);
      break;
  }
}

function summarizeRows(
  cells: Cell[],
  size: [number, number, number],
  warnings: SerializationWarning[],
): RowSummary[] {
  const rows: RowSummary[] = [];
  for (let y = 0; y < size[1]; y++) {
    const rowCells = cells.filter((cell) => cell.pos[1] === y);
    rows.push(extractFloorMaterial(y, rowCells, size, warnings));
  }

  for (let y = 0; y < rows.length; y++) {
    if (rows[y].material !== null) continue;
    warnings.push({
      rule: 'gap_in_floors',
      message: `row ${y} contains no recoverable floor material`,
      pos: [0, y, 0],
    });
    rows[y].material = findNearbyMaterial(rows, y) ?? 'oak_planks';
  }
  return rows;
}

function extractFloorMaterial(
  y: number,
  rowCells: Cell[],
  size: [number, number, number],
  warnings: SerializationWarning[],
): RowSummary {
  const perimeterCells = rowCells.filter((cell) => isPerimeterCell(cell.pos, size));
  const perimeterFloors = perimeterCells.filter((cell) => classifyBlock(cell.id) === 'floor');
  if (perimeterFloors.length > 0) {
    emitRowWarnings(y, perimeterFloors, warnings);
    return {
      material: dominantMaterialId(perimeterFloors),
      hasSlabOrStair: perimeterCells.some((cell) => isSlabOrStairId(cell.id)),
    };
  }

  const rowFloors = rowCells.filter((cell) => classifyBlock(cell.id) === 'floor');
  const rowHasSlabOrStair = rowCells.some((cell) => isSlabOrStairId(cell.id));

  if (y === 0) {
    if (rowFloors.length > 0) {
      emitRowWarnings(y, rowFloors, warnings);
      return { material: dominantMaterialId(rowFloors), hasSlabOrStair: rowHasSlabOrStair };
    }
    return { material: null, hasSlabOrStair: rowHasSlabOrStair };
  }

  if (y === size[1] - 1) {
    const slabStairs = rowCells.filter((cell) => isSlabOrStairId(cell.id));
    if (slabStairs.length > 0) {
      emitRowWarnings(y, slabStairs, warnings);
      return { material: dominantMaterialId(slabStairs), hasSlabOrStair: true };
    }
    if (rowFloors.length > 0) {
      emitRowWarnings(y, rowFloors, warnings);
      return { material: dominantMaterialId(rowFloors), hasSlabOrStair: rowHasSlabOrStair };
    }
    return { material: null, hasSlabOrStair: rowHasSlabOrStair };
  }

  if (rowFloors.length > 0) {
    emitRowWarnings(y, rowFloors, warnings);
    return { material: dominantMaterialId(rowFloors), hasSlabOrStair: rowHasSlabOrStair };
  }
  return { material: null, hasSlabOrStair: rowHasSlabOrStair };
}

function emitRowWarnings(
  y: number,
  candidates: Cell[],
  warnings: SerializationWarning[],
): void {
  if (candidates.length < 2) return;
  const counts = new Map<MaterialId, number>();
  for (const cell of candidates) {
    counts.set(cell.material, (counts.get(cell.material) ?? 0) + 1);
  }
  if (counts.size > 1) {
    warnings.push({
      rule: 'mixed_material_row',
      message: `row ${y} contains ${counts.size} mapped materials`,
      pos: candidates[0]?.pos,
    });
  }
  const sorted = [...counts.entries()].sort((left, right) => {
    const diff = right[1] - left[1];
    return diff !== 0 ? diff : left[0].localeCompare(right[0]);
  });
  if (sorted[1] && sorted[1][1] === sorted[0][1]) {
    warnings.push({
      rule: 'ambiguous_floor',
      message: `row ${y} has tied dominant materials ${sorted[0][0]} and ${sorted[1][0]}`,
      pos: candidates[0]?.pos,
    });
  }
}

function dominantMaterialId(cells: Cell[]): MaterialId {
  const counts = new Map<MaterialId, number>();
  for (const cell of cells) {
    counts.set(cell.material, (counts.get(cell.material) ?? 0) + 1);
  }
  const sorted = [...counts.entries()].sort((left, right) => {
    const diff = right[1] - left[1];
    return diff !== 0 ? diff : left[0].localeCompare(right[0]);
  });
  return sorted[0][0];
}

function isPerimeterCell(pos: Coord, size: [number, number, number]): boolean {
  const [w, , d] = size;
  return pos[0] === 0 || pos[0] === w - 1 || pos[2] === 0 || pos[2] === d - 1;
}

function isSlabOrStairId(id: string): boolean {
  return id.endsWith('_slab') || id.endsWith('_stairs');
}

function findNearbyMaterial(rows: RowSummary[], y: number): MaterialId | null {
  for (let distance = 1; distance < rows.length; distance++) {
    const below = y - distance;
    if (below >= 0 && rows[below].material !== null) return rows[below].material;
    const above = y + distance;
    if (above < rows.length && rows[above].material !== null) return rows[above].material;
  }
  return null;
}

function buildFloors(rows: RowSummary[], height: number): Floor[] {
  const floors: Floor[] = [];
  let start = 0;
  let material = rows[0]?.material ?? 'oak_planks';
  let hasSlabOrStair = rows[0]?.hasSlabOrStair ?? false;
  for (let y = 1; y < height; y++) {
    if (rows[y].material !== material) {
      floors.push(makeFloor(start, y - 1, material, hasSlabOrStair, height));
      start = y;
      material = rows[y].material ?? material;
      hasSlabOrStair = rows[y].hasSlabOrStair;
    } else {
      hasSlabOrStair ||= rows[y].hasSlabOrStair;
    }
  }
  floors.push(makeFloor(start, height - 1, material, hasSlabOrStair, height));
  return floors;
}

function makeFloor(
  start: number,
  end: number,
  material: MaterialId,
  hasSlabOrStair: boolean,
  height: number,
): Floor {
  const layout: LayoutHint = start === 0
    ? 'ground'
    : end === height - 1
      ? hasSlabOrStair ? 'battlements' : 'roof'
      : 'residential';
  return { range: [start, end], material, layout };
}

function extractDevices(
  cells: Cell[],
  grid: Map<string, Cell>,
  size: [number, number, number],
  warnings: SerializationWarning[],
): Device[] {
  const devices: Device[] = [];
  devices.push(...extractDoors(cells, grid, size, warnings));
  devices.push(...extractWindows(cells, grid, size, warnings));
  devices.push(...extractTorches(cells));
  devices.push(...extractLadders(cells, grid, size, warnings));
  devices.push(...extractLevers(cells, size, warnings));
  devices.push(...extractFlowerPots(cells, size, warnings));
  devices.push(...extractBeds(cells, grid, size, warnings));
  devices.push(...extractChests(cells, grid, size, warnings));
  devices.push(...extractBarrels(cells, grid, size, warnings));
  devices.push(...extractCandles(cells, size, warnings));
  devices.push(...extractCampfires(cells, size, warnings));
  devices.push(...extractFurnaces(cells, grid, size, warnings));
  devices.push(...extractExternalStairs(cells, grid, size));
  return devices;
}

function extractDoors(
  cells: Cell[],
  grid: Map<string, Cell>,
  size: [number, number, number],
  warnings: SerializationWarning[],
): Device[] {
  const devices: Device[] = [];
  const visited = new Set<string>();
  for (const cell of cells.filter((candidate) => candidate.id.endsWith('_door'))) {
    const key = cellKey(cell.pos);
    if (visited.has(key)) continue;
    const group = cells.filter((candidate) => {
      const [x, , z] = candidate.pos;
      return candidate.id.endsWith('_door') && x === cell.pos[0] && z === cell.pos[2];
    });
    for (const member of group) visited.add(cellKey(member.pos));
    const exposed = pickExposedCell(group, grid, size);
    if (!exposed) {
      warnings.push({
        rule: 'unparseable_device',
        message: `door at ${cellKey(cell.pos)} has no exposed perimeter face`,
        pos: cell.pos,
      });
      continue;
    }
    devices.push({ kind: 'door', side: exposed.side, floor: Math.min(...group.map((member) => member.pos[1])) });
  }
  return devices;
}

function extractWindows(
  cells: Cell[],
  grid: Map<string, Cell>,
  size: [number, number, number],
  warnings: SerializationWarning[],
): Device[] {
  const exposed: ExposedCell[] = [];
  for (const cell of cells.filter((candidate) => candidate.id === 'glass_pane')) {
    const side = chooseSide(cell, grid, size);
    if (side === null) {
      warnings.push({
        rule: 'unparseable_device',
        message: `glass pane at ${cellKey(cell.pos)} is not on a perimeter wall`,
        pos: cell.pos,
      });
    } else {
      exposed.push({ cell, side });
    }
  }

  const devices: Device[] = [];
  const groups = new Map<string, ExposedCell[]>();
  for (const item of exposed) {
    const key = `${item.side}:${item.cell.pos[1]}`;
    const group = groups.get(key) ?? [];
    group.push(item);
    groups.set(key, group);
  }
  for (const [key, group] of groups) {
    const side = group[0].side;
    const sorted = [...group].sort((left, right) => windowAxis(left.cell.pos, side) - windowAxis(right.cell.pos, side));
    let run: ExposedCell[] = [];
    for (const item of sorted) {
      if (run.length === 0 || windowAxis(item.cell.pos, side) === windowAxis(run[run.length - 1].cell.pos, side) + 1) {
        run.push(item);
      } else {
        devices.push(makeWindowDevice(side, Number(key.split(':')[1]), run));
        run = [item];
      }
    }
    if (run.length > 0) devices.push(makeWindowDevice(side, Number(key.split(':')[1]), run));
  }
  return devices;
}

function makeWindowDevice(side: Side, floor: number, run: ExposedCell[]): Device {
  const device: Extract<Device, { kind: 'window' }> = { kind: 'window', side, floor };
  if (run.length > 1) device.width = run.length;
  return device;
}

function extractTorches(cells: Cell[]): Device[] {
  const torches = cells.filter((cell) => TORCH_IDS.has(cell.id));
  const remaining = new Set(torches.map((cell) => cellKey(cell.pos)));
  const devices: Device[] = [];
  while (remaining.size > 0) {
    const seedKey = remaining.values().next().value as string;
    const component: Cell[] = [];
    const queue = [torches.find((cell) => cellKey(cell.pos) === seedKey) as Cell];
    remaining.delete(seedKey);
    while (queue.length > 0) {
      const current = queue.shift() as Cell;
      component.push(current);
      for (const candidate of torches) {
        const candidateKey = cellKey(candidate.pos);
        if (!remaining.has(candidateKey) || distance(current.pos, candidate.pos) > 1) continue;
        remaining.delete(candidateKey);
        queue.push(candidate);
      }
    }
    const centroid: Coord = [
      Math.round(component.reduce((sum, cell) => sum + cell.pos[0], 0) / component.length),
      Math.round(component.reduce((sum, cell) => sum + cell.pos[1], 0) / component.length),
      Math.round(component.reduce((sum, cell) => sum + cell.pos[2], 0) / component.length),
    ];
    devices.push({ kind: 'torch', pos: centroid });
  }
  return devices;
}

function extractLadders(
  cells: Cell[],
  grid: Map<string, Cell>,
  size: [number, number, number],
  warnings: SerializationWarning[],
): Device[] {
  const devices: Device[] = [];
  for (const cell of cells.filter((candidate) => candidate.id.endsWith('_ladder') || candidate.id === 'ladder')) {
    const side = chooseSide(cell, grid, size);
    if (side === null) {
      warnings.push({
        rule: 'unparseable_device',
        message: `ladder at ${cellKey(cell.pos)} is not on a perimeter wall`,
        pos: cell.pos,
      });
      continue;
    }
    devices.push({ kind: 'ladder', side, pos: cell.pos });
  }
  return devices;
}

function extractLevers(
  cells: Cell[],
  size: [number, number, number],
  warnings: SerializationWarning[],
): Device[] {
  const devices: Device[] = [];
  for (const cell of cells.filter((candidate) => candidate.id === 'lever')) {
    if (!inBox(cell.pos, size)) {
      warnings.push({
        rule: 'unparseable_device',
        message: `lever at ${cellKey(cell.pos)} is outside the footprint`,
        pos: cell.pos,
      });
      continue;
    }
    devices.push({ kind: 'lever', pos: cell.pos });
  }
  return devices;
}

function extractFlowerPots(
  cells: Cell[],
  size: [number, number, number],
  warnings: SerializationWarning[],
): Device[] {
  const devices: Device[] = [];
  for (const cell of cells.filter((candidate) => POT_IDS.has(candidate.id) || candidate.id.startsWith(POTTED_PREFIX))) {
    if (!inBox(cell.pos, size)) {
      warnings.push({
        rule: 'unparseable_device',
        message: `flower_pot at ${cellKey(cell.pos)} is outside the footprint`,
        pos: cell.pos,
      });
      continue;
    }
    devices.push({ kind: 'flower_pot', pos: cell.pos });
  }
  return devices;
}

function extractBeds(
  cells: Cell[],
  grid: Map<string, Cell>,
  size: [number, number, number],
  warnings: SerializationWarning[],
): Device[] {
  const devices: Device[] = [];
  const visited = new Set<string>();
  for (const cell of cells.filter((candidate) => BED_IDS.has(candidate.id))) {
    const key = cellKey(cell.pos);
    if (visited.has(key)) continue;
    const component = cells.filter((candidate) => {
      const [x, , z] = candidate.pos;
      return BED_IDS.has(candidate.id) && x === cell.pos[0] && z === cell.pos[2];
    });
    for (const member of component) visited.add(cellKey(member.pos));
    const exposed = pickExposedCell(component, grid, size);
    if (!exposed) {
      warnings.push({
        rule: 'unparseable_device',
        message: `bed at ${cellKey(cell.pos)} has no exposed perimeter face`,
        pos: cell.pos,
      });
      continue;
    }
    devices.push({ kind: 'bed', side: exposed.side, floor: Math.min(...component.map((member) => member.pos[1])) });
  }
  return devices;
}

function extractChests(
  cells: Cell[],
  grid: Map<string, Cell>,
  size: [number, number, number],
  warnings: SerializationWarning[],
): Device[] {
  const devices: Device[] = [];
  for (const cell of cells.filter((candidate) => candidate.id === 'chest' || candidate.id === 'trapped_chest')) {
    const exposed = pickExposedCell([cell], grid, size);
    if (!exposed) {
      warnings.push({
        rule: 'unparseable_device',
        message: `chest at ${cellKey(cell.pos)} has no exposed perimeter face`,
        pos: cell.pos,
      });
      continue;
    }
    devices.push({ kind: 'chest', side: exposed.side, floor: cell.pos[1] });
  }
  return devices;
}

function extractBarrels(
  cells: Cell[],
  grid: Map<string, Cell>,
  size: [number, number, number],
  warnings: SerializationWarning[],
): Device[] {
  const devices: Device[] = [];
  for (const cell of cells.filter((candidate) => candidate.id === 'barrel')) {
    const exposed = pickExposedCell([cell], grid, size);
    if (!exposed) {
      warnings.push({
        rule: 'unparseable_device',
        message: `barrel at ${cellKey(cell.pos)} has no exposed perimeter face`,
        pos: cell.pos,
      });
      continue;
    }
    devices.push({ kind: 'barrel', side: exposed.side, floor: cell.pos[1] });
  }
  return devices;
}

function extractCandles(
  cells: Cell[],
  size: [number, number, number],
  warnings: SerializationWarning[],
): Device[] {
  const devices: Device[] = [];
  for (const cell of cells.filter((candidate) => CANDLE_IDS.has(candidate.id))) {
    if (!inBox(cell.pos, size)) {
      warnings.push({
        rule: 'unparseable_device',
        message: `candle at ${cellKey(cell.pos)} is outside the footprint`,
        pos: cell.pos,
      });
      continue;
    }
    devices.push({ kind: 'candle', pos: cell.pos });
  }
  return devices;
}

function extractCampfires(
  cells: Cell[],
  size: [number, number, number],
  warnings: SerializationWarning[],
): Device[] {
  const devices: Device[] = [];
  for (const cell of cells.filter((candidate) => CAMPFIRE_IDS.has(candidate.id))) {
    if (!inBox(cell.pos, size)) {
      warnings.push({
        rule: 'unparseable_device',
        message: `campfire at ${cellKey(cell.pos)} is outside the footprint`,
        pos: cell.pos,
      });
      continue;
    }
    devices.push({ kind: 'campfire', pos: cell.pos });
  }
  return devices;
}

function extractFurnaces(
  cells: Cell[],
  grid: Map<string, Cell>,
  size: [number, number, number],
  warnings: SerializationWarning[],
): Device[] {
  const devices: Device[] = [];
  for (const cell of cells.filter((candidate) => FURNACE_IDS.has(candidate.id))) {
    const exposed = pickExposedCell([cell], grid, size);
    if (!exposed) {
      warnings.push({
        rule: 'unparseable_device',
        message: `furnace at ${cellKey(cell.pos)} has no exposed perimeter face`,
        pos: cell.pos,
      });
      continue;
    }
    devices.push({ kind: 'furnace', side: exposed.side, floor: cell.pos[1] });
  }
  return devices;
}

function inBox(pos: Coord, size: [number, number, number]): boolean {
  const [x, y, z] = pos;
  return x >= 0 && x < size[0] && y >= 0 && y < size[1] && z >= 0 && z < size[2];
}

function extractExternalStairs(
  cells: Cell[],
  grid: Map<string, Cell>,
  size: [number, number, number],
): Device[] {
  const candidates = cells.filter((cell) => {
    if (!cell.id.endsWith('_stairs') || !hasAirColumnAbove(cell, grid, size)) return false;
    const side = stairSide(cell, size);
    return side !== null && cell.properties.facing === side;
  });
  const remaining = new Set(candidates.map((cell) => cellKey(cell.pos)));
  const devices: Device[] = [];
  while (remaining.size > 0) {
    const seedKey = remaining.values().next().value as string;
    const seed = candidates.find((cell) => cellKey(cell.pos) === seedKey) as Cell;
    const side = stairSide(seed, size) as Side;
    const component: Cell[] = [];
    const queue = [seed];
    remaining.delete(seedKey);
    while (queue.length > 0) {
      const current = queue.shift() as Cell;
      component.push(current);
      for (const candidate of candidates) {
        const candidateKey = cellKey(candidate.pos);
        if (
          !remaining.has(candidateKey)
          || stairSide(candidate, size) !== side
          || !stairCellsTouch(current, candidate)
        ) continue;
        remaining.delete(candidateKey);
        queue.push(candidate);
      }
    }
    const ys = component.map((cell) => cell.pos[1]);
    const startY = Math.min(...ys);
    const endY = Math.max(...ys);
    if (startY < endY) devices.push({ kind: 'external_stair', side, start_y: startY, end_y: endY });
  }
  return devices;
}

function pickExposedCell(group: Cell[], grid: Map<string, Cell>, size: [number, number, number]): ExposedCell | null {
  for (const cell of group) {
    const side = chooseSide(cell, grid, size);
    if (side !== null) return { cell, side };
  }
  return null;
}

function chooseSide(cell: Cell, grid: Map<string, Cell>, size: [number, number, number]): Side | null {
  const [x, , z] = cell.pos;
  const candidates = exposedSides(x, z, size).filter((side) => {
    const [dx, dz] = sideOffset(side);
    return isAirAt(grid, x + dx, cell.pos[1], z + dz, size);
  });
  return SIDE_ORDER.find((side) => candidates.includes(side)) ?? null;
}

function exposedSides(x: number, z: number, size: [number, number, number]): Side[] {
  const [width, , depth] = size;
  const sides: Side[] = [];
  if (z === 0) sides.push('north');
  if (z === depth - 1) sides.push('south');
  if (x === 0) sides.push('west');
  if (x === width - 1) sides.push('east');
  return sides;
}

function stairSide(cell: Cell, size: [number, number, number]): Side | null {
  const facing = cell.properties.facing;
  if (facing !== 'north' && facing !== 'south' && facing !== 'east' && facing !== 'west') return null;
  return exposedSides(cell.pos[0], cell.pos[2], size).includes(facing) ? facing : null;
}

function hasAirColumnAbove(cell: Cell, grid: Map<string, Cell>, size: [number, number, number]): boolean {
  for (let y = cell.pos[1] + 1; y < size[1]; y++) {
    if (!isAirAt(grid, cell.pos[0], y, cell.pos[2], size)) return false;
  }
  return true;
}

function stairCellsTouch(left: Cell, right: Cell): boolean {
  const dx = Math.abs(left.pos[0] - right.pos[0]);
  const dy = Math.abs(left.pos[1] - right.pos[1]);
  const dz = Math.abs(left.pos[2] - right.pos[2]);
  return dy <= 1 && dx + dz <= 1 && (dx + dy + dz > 0);
}

function windowAxis(pos: Coord, side: Side): number {
  return side === 'north' || side === 'south' ? pos[0] : pos[2];
}

function isAirAt(
  grid: Map<string, Cell>,
  x: number,
  y: number,
  z: number,
  size: [number, number, number],
): boolean {
  if (x < 0 || y < 0 || z < 0 || x >= size[0] || y >= size[1] || z >= size[2]) return true;
  const cell = grid.get(`${x},${y},${z}`);
  return cell === undefined || isAirId(cell.id);
}

function isEphemeralDevice(id: string): boolean {
  return id.endsWith('_door') || id === 'glass_pane' || TORCH_IDS.has(id)
    || POT_IDS.has(id) || id.startsWith(POTTED_PREFIX)
    || BED_IDS.has(id) || id === 'chest' || id === 'trapped_chest' || id === 'barrel'
    || CANDLE_IDS.has(id) || CAMPFIRE_IDS.has(id) || FURNACE_IDS.has(id)
    || id === 'lever' || id === 'ladder' || id.endsWith('_ladder');
}

function isAirId(id: string): boolean {
  return id === 'air' || id === 'cave_air' || id === 'void_air';
}

function classifyBlock(id: string): 'floor' | 'device' | 'ignored' {
  if (IGNORED_IDS.has(id)) return 'ignored';
  if (isDevicePattern(id)) return 'device';
  return 'floor';
}

function isDevicePattern(id: string): boolean {
  if (id.endsWith('_door')) return true;
  if (id.endsWith('_trapdoor')) return true;
  if (id.endsWith('_pressure_plate')) return true;
  if (id === 'glass_pane') return true;
  if (id.endsWith('_ladder')) return true;
  if (id === 'lever') return true;
  if (POT_IDS.has(id) || id.startsWith(POTTED_PREFIX)) return true;
  if (BED_IDS.has(id)) return true;
  if (id === 'chest' || id === 'trapped_chest') return true;
  if (id === 'barrel') return true;
  if (CANDLE_IDS.has(id)) return true;
  if (CAMPFIRE_IDS.has(id)) return true;
  if (FURNACE_IDS.has(id)) return true;
  if (TORCH_IDS.has(id)) return true;
  if (id.endsWith('_stairs')) return true;
  return false;
}

function materialForCell(
  id: string,
  pos: Coord,
  warnings: SerializationWarning[],
  unknownIds: Set<string>,
): MaterialId {
  const material = knownMaterial(id);
  if (material !== null) return material;
  if (isEphemeralDevice(id)) return 'oak_planks';
  if (!unknownIds.has(id)) {
    unknownIds.add(id);
    warnings.push({
      rule: 'unknown_block',
      message: `unknown block "${id}"; using oak_planks`,
      pos,
    });
  }
  return 'oak_planks';
}

function knownMaterial(id: string): MaterialId | null {
  if (id === 'oak_planks') return 'oak_planks';
  if (id === 'oak_log') return 'oak_log';
  if (id === 'oak_slab') return 'oak_slab';
  if (id === 'oak_stairs') return 'oak_stairs';
  if (id === 'oak_fence') return 'oak_fence';
  if (id === 'oak_door') return 'oak_door';
  if (id === 'oak_trapdoor') return 'oak_trapdoor';
  if (id === 'oak_pressure_plate') return 'oak_pressure_plate';
  if (id === 'oak_leaves') return 'oak_leaves';
  if (id === 'stripped_oak_log') return 'stripped_oak_log';
  if (id.startsWith('oak_')) return 'oak_planks';
  if (id === 'cobblestone') return 'cobblestone';
  if (id === 'cobblestone_slab') return 'cobblestone_slab';
  if (id === 'cobblestone_stairs') return 'cobblestone_stairs';
  if (id === 'cobblestone_wall') return 'cobblestone_wall';
  if (id === 'mossy_cobblestone') return 'mossy_cobblestone';
  if (id === 'stone') return 'stone';
  if (id === 'stone_slab') return 'stone_slab';
  if (id === 'stone_stairs') return 'stone_stairs';
  if (id === 'stone_bricks') return 'stone_bricks';
  if (id === 'stone_brick_slab') return 'stone_brick_slab';
  if (id === 'stone_brick_stairs') return 'stone_brick_stairs';
  if (id === 'stone_brick_wall') return 'stone_brick_wall';
  if (id === 'stone_bricks_slab') return 'stone_brick_slab';
  if (id === 'stone_bricks_stairs') return 'stone_brick_stairs';
  if (id === 'stone_bricks_wall') return 'stone_brick_wall';
  if (id === 'andesite') return 'andesite';
  if (id === 'dirt' || id === 'coarse_dirt' || id === 'dirt_path') return 'dirt';
  if (id === 'grass_block') return 'grass_block';
  if (id === 'farmland') return 'farmland';
  if (id === 'mud') return 'mud';
  if (id === 'podzol') return 'podzol';
  if (id === 'rooted_dirt') return 'rooted_dirt';
  if (id === 'smooth_stone') return 'smooth_stone';
  if (id === 'white_terracotta') return 'white_terracotta';
  if (id === 'glass_pane') return 'glass_pane';
  if (id === 'iron_bars') return 'iron_bars';
  if (id === 'hay_block') return 'hay_block';
  if (id === 'bookshelf') return 'bookshelf';
  if (id === 'chiseled_bookshelf') return 'chiseled_bookshelf';
  if (id === 'bamboo_block') return 'bamboo_block';
  if (id === 'bamboo_planks') return 'bamboo_planks';
  if (id === 'bamboo_mosaic') return 'bamboo_mosaic';
  if (id === 'moss_block') return 'moss_block';
  if (id === 'moss_carpet') return 'moss_carpet';
  if (id === 'hanging_roots') return 'hanging_roots';
  if (id === 'white_wool') return 'white_wool';
  if (id === 'yellow_wool') return 'yellow_wool';
  if (id === 'red_wool') return 'red_wool';
  if (id === 'brown_wool') return 'brown_wool';
  if (id === 'lime_wool') return 'lime_wool';
  if (id === 'composter') return 'composter';
  if (id === 'barrel') return 'barrel';
  if (id === 'crafting_table') return 'crafting_table';
  if (id === 'cartography_table') return 'cartography_table';
  if (id === 'fletching_table') return 'fletching_table';
  if (id === 'smithing_table') return 'smithing_table';
  if (id === 'loom') return 'loom';
  if (id === 'jukebox') return 'jukebox';
  if (id === 'note_block') return 'note_block';
  if (id === 'bell') return 'bell';
  if (id === 'candle' || CANDLE_IDS.has(id)) return 'candle';
  if (id === 'flower_pot') return 'flower_pot';
  if (id === 'lantern') return 'lantern';
  if (id === 'soul_lantern') return 'soul_lantern';
  if (id === 'soul_torch') return 'soul_torch';
  if (id === 'torch' || TORCH_IDS.has(id)) return 'torch';
  return null;
}

function stripNamespace(id: string): string {
  return id.startsWith('minecraft:') ? id.slice('minecraft:'.length) : id;
}

function compareCells(left: Cell, right: Cell): number {
  return left.pos[1] - right.pos[1] || left.pos[2] - right.pos[2] || left.pos[0] - right.pos[0];
}

function cellKey(pos: Coord): string {
  return `${pos[0]},${pos[1]},${pos[2]}`;
}

function sideOffset(side: Side): [number, number] {
  switch (side) {
    case 'north': return [0, -1];
    case 'south': return [0, 1];
    case 'west': return [-1, 0];
    case 'east': return [1, 0];
  }
}

function distance(left: Coord, right: Coord): number {
  return Math.sqrt(
    (left[0] - right[0]) ** 2
      + (left[1] - right[1]) ** 2
      + (left[2] - right[2]) ** 2,
  );
}

function inlineArray(values: readonly (number | string)[]): string {
  return `[${values.map((value) => typeof value === 'number' ? String(value) : yamlScalar(value)).join(', ')}]`;
}

function yamlScalar(value: string): string {
  if (/^[A-Za-z0-9_./-]+$/.test(value) && !['true', 'false', 'null', '~'].includes(value)) return value;
  return JSON.stringify(value);
}
