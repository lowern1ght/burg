import { describe, expect, it } from 'vitest';
import { Structure, BlockPos } from '@mattzh72/lodestone';
import {
  loadPlan,
  serializePlanToYaml,
  serializeStructureToPlan,
  validatePlan,
} from './index';

type Coord = [number, number, number];

type AddBlockSpec = {
  pos: Coord;
  id: string;
  properties?: Record<string, string>;
};

function buildStructure(size: [number, number, number], blocks: AddBlockSpec[]): Structure {
  const structure = new Structure(BlockPos.create(size[0], size[1], size[2]));
  for (const block of blocks) {
    const id = block.id.includes(':') ? block.id : `minecraft:${block.id}`;
    structure.addBlock(
      BlockPos.create(block.pos[0], block.pos[1], block.pos[2]),
      id,
      block.properties,
    );
  }
  return structure;
}

function fillBox(
  size: [number, number, number],
  id: string,
  start: Coord = [0, 0, 0],
  end?: Coord,
): AddBlockSpec[] {
  const [ex, ey, ez] = end ?? [size[0] - 1, size[1] - 1, size[2] - 1];
  const blocks: AddBlockSpec[] = [];
  for (let y = start[1]; y <= ey; y++) {
    for (let z = start[2]; z <= ez; z++) {
      for (let x = start[0]; x <= ex; x++) {
        blocks.push({ pos: [x, y, z], id });
      }
    }
  }
  return blocks;
}

function wallsAndFloor(size: [number, number, number], id: string): AddBlockSpec[] {
  const blocks: AddBlockSpec[] = [];
  for (let y = 1; y < size[1]; y++) {
    for (let x = 0; x < size[0]; x++) {
      blocks.push({ pos: [x, y, 0], id });
      blocks.push({ pos: [x, y, size[2] - 1], id });
    }
    for (let z = 1; z < size[2] - 1; z++) {
      blocks.push({ pos: [0, y, z], id });
      blocks.push({ pos: [size[0] - 1, y, z], id });
    }
  }
  for (let x = 1; x < size[0] - 1; x++) {
    for (let z = 1; z < size[2] - 1; z++) {
      blocks.push({ pos: [x, 1, z], id });
    }
  }
  return blocks;
}

describe('dsl — serializeStructureToPlan', () => {
  it('serialises a uniform 5x5x5 oak box as a single ground floor', () => {
    const size: [number, number, number] = [5, 5, 5];
    const structure = buildStructure(size, fillBox(size, 'oak_planks'));

    const result = serializeStructureToPlan(structure, { name: 'cube' });

    expect(result.valid).toBe(true);
    expect(result.plan.structure.footprint).toEqual([5, 5]);
    expect(result.plan.structure.height).toBe(5);
    expect(result.plan.floors).toEqual([
      { range: [0, 4], material: 'oak_planks', layout: 'ground' },
    ]);
    expect(result.plan.devices).toEqual([]);
    expect(result.warnings).toEqual([]);
  });

  it('serialises a 7x7x9 house with door, windows, and a torch', () => {
    const size: [number, number, number] = [7, 7, 9];
    const blocks: AddBlockSpec[] = [];
    // Two storey walls + a stone upper course.
    for (let y = 1; y < 3; y++) {
      for (const block of wallsAndFloor(size, 'oak_planks')) {
        if (block.pos[1] === y) blocks.push(block);
      }
    }
    for (let y = 3; y < 6; y++) {
      for (let x = 0; x < size[0]; x++) {
        blocks.push({ pos: [x, y, 0], id: 'stone_bricks' });
        blocks.push({ pos: [x, y, size[2] - 1], id: 'stone_bricks' });
      }
      for (let z = 1; z < size[2] - 1; z++) {
        blocks.push({ pos: [0, y, z], id: 'stone_bricks' });
        blocks.push({ pos: [size[0] - 1, y, z], id: 'stone_bricks' });
      }
    }
    // Roof: stair cells along the long axis at the top.
    for (let x = 0; x < size[0]; x++) {
      blocks.push({ pos: [x, 6, 0], id: 'cobblestone_stairs', properties: { facing: 'south', half: 'bottom' } });
      blocks.push({ pos: [x, 6, size[2] - 1], id: 'cobblestone_stairs', properties: { facing: 'north', half: 'bottom' } });
    }
    // Door on the south face at floor 1.
    blocks.push({
      pos: [Math.floor(size[0] / 2), 1, size[2] - 1],
      id: 'oak_door',
      properties: { facing: 'south', half: 'lower', hinge: 'left' },
    });
    blocks.push({
      pos: [Math.floor(size[0] / 2), 2, size[2] - 1],
      id: 'oak_door',
      properties: { facing: 'south', half: 'upper', hinge: 'left' },
    });
    // Two windows on the south face at floor 2.
    for (const x of [Math.floor(size[0] / 2) - 1, Math.floor(size[0] / 2) + 1]) {
      blocks.push({ pos: [x, 2, size[2] - 1], id: 'glass_pane' });
    }
    // One torch mounted on a wall.
    blocks.push({ pos: [1, 2, 1], id: 'torch' });

    const structure = buildStructure(size, blocks);
    const result = serializeStructureToPlan(structure, {
      name: 'house',
      outputPath: 'houses/simple/',
    });

    expect(result.valid).toBe(true);
    expect(result.plan.output).toEqual({ name: 'house', path: 'houses/simple/' });
    // Floors collapse as: y=0 (gap-filled) + y=1..2 oak_planks → ground; y=3..5
    // stone_bricks → residential; y=6 stair-only course → battlements (the
    // serializer maps cobblestone_stairs to its own MaterialId and treats the
    // row as battlements because it contains a stair cell).
    expect(result.plan.floors.map((floor) => floor.material)).toEqual([
      'oak_planks',
      'stone_bricks',
      'cobblestone_stairs',
    ]);
    expect(result.plan.floors.map((floor) => floor.layout)).toEqual([
      'ground',
      'residential',
      'battlements',
    ]);
    expect(result.plan.devices).toHaveLength(4);
    const kinds = result.plan.devices.map((device) => device.kind).sort();
    expect(kinds).toEqual(['door', 'torch', 'window', 'window']);
  });

  it('emits an unknown_block warning for unmapped blocks and still validates', () => {
    const size: [number, number, number] = [3, 1, 3];
    const blocks = fillBox(size, 'oak_planks');
    blocks.push({ pos: [1, 0, 1], id: 'minecraft:foo' });

    const structure = buildStructure(size, blocks);
    const result = serializeStructureToPlan(structure, { name: 'odd' });

    expect(result.valid).toBe(true);
    const unknownWarnings = result.warnings.filter((warning) => warning.rule === 'unknown_block');
    expect(unknownWarnings).toHaveLength(1);
    expect(unknownWarnings[0].message).toContain('foo');
    expect(unknownWarnings[0].pos).toEqual([1, 0, 1]);
  });

  it('round-trips a structure through YAML with no semantic drift', () => {
    const size: [number, number, number] = [5, 4, 5];
    const structure = buildStructure(size, wallsAndFloor(size, 'oak_planks'));
    structure.addBlock(
      BlockPos.create(Math.floor(size[0] / 2), 1, size[2] - 1),
      'minecraft:oak_door',
      { facing: 'south', half: 'lower', hinge: 'left' },
    );
    structure.addBlock(
      BlockPos.create(Math.floor(size[0] / 2), 2, size[2] - 1),
      'minecraft:oak_door',
      { facing: 'south', half: 'upper', hinge: 'left' },
    );
    structure.addBlock(BlockPos.create(1, 2, 1), 'minecraft:torch');

    const first = serializeStructureToPlan(structure, { name: 'rt' });
    const yaml = serializePlanToYaml(first.plan);
    const reloaded = loadPlan(yaml);
    const revalidation = validatePlan(reloaded);

    expect(revalidation.ok).toBe(true);
    expect(reloaded.floors).toEqual(first.plan.floors);
    expect(reloaded.devices).toEqual(first.plan.devices);
    expect(reloaded.rules).toEqual(first.plan.rules);
  });

  it('flags an unparseable door buried inside a wall with no exposed side', () => {
    const size: [number, number, number] = [3, 3, 3];
    // Surround a door cell on every horizontal neighbour with planks so it has
    // no air neighbour — pickExposedCell must report unparseable_device. The
    // door is added LAST so the fillBox planks do not overwrite it.
    const blocks: AddBlockSpec[] = [
      ...fillBox(size, 'oak_planks'),
      { pos: [0, 1, 1], id: 'oak_planks' },
      { pos: [2, 1, 1], id: 'oak_planks' },
      { pos: [1, 1, 0], id: 'oak_planks' },
      { pos: [1, 1, 2], id: 'oak_planks' },
      { pos: [1, 1, 1], id: 'oak_door' },
    ];
    const structure = buildStructure(size, blocks);
    const result = serializeStructureToPlan(structure, { name: 'buried' });

    expect(result.warnings.some((warning) => warning.rule === 'unparseable_device')).toBe(true);
  });
});
