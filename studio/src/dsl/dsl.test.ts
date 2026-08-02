import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  buildFromYaml,
  generateStructure,
  loadPlan,
  validatePlan,
  YamlParseError,
} from './index';
import { parseYaml } from './parser';

// dsl.test.ts is at studio/src/dsl/dsl.test.ts — useful when other tests
// need to resolve sibling paths. Currently only the example YAML does.
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const EXAMPLE = path.join(__dirname, 'example-watchtower.yaml');

function loadExampleYaml(): string {
  return readFileSync(EXAMPLE, 'utf8');
}

describe('dsl — YAML parser', () => {
  it('parses a flat scalar document', () => {
    expect(parseYaml('name: hello')).toEqual({ name: 'hello' });
  });

  it('parses nested mappings via indentation', () => {
    const doc = parseYaml('a:\n  b: 1\n  c: 2');
    expect(doc).toEqual({ a: { b: 1, c: 2 } });
  });

  it('parses block sequences of scalars', () => {
    const doc = parseYaml('items:\n  - one\n  - two\n  - three');
    expect(doc).toEqual({ items: ['one', 'two', 'three'] });
  });

  it('parses block sequences of mappings', () => {
    const doc = parseYaml('floors:\n  - range: [0, 3]\n    material: oak_planks');
    expect(doc).toEqual({ floors: [{ range: [0, 3], material: 'oak_planks' }] });
  });

  it('parses inline list literals', () => {
    expect(parseYaml('a: [1, 2, 3]')).toEqual({ a: [1, 2, 3] });
  });

  it('parses booleans, integers, floats, null', () => {
    const doc = parseYaml('a: true\nb: false\nc: 42\nd: 3.14\ne: null');
    expect(doc).toEqual({ a: true, b: false, c: 42, d: 3.14, e: null });
  });

  it('strips trailing comments outside quotes', () => {
    expect(parseYaml('a: 1 # trailing')).toEqual({ a: 1 });
  });

  it('keeps # inside quoted strings', () => {
    expect(parseYaml('a: "a # b"')).toEqual({ a: 'a # b' });
  });

  it('throws YamlParseError on a malformed line', () => {
    expect(() => parseYaml('not a valid line')).toThrow(YamlParseError);
  });

  it('returns null for an empty document', () => {
    expect(parseYaml('')).toBeNull();
  });
});

describe('dsl — loadPlan', () => {
  it('loads the example YAML and coerces it into a Plan', () => {
    const plan = loadPlan(loadExampleYaml());
    expect(plan.name).toContain('watchtower');
    expect(plan.version).toBe(1);
    expect(plan.structure.footprint).toEqual([5, 5]);
    expect(plan.structure.height).toBe(4);
    expect(plan.floors).toHaveLength(1);
    expect(plan.devices.length).toBeGreaterThan(0);
    expect(plan.rules.guard).toContain('no_floating_roof');
  });

  it('rejects an unknown version', () => {
    expect(() => loadPlan('version: 99\nname: x')).toThrow(/version must be 1/);
  });

  it('rejects a missing required section', () => {
    expect(() => loadPlan('version: 1\nname: x\nstructure:\n  footprint: [1,1]\n  height: 1\n  origin: [0,0,0]')).toThrow(/floors must be a list/);
  });
});

describe('dsl — validatePlan', () => {
  it('accepts the example plan with zero errors', () => {
    const plan = loadPlan(loadExampleYaml());
    const result = validatePlan(plan);
    if (!result.ok) {
      // Surface the errors so the test message says what failed.
      expect(result.errors).toEqual([]);
    }
    expect(result.ok).toBe(true);
  });

  it('flags a non-positive footprint', () => {
    const plan = loadPlan(loadExampleYaml());
    plan.structure.footprint = [0, 5];
    const result = validatePlan(plan);
    expect(result.errors.some((e) => e.rule === 'structure.footprint')).toBe(true);
  });

  it('flags a non-contiguous floor coverage', () => {
    const plan = loadPlan(loadExampleYaml());
    plan.floors = [
      { range: [0, 1], material: 'oak_planks', layout: 'residential' },
      { range: [3, 3], material: 'oak_planks', layout: 'battlements' },
    ];
    const result = validatePlan(plan);
    expect(result.errors.some((e) => e.rule === 'floors.contiguous')).toBe(true);
  });

  it('flags an unknown device kind', () => {
    const plan = loadPlan(loadExampleYaml());
    plan.devices.push({ kind: 'chimney', offset: [99, 99], from_floor: 0, to_floor: 1 } as never);
    const result = validatePlan(plan);
    expect(result.errors.some((e) => e.rule === 'device.chimney.offset')).toBe(true);
  });

  it('warns (not errors) when material_ladder is broken', () => {
    const plan = loadPlan(loadExampleYaml());
    plan.rules.compliance = ['material_ladder'];
    plan.floors = [
      { range: [0, 1], material: 'oak_planks', layout: 'residential' },
      { range: [2, 3], material: 'oak_planks', layout: 'residential' },
    ];
    const result = validatePlan(plan);
    expect(result.warnings.some((w) => w.rule === 'material_ladder')).toBe(false);
    plan.floors = [
      { range: [0, 1], material: 'stone_bricks', layout: 'residential' },
      { range: [2, 3], material: 'oak_planks', layout: 'residential' },
    ];
    const result2 = validatePlan(plan);
    expect(result2.warnings.some((w) => w.rule === 'material_ladder')).toBe(true);
  });

  it('flags an unknown guard rule', () => {
    const plan = loadPlan(loadExampleYaml());
    (plan.rules.guard as string[]).push('no_such_rule');
    const result = validatePlan(plan);
    expect(result.errors.some((e) => e.message.includes('no_such_rule'))).toBe(true);
  });
});

describe('dsl — generateStructure', () => {
  it('generates a non-empty structure for the example plan', () => {
    const plan = loadPlan(loadExampleYaml());
    const gen = generateStructure(plan);
    expect(gen.blockCount).toBeGreaterThan(0);
    expect(gen.bbox).toEqual([5, 4, 5]);
    expect(gen.structure.getSize()).toEqual([5, 4, 5]);
  });

  it('refuses to generate from an invalid plan', () => {
    const plan = loadPlan(loadExampleYaml());
    plan.structure.footprint = [0, 5];
    expect(() => generateStructure(plan)).toThrow(/cannot generate/);
  });

  it('places the door on the south face at the centre x', () => {
    const plan = loadPlan(loadExampleYaml());
    const gen = generateStructure(plan);
    const [w, , d] = gen.bbox;
    const doorPos = [Math.floor(w / 2), 1, d - 1] as const;
    const block = gen.structure.getBlock(doorPos as unknown as [number, number, number]);
    expect(block).not.toBeNull();
    expect(block!.state.getName().toString()).toContain('oak_door');
  });

  it('places the four corner posts on the building corners', () => {
    const plan = loadPlan(loadExampleYaml());
    const gen = generateStructure(plan);
    const [w, , d] = gen.bbox;
    const corners: [number, number, number][] = [
      [0, 1, 0], [w - 1, 1, 0], [0, 1, d - 1], [w - 1, 1, d - 1],
    ];
    for (const pos of corners) {
      const block = gen.structure.getBlock(pos);
      expect(block).not.toBeNull();
      expect(block!.state.getName().toString()).toContain('oak_log');
    }
  });

  it('round-trips through the checker pipeline without crashing', async () => {
    const plan = loadPlan(loadExampleYaml());
    const gen = generateStructure(plan);
    const { checkGenerated } = await import('./generate');
    const report = checkGenerated(plan, gen);
    // The example is small and dense — the integrity primitives and the
    // structure-with-air rules should pass; we don't gate on roof counts
    // because the example doesn't ship a roof device.
    expect(report).toBeDefined();
    expect(typeof report.ok).toBe('boolean');
  });

  it('accepts a donor BlockGrid and emits blocks lifted from it', async () => {
    const plan = loadPlan(loadExampleYaml());
    const { makeGrid } = await import('../engine/test-utils');
    // Build a 5x5x5 donor that contributes a `spruce_log` post — the harvested
    // vocabulary should put `spruce_log` on the corners, not the default oak.
    const entries = [
      { pos: [0, 1, 0] as [number, number, number], id: 'spruce_log', props: { axis: 'y' } },
      { pos: [4, 1, 0] as [number, number, number], id: 'spruce_log', props: { axis: 'y' } },
      { pos: [0, 1, 4] as [number, number, number], id: 'spruce_log', props: { axis: 'y' } },
      { pos: [4, 1, 4] as [number, number, number], id: 'spruce_log', props: { axis: 'y' } },
    ];
    const donor = makeGrid([5, 4, 5], entries);
    const gen = generateStructure(plan, { donor, donorName: 'spruce_donor' });
    // The plan's corner_post devices were already on the corners before the
    // harvest pass, but the new auto-placed corner posts (per the plan's
    // ground + residential scaffold) should also pick up the harvested
    // spruce_log.
    const [, h, d] = gen.bbox;
    const w = 5;
    const scaffoldCorners: [number, number, number][] = [
      [0, 1, 0], [w - 1, 1, 0], [0, 1, d - 1], [w - 1, 1, d - 1],
    ];
    for (const pos of scaffoldCorners) {
      const block = gen.structure.getBlock(pos as unknown as [number, number, number]);
      expect(block).not.toBeNull();
      expect(block!.state.getName().toString()).toContain('spruce_log');
    }
    void h;
  });
});

describe('dsl — buildFromYaml convenience', () => {
  it('runs load + validate + generate in one call', async () => {
    const result = buildFromYaml(loadExampleYaml());
    expect(result.validation.ok).toBe(true);
    expect(result.generation.blockCount).toBeGreaterThan(0);
    // The example targets structure/military/watchtower/watchtower_dsl_example.nbt.
    expect(result.plan.output.path).toBe('military/watchtower/');
  });

  it('throws on an invalid plan', () => {
    expect(() =>
      buildFromYaml(`
        version: 1
        name: broken
        structure:
          footprint: [-1, 5]
          height: 3
          origin: [0, 0, 0]
        floors:
          - range: [0, 2]
            material: oak_planks
            layout: residential
        devices: []
        rules:
          guard: []
          compliance: []
        output:
          name: broken
          path: "broken/"
      `),
    ).toThrow(/plan validation failed/);
  });
});

describe('dsl — file paths', () => {
  it('example-watchtower.yaml exists at the expected path', () => {
    expect(loadExampleYaml().length).toBeGreaterThan(0);
  });
});
