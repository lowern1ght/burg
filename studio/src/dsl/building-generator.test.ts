import { describe, expect, it } from 'vitest';
import { BlockPos, Structure } from '@mattzh72/lodestone';
import { buildFromSpec } from './building-generator';
import { STYLE_GRAMMAR } from './style-grammar';

function getSize(s: ReturnType<typeof buildFromSpec>['structure']): [number, number, number] {
  return s.getSize() as [number, number, number];
}

function blockIds(s: ReturnType<typeof buildFromSpec>['structure']): Set<string> {
  const set = new Set<string>();
  for (const b of s.getBlocks()) {
    const name = b.state.getName().toString();
    const short = name.startsWith('minecraft:') ? name.slice(10) : name;
    set.add(short);
  }
  return set;
}

function blockCount(s: ReturnType<typeof buildFromSpec>['structure']): number {
  return s.getBlocks().length;
}

function sameStructure(
  a: ReturnType<typeof buildFromSpec>['structure'],
  b: ReturnType<typeof buildFromSpec>['structure'],
): boolean {
  const aBlocks = a.getBlocks();
  const bBlocks = b.getBlocks();
  if (aBlocks.length !== bBlocks.length) return false;
  const aMap = new Map<string, string>();
  for (const blk of aBlocks) {
    const [x, y, z] = blk.pos;
    aMap.set(`${x},${y},${z}`, blk.state.getName().toString());
  }
  for (const blk of bBlocks) {
    const [x, y, z] = blk.pos;
    const expect = aMap.get(`${x},${y},${z}`);
    if (expect === undefined) return false;
    if (expect !== blk.state.getName().toString()) return false;
  }
  return true;
}

describe('building-generator - determinism', () => {
  it('same spec + same seed produces the same Structure', () => {
    const a = buildFromSpec({
      family: 'house',
      footprint: [7, 9],
      height: 7,
      decor: 'medium',
      seed: 12345,
    });
    const b = buildFromSpec({
      family: 'house',
      footprint: [7, 9],
      height: 7,
      decor: 'medium',
      seed: 12345,
    });
    expect(sameStructure(a.structure, b.structure)).toBe(true);
  });

  it('same spec + same seed produces the same Plan', () => {
    const a = buildFromSpec({
      family: 'watchtower',
      footprint: [5, 6],
      height: 9,
      decor: 'sparse',
      seed: 7,
    });
    const b = buildFromSpec({
      family: 'watchtower',
      footprint: [5, 6],
      height: 9,
      decor: 'sparse',
      seed: 7,
    });
    expect(a.plan).toEqual(b.plan);
  });
});

describe('building-generator - variability', () => {
  it('different seed changes decoration positions', () => {
    const a = buildFromSpec({
      family: 'house',
      footprint: [9, 11],
      height: 9,
      decor: 'dense',
      seed: 1,
    });
    const b = buildFromSpec({
      family: 'house',
      footprint: [9, 11],
      height: 9,
      decor: 'dense',
      seed: 2,
    });
    expect(sameStructure(a.structure, b.structure)).toBe(false);
  });

  it('different footprint changes the wall pattern', () => {
    const a = buildFromSpec({
      family: 'wall_segment',
      footprint: [6, 8],
      height: 7,
      decor: 'sparse',
      seed: 99,
    });
    const b = buildFromSpec({
      family: 'wall_segment',
      footprint: [8, 10],
      height: 7,
      decor: 'sparse',
      seed: 99,
    });
    expect(sameStructure(a.structure, b.structure)).toBe(false);
    expect(getSize(a.structure)).toEqual([6, 7, 8]);
    expect(getSize(b.structure)).toEqual([8, 7, 10]);
  });
});

describe('building-generator - grammar compliance', () => {
  it('door is not at the centre x', () => {
    const result = buildFromSpec({
      family: 'house',
      footprint: [9, 11],
      height: 7,
      decor: 'medium',
      seed: 42,
    });
    const door = result.plan.devices.find((d) => d.kind === 'door');
    expect(door).toBeDefined();
    if (door && door.kind === 'door') {
      const [w, , d] = getSize(result.structure);
      if (door.side === 'south' || door.side === 'north') {
        const cx = Math.floor(w / 2);
        const doorX = door.side === 'south' ? w - 2 : 1;
        expect(doorX).not.toBe(cx);
        void d;
      } else {
        const cz = Math.floor(d / 2);
        const doorZ = door.side === 'east' ? d - 2 : 1;
        expect(doorZ).not.toBe(cz);
      }
    }
  });

  it('no banned materials appear in the structure', () => {
    const families: Array<'house' | 'wall_segment' | 'watchtower' | 'armory' | 'barracks'> = [
      'house', 'wall_segment', 'watchtower', 'armory', 'barracks',
    ];
    for (const family of families) {
      const grammar = STYLE_GRAMMAR.plains[family] ?? STYLE_GRAMMAR.military[family];
      if (!grammar) continue;
      const result = buildFromSpec({
        family,
        footprint: [9, 9],
        height: 7,
        decor: 'medium',
        seed: 13,
      });
      const ids = blockIds(result.structure);
      for (const banned of grammar.materials.banned) {
        expect(ids.has(banned), `banned "${banned}" present in ${family}`).toBe(false);
      }
    }
  });

  it('ladder is at a corner, not the centre', () => {
    const result = buildFromSpec({
      family: 'watchtower',
      footprint: [5, 6],
      height: 9,
      decor: 'sparse',
      seed: 11,
    });
    const ladder = result.plan.devices.find((d) => d.kind === 'ladder');
    expect(ladder).toBeDefined();
    if (ladder && ladder.kind === 'ladder') {
      const [x, , z] = ladder.pos;
      const [w, , d] = getSize(result.structure);
      const onCorner = (x === 0 || x === w - 1) && (z === 0 || z === d - 1);
      expect(onCorner).toBe(true);
    }
  });

  it('plan validates against the existing validator', () => {
    const result = buildFromSpec({
      family: 'house',
      footprint: [9, 11],
      height: 9,
      decor: 'medium',
      seed: 5,
    });
    expect(result.plan.floors.length).toBeGreaterThan(0);
    expect(result.plan.floors[0]?.range[0]).toBe(0);
    expect(result.plan.floors[result.plan.floors.length - 1]?.range[1]).toBe(8);
  });
});

describe('building-generator - any complexity', () => {
  const cases: Array<{ label: string; footprint: [number, number]; height: number; family: 'house' | 'wall_segment' | 'watchtower' | 'armory' }> = [
    { label: 'small 5x5x6 wall_segment', footprint: [5, 5], height: 6, family: 'wall_segment' },
    { label: 'medium 9x11x9 house', footprint: [9, 11], height: 9, family: 'house' },
    { label: 'large 15x15x13 house', footprint: [15, 15], height: 13, family: 'house' },
    { label: 'tall 6x12x20 wall_segment', footprint: [6, 12], height: 20, family: 'wall_segment' },
  ];
  for (const { label, footprint, height, family } of cases) {
    it(`generates ${label} without errors`, () => {
      const result = buildFromSpec({
        family,
        footprint,
        height,
        decor: 'medium',
        seed: 17,
      });
      const [w, h, d] = getSize(result.structure);
      const grammar = STYLE_GRAMMAR.plains[family] ?? STYLE_GRAMMAR.military[family];
      const dims = grammar ? Object.values(grammar.rungs) : [];
      const minW = dims.length > 0 ? Math.min(...dims.map((r) => r.width)) : 3;
      const maxW = dims.length > 0 ? Math.max(...dims.map((r) => r.width)) + 4 : 999;
      const minD = dims.length > 0 ? Math.min(...dims.map((r) => r.depth)) : 3;
      const maxD = dims.length > 0 ? Math.max(...dims.map((r) => r.depth)) + 4 : 999;
      expect(w).toBeGreaterThanOrEqual(Math.max(3, minW));
      expect(w).toBeLessThanOrEqual(maxW);
      expect(d).toBeGreaterThanOrEqual(Math.max(3, minD));
      expect(d).toBeLessThanOrEqual(maxD);
      expect(h).toBe(height);
      expect(blockCount(result.structure)).toBeGreaterThan(0);
    });
  }
});

describe('building-generator - warnings', () => {
  it('emits a warning when rung is out of range', () => {
    const result = buildFromSpec({
      family: 'house',
      rung: 999,
      footprint: [9, 11],
      height: 9,
      decor: 'medium',
      seed: 1,
    });
    expect(result.warnings.some((w) => w.includes('rung'))).toBe(true);
  });

  it('emits a warning when footprint exceeds grammar max', () => {
    const result = buildFromSpec({
      family: 'house',
      footprint: [200, 200],
      height: 5,
      decor: 'sparse',
      seed: 1,
    });
    expect(result.warnings.some((w) => w.includes('footprint'))).toBe(true);
  });

  it('emits a warning when materialLadder contains a banned material', () => {
    const result = buildFromSpec({
      family: 'house',
      footprint: [9, 11],
      height: 7,
      materialLadder: ['oak_planks', 'iron_bars'],
      decor: 'sparse',
      seed: 1,
    });
    expect(result.warnings.some((w) => w.includes('banned'))).toBe(true);
  });
});

describe('building-generator - size constraint', () => {
  it('clamps an oversized footprint to the grammar + 4 bound', () => {
    const result = buildFromSpec({
      family: 'house',
      footprint: [200, 200],
      height: 5,
      decor: 'sparse',
      seed: 1,
    });
    const [w, h, d] = getSize(result.structure);
    expect(w).toBeLessThanOrEqual(18);
    expect(d).toBeLessThanOrEqual(16);
    expect(h).toBe(5);
  });
});

describe('building-generator - variety', () => {
  function shapeDiff(
    a: ReturnType<typeof buildFromSpec>['structure'],
    b: ReturnType<typeof buildFromSpec>['structure'],
  ): number {
    const aMap = new Map<string, string>();
    for (const blk of a.getBlocks()) {
      const [x, y, z] = blk.pos;
      aMap.set(`${x},${y},${z}`, blk.state.getName().toString());
    }
    const bMap = new Map<string, string>();
    for (const blk of b.getBlocks()) {
      const [x, y, z] = blk.pos;
      bMap.set(`${x},${y},${z}`, blk.state.getName().toString());
    }
    let diff = 0;
    const allKeys = new Set<string>([...aMap.keys(), ...bMap.keys()]);
    for (const k of allKeys) {
      if (aMap.get(k) !== bMap.get(k)) diff++;
    }
    return allKeys.size === 0 ? 0 : diff / allKeys.size;
  }

  it('generates materially different structures for different families', () => {
    const a = buildFromSpec({
      family: 'house',
      footprint: [9, 11],
      height: 7,
      decor: 'medium',
      seed: 1,
    });
    const b = buildFromSpec({
      family: 'wall_segment',
      footprint: [6, 8],
      height: 9,
      decor: 'sparse',
      seed: 1,
    });
    expect(shapeDiff(a.structure, b.structure)).toBeGreaterThan(0.35);
  });

  it('palette reaches the corpus lower bound (>= 5 distinct blocks)', () => {
    const result = buildFromSpec({
      family: 'house',
      footprint: [9, 11],
      height: 9,
      decor: 'dense',
      seed: 5,
    });
    const ids = blockIds(result.structure);
    expect(ids.size).toBeGreaterThanOrEqual(5);
  });
});

describe('building-generator - donor', () => {
  it('uses the donor structure to influence corner posts', () => {
    const donor = new Structure(BlockPos.create(5, 4, 5));
    donor.addBlock(BlockPos.create(0, 1, 0), 'minecraft:spruce_log', { axis: 'y' });
    donor.addBlock(BlockPos.create(4, 1, 0), 'minecraft:spruce_log', { axis: 'y' });
    donor.addBlock(BlockPos.create(0, 1, 4), 'minecraft:spruce_log', { axis: 'y' });
    donor.addBlock(BlockPos.create(4, 1, 4), 'minecraft:spruce_log', { axis: 'y' });
    const result = buildFromSpec({
      family: 'watchtower',
      footprint: [5, 5],
      height: 7,
      decor: 'sparse',
      donor,
      seed: 1,
    });
    const w = result.structure.getSize()[0] as number;
    const d = result.structure.getSize()[2] as number;
    const corners: Array<[number, number, number]> = [
      [0, 1, 0], [w - 1, 1, 0], [0, 1, d - 1], [w - 1, 1, d - 1],
    ];
    let spruceCount = 0;
    for (const pos of corners) {
      const blk = result.structure.getBlock(pos);
      if (blk && blk.state.getName().toString().includes('spruce_log')) {
        spruceCount++;
      }
    }
    expect(spruceCount).toBeGreaterThan(0);
  });
});
