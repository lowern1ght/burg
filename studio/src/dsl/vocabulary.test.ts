import { describe, expect, it } from 'vitest';
import {
  analyse,
  describe as describeVocab,
  harvest,
  merge,
  pickFromList,
  toBlockState,
  type BlockState,
  type Vocabulary,
} from './vocabulary';
import { makeGrid, type GridEntry } from '../engine/test-utils';

function buildGrid(entries: GridEntry[]): ReturnType<typeof makeGrid> {
  const [w, h, d] = sizeFromEntries(entries);
  return makeGrid([w, h, d], entries);
}

function sizeFromEntries(entries: GridEntry[]): [number, number, number] {
  let w = 0, h = 0, d = 0;
  for (const e of entries) {
    if (e.pos[0] + 1 > w) w = e.pos[0] + 1;
    if (e.pos[1] + 1 > h) h = e.pos[1] + 1;
    if (e.pos[2] + 1 > d) d = e.pos[2] + 1;
  }
  return [Math.max(1, w), Math.max(1, h), Math.max(1, d)];
}

/** Build a complete 5x5x5 watchtower-style donor: ground apron, stone + oak
 *  walls, oak door, glass window, oak log corners, oak slab cap, stone slab
 *  string course, roof stairs on every side. */
function buildTowerDonor(): ReturnType<typeof makeGrid> {
  const size: [number, number, number] = [5, 6, 5];
  const entries: GridEntry[] = [];

  // y=0 — grass apron with a dirt path on the south face.
  for (let x = 0; x < 5; x++) {
    for (let z = 0; z < 5; z++) {
      entries.push({ pos: [x, 0, z], id: 'grass_block' });
    }
  }
  entries.push({ pos: [2, 0, 4], id: 'dirt_path' });

  // y=1 — stone foundation footprint.
  for (let x = 1; x < 4; x++) {
    for (let z = 1; z < 4; z++) {
      entries.push({ pos: [x, 1, z], id: 'cobblestone' });
    }
  }

  // y=2..3 — oak walls (perimeter).
  for (let y = 2; y <= 3; y++) {
    for (let x = 0; x < 5; x++) {
      entries.push({ pos: [x, y, 0], id: 'oak_planks' });
      entries.push({ pos: [x, y, 4], id: 'oak_planks' });
    }
    for (let z = 1; z < 4; z++) {
      entries.push({ pos: [0, y, z], id: 'oak_planks' });
      entries.push({ pos: [4, y, z], id: 'oak_planks' });
    }
  }

  // Corner posts: oak_log{axis=y} on all four corners, full height.
  for (let y = 1; y <= 4; y++) {
    for (const [x, z] of [[0, 0], [4, 0], [0, 4], [4, 4]] as Array<[number, number]>) {
      entries.push({ pos: [x, y, z], id: 'oak_log', props: { axis: 'y' } });
    }
  }

  // Door on the south face at y=2..3.
  entries.push({ pos: [2, 2, 4], id: 'oak_door', props: { half: 'lower', facing: 'south' } });
  entries.push({ pos: [2, 3, 4], id: 'oak_door', props: { half: 'upper', facing: 'south' } });

  // Window on the west face at y=2.
  entries.push({ pos: [0, 2, 2], id: 'glass_pane' });

  // Interior floor at y=2 (oak planks in the middle).
  for (const [x, z] of [[1, 1], [1, 2], [1, 3], [2, 1], [2, 3], [3, 1], [3, 2], [3, 3]] as Array<[number, number]>) {
    entries.push({ pos: [x, 2, z], id: 'oak_planks' });
  }

  // String course — cobblestone slabs at y=4 (top of wall).
  for (let x = 0; x < 5; x++) {
    entries.push({ pos: [x, 4, 0], id: 'cobblestone_slab', props: { type: 'top' } });
    entries.push({ pos: [x, 4, 4], id: 'cobblestone_slab', props: { type: 'top' } });
  }
  for (let z = 1; z < 4; z++) {
    entries.push({ pos: [0, 4, z], id: 'cobblestone_slab', props: { type: 'top' } });
    entries.push({ pos: [4, 4, z], id: 'cobblestone_slab', props: { type: 'top' } });
  }

  // Roof — oak stairs on the perimeter at y=5, one facing per side.
  for (let x = 0; x < 5; x++) {
    entries.push({ pos: [x, 5, 0], id: 'oak_stairs', props: { facing: 'south', half: 'bottom' } });
    entries.push({ pos: [x, 5, 4], id: 'oak_stairs', props: { facing: 'north', half: 'bottom' } });
  }
  for (let z = 1; z < 4; z++) {
    entries.push({ pos: [0, 5, z], id: 'oak_stairs', props: { facing: 'east', half: 'bottom' } });
    entries.push({ pos: [4, 5, z], id: 'oak_stairs', props: { facing: 'west', half: 'bottom' } });
  }
  // Ridge — oak_slab top.
  entries.push({ pos: [2, 5, 2], id: 'oak_slab', props: { type: 'top' } });

  return makeGrid(size, entries);
}

describe('dsl — vocabulary.analyse', () => {
  it('classifies a 1x1x1 empty structure as no-op', () => {
    const grid = makeGrid([1, 1, 1]);
    const a = analyse(grid);
    expect(a.groundTop).toBe(0);
    expect(a.wallLo).toBe(0);
    expect(a.wallHi).toBe(0);
    expect(a.shell).toEqual([0, 0, 0, 0]);
  });

  it('detects a 1-cell-tall terrain apron as ground', () => {
    const grid = buildGrid([{ pos: [0, 0, 0], id: 'grass_block' }]);
    const a = analyse(grid);
    expect(a.groundTop).toBe(0);
    expect(a.wallLo).toBe(1);
  });

  it('detects roof zone from a stair-dominated top layer', () => {
    const grid = makeGrid([5, 4, 5]);
    // Ground layer — terrain.
    for (let x = 0; x < 5; x++) {
      for (let z = 0; z < 5; z++) {
        (grid as { set: (x: number, y: number, z: number, b: { id: string; props: Record<string, string> }) => void }).set(x, 0, z, { id: 'grass_block', props: {} });
      }
    }
    // Two wall layers — perimeter oak_planks.
    for (let y = 1; y <= 2; y++) {
      for (let x = 0; x < 5; x++) {
        (grid as { set: (x: number, y: number, z: number, b: { id: string; props: Record<string, string> }) => void }).set(x, y, 0, { id: 'oak_planks', props: {} });
        (grid as { set: (x: number, y: number, z: number, b: { id: string; props: Record<string, string> }) => void }).set(x, y, 4, { id: 'oak_planks', props: {} });
      }
      for (let z = 1; z < 4; z++) {
        (grid as { set: (x: number, y: number, z: number, b: { id: string; props: Record<string, string> }) => void }).set(0, y, z, { id: 'oak_planks', props: {} });
        (grid as { set: (x: number, y: number, z: number, b: { id: string; props: Record<string, string> }) => void }).set(4, y, z, { id: 'oak_planks', props: {} });
      }
    }
    // Roof layer — stairs on every perimeter cell.
    for (let x = 0; x < 5; x++) {
      (grid as { set: (x: number, y: number, z: number, b: { id: string; props: Record<string, string> }) => void }).set(x, 3, 0, { id: 'oak_stairs', props: { facing: 'south', half: 'bottom' } });
      (grid as { set: (x: number, y: number, z: number, b: { id: string; props: Record<string, string> }) => void }).set(x, 3, 4, { id: 'oak_stairs', props: { facing: 'north', half: 'bottom' } });
    }
    for (let z = 1; z < 4; z++) {
      (grid as { set: (x: number, y: number, z: number, b: { id: string; props: Record<string, string> }) => void }).set(0, 3, z, { id: 'oak_stairs', props: { facing: 'east', half: 'bottom' } });
      (grid as { set: (x: number, y: number, z: number, b: { id: string; props: Record<string, string> }) => void }).set(4, 3, z, { id: 'oak_stairs', props: { facing: 'west', half: 'bottom' } });
    }
    const a = analyse(grid);
    expect(a.groundTop).toBe(0);
    expect(a.wallLo).toBe(1);
    expect(a.wallHi).toBe(2);
    expect(a.roofLo).toBe(3);
  });
});

describe('dsl — vocabulary.harvest', () => {
  it('lifts an apron, a stone, a timber, and a window from a 5x5x5 tower', () => {
    const donor = buildTowerDonor();
    const v = harvest(donor, 'tower');
    expect(v.donor).toBe('tower');
    expect(v.apron.length).toBeGreaterThan(0);
    expect(v.stone).toContainEqual(toBlockState({ id: 'cobblestone', props: {} }));
    expect(v.timber).toContainEqual(toBlockState({ id: 'oak_planks', props: {} }));
    expect(v.window.length).toBeGreaterThan(0);
  });

  it('collects the four corner posts and a fence from the donor', () => {
    const v = harvest(buildTowerDonor(), 'tower');
    expect(v.post).not.toBeNull();
    expect(v.post?.id).toBe('oak_log');
    expect(v.post?.properties.axis).toBe('y');
  });

  it('records lower and upper door halves separately', () => {
    const v = harvest(buildTowerDonor(), 'tower');
    expect(v.door_lower.length).toBeGreaterThan(0);
    expect(v.door_lower[0].properties.half).toBe('lower');
    expect(v.door_upper.length).toBeGreaterThan(0);
    expect(v.door_upper[0].properties.half).toBe('upper');
  });

  it('classifies a perimeter stair as roof_stairs, not stairs', () => {
    const v = harvest(buildTowerDonor(), 'tower');
    expect(v.roof_stairs.length).toBeGreaterThan(0);
    expect(v.stairs.length).toBe(0);
  });

  it('records the four most-common top slabs for the string course', () => {
    const v = harvest(buildTowerDonor(), 'tower');
    expect(v.stone_slab_top).not.toBeNull();
    expect(v.stone_slab_top?.id).toBe('cobblestone_slab');
    expect(v.stone_slab_top?.properties.type).toBe('top');
  });

  it('falls back to defaults when the donor is empty', () => {
    const donor = makeGrid([2, 2, 2]);
    const v = harvest(donor, 'empty');
    expect(v.donor).toBe('empty');
    expect(v.post).not.toBeNull();
    expect(v.post?.id).toBe('oak_log');
    expect(v.stone.length).toBeGreaterThan(0);
    expect(v.window.length).toBeGreaterThan(0);
  });

  it('uses donor to populate vegetation when present', () => {
    const donor = buildGrid([
      { pos: [0, 0, 0], id: 'grass_block' },
      { pos: [1, 0, 1], id: 'oak_leaves', props: { persistent: 'true' } },
    ]);
    const v = harvest(donor, 'tree');
    expect(v.vegetation).toContainEqual(toBlockState({ id: 'oak_leaves', props: { persistent: 'true' } }));
  });
});

describe('dsl — vocabulary.merge', () => {
  const primary: Vocabulary = {
    donor: 'house',
    apron: [{ id: 'grass_block', properties: {} }],
    floor: [],
    stone: [{ id: 'cobblestone', properties: {} }],
    timber: [{ id: 'oak_planks', properties: {} }],
    post: { id: 'oak_log', properties: { axis: 'y' } },
    slab_top: { id: 'oak_slab', properties: { type: 'top' } },
    slab_bottom: { id: 'oak_slab', properties: { type: 'bottom' } },
    stone_slab_top: null,
    window: [{ id: 'glass_pane', properties: {} }],
    fence: null,
    crenel: { id: 'cobblestone', properties: {} },
    stairs: [],
    light: [],
    door_lower: [{ id: 'oak_door', properties: { half: 'lower' } }],
    door_upper: [{ id: 'oak_door', properties: { half: 'upper' } }],
    rail: [],
    roof_stairs: [],
    beam: [],
    hanging: [],
    vegetation: [],
    decoration: [],
  };

  it('keeps primary roles when both are filled', () => {
    const secondary: Vocabulary = { ...primary, donor: 'tower', stone: [{ id: 'mossy_cobblestone', properties: {} }] };
    const merged = merge(primary, secondary);
    expect(merged.stone).toEqual(primary.stone);
    expect(merged.donor).toBe('house + tower');
  });

  it('fills empty list roles from secondary', () => {
    const secondary: Vocabulary = { ...primary, donor: 'tower', roof_stairs: [{ id: 'oak_stairs', properties: { facing: 'north' } }] };
    const merged = merge(primary, secondary);
    expect(merged.roof_stairs).toEqual(secondary.roof_stairs);
  });

  it('fills empty single roles from secondary', () => {
    const secondary: Vocabulary = { ...primary, donor: 'tower', fence: { id: 'oak_fence', properties: {} }, stone_slab_top: { id: 'cobblestone_slab', properties: { type: 'top' } } };
    const merged = merge(primary, secondary);
    expect(merged.fence).toEqual(secondary.fence);
    expect(merged.stone_slab_top).toEqual(secondary.stone_slab_top);
  });

  it('keeps null when neither vocab supplies the role', () => {
    const empty: Vocabulary = { ...primary, donor: 'empty', vegetation: [], decoration: [] };
    const merged = merge(empty);
    expect(merged.vegetation).toEqual([]);
    expect(merged.fence).toBeNull();
  });
});

describe('dsl — vocabulary helpers', () => {
  const states: BlockState[] = [
    { id: 'oak_planks', properties: {} },
    { id: 'cobblestone', properties: {} },
    { id: 'cobblestone', properties: {} },
  ];

  it('pickFromList returns null for an empty list', () => {
    expect(pickFromList([], 0)).toBeNull();
  });

  it('pickFromList returns the indexed element when in range', () => {
    expect(pickFromList(states, 0)?.id).toBe('oak_planks');
    expect(pickFromList(states, 1)?.id).toBe('cobblestone');
    expect(pickFromList(states, 2)?.id).toBe('cobblestone');
  });

  it('pickFromList wraps negative and out-of-range indices', () => {
    expect(pickFromList(states, -1)?.id).toBe('cobblestone');
    expect(pickFromList(states, 3)?.id).toBe('oak_planks');
    expect(pickFromList(states, 4)?.id).toBe('cobblestone');
  });

  it('describeVocab reports donor and role counts', () => {
    const v = harvest(buildTowerDonor(), 'tower');
    const text = describeVocab(v);
    expect(text).toContain('tower');
    expect(text).toContain('stone=');
    expect(text).toContain('timber=');
  });
});
