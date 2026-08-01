/**
 * Public API of the build-plan DSL.
 *
 * Three entry points, designed to be chained:
 *
 *   const plan = loadPlan(yamlText);     // YAML string → Plan
 *   const result = validatePlan(plan);   // Plan → ValidationResult
 *   const gen = generateStructure(plan); // Plan → Lodestone Structure
 *
 * `generateStructure` re-validates internally and throws if the plan is
 * broken — callers who want a clean ValidationResult should call
 * `validatePlan` first, as the example test does.
 */

export { parseYaml, YamlParseError, type YamlValue } from './parser';
export type {
  ComplianceRule,
  Device,
  Floor,
  GenerationResult,
  GuardRule,
  LayoutHint,
  MaterialId,
  Output,
  Plan,
  Rules,
  Side,
  ValidationError,
  ValidationResult,
  ValidationWarning,
} from './types';
export { validatePlan } from './validate';
export { generateStructure, checkGenerated, validate } from './generate';
export {
  serializeStructureToPlan,
  serializePlanToYaml,
  type SerializationOptions,
  type SerializationWarning,
  type SerializationWarningRule,
  type StructureSerializationResult,
} from './serialize';

import { parseYaml } from './parser';
import type { YamlValue } from './parser';
import type { Plan, ValidationResult, GenerationResult } from './types';
import { validatePlan } from './validate';
import { generateStructure } from './generate';

/**
 * Parse a YAML document into a `Plan`. Throws `YamlParseError` on syntax
 * errors and `Error` on schema mismatches (missing keys, wrong types).
 *
 * The parser is forgiving about whitespace and indentation but strict about
 * the shape — every required field in `Plan` must be present.
 */
export function loadPlan(yamlText: string): Plan {
  const raw = parseYaml(yamlText);
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new Error('plan document must be a YAML mapping at the top level');
  }
  return coercePlan(raw as Record<string, YamlValue>);
}

function coercePlan(raw: Record<string, YamlValue>): Plan {
  if (raw['version'] !== 1) {
    throw new Error(`plan version must be 1, got ${String(raw['version'])}`);
  }
  if (typeof raw['name'] !== 'string') throw new Error('plan.name must be a string');
  const structure = raw['structure'] as Record<string, YamlValue> | undefined;
  if (!structure || typeof structure !== 'object') {
    throw new Error('plan.structure is required');
  }
  const footprint = structure['footprint'] as YamlValue;
  const height = structure['height'];
  const origin = structure['origin'] as YamlValue;
  if (!Array.isArray(footprint) || footprint.length !== 2) {
    throw new Error('plan.structure.footprint must be [width, depth]');
  }
  if (typeof height !== 'number') throw new Error('plan.structure.height must be a number');
  if (!Array.isArray(origin) || origin.length !== 3) {
    throw new Error('plan.structure.origin must be [x, y, z]');
  }
  const floors = raw['floors'] as YamlValue;
  if (!Array.isArray(floors)) throw new Error('plan.floors must be a list');
  const devices = raw['devices'] as YamlValue;
  if (!Array.isArray(devices)) throw new Error('plan.devices must be a list');
  const rules = raw['rules'] as Record<string, YamlValue> | undefined;
  if (!rules) throw new Error('plan.rules is required');
  const output = raw['output'] as Record<string, YamlValue> | undefined;
  if (!output) throw new Error('plan.output is required');

  return {
    name: raw['name'],
    version: 1,
    structure: {
      footprint: [footprint[0] as number, footprint[1] as number],
      height: height,
      origin: [origin[0] as number, origin[1] as number, origin[2] as number],
    },
    floors: floors.map((f) => coerceFloor(f)),
    devices: devices.map((d) => coerceDevice(d)),
    rules: {
      guard: ((rules['guard'] as YamlValue[]) ?? []).filter((x): x is string => typeof x === 'string') as Plan['rules']['guard'],
      compliance: ((rules['compliance'] as YamlValue[]) ?? []).filter((x): x is string => typeof x === 'string') as Plan['rules']['compliance'],
    },
    output: {
      name: output['name'] as string,
      path: output['path'] as string,
    },
  };
}

function coerceFloor(raw: YamlValue): Plan['floors'][number] {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new Error('floor entry must be a mapping');
  }
  const m = raw as Record<string, YamlValue>;
  const range = m['range'] as YamlValue;
  if (!Array.isArray(range) || range.length !== 2) {
    throw new Error('floor.range must be [yStart, yEnd]');
  }
  return {
    range: [range[0] as number, range[1] as number],
    material: m['material'] as Plan['floors'][number]['material'],
    layout: m['layout'] as Plan['floors'][number]['layout'],
  };
}

function coerceDevice(raw: YamlValue): Plan['devices'][number] {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new Error('device entry must be a mapping');
  }
  const m = raw as Record<string, YamlValue>;
  const kind = m['kind'] as Plan['devices'][number]['kind'];
  switch (kind) {
    case 'door':
      return { kind: 'door', side: m['side'] as 'north', floor: m['floor'] as number };
    case 'window':
      return {
        kind: 'window',
        side: m['side'] as 'north',
        floor: m['floor'] as number,
        width: m['width'] as number | undefined,
      };
    case 'torch':
      return { kind: 'torch', pos: m['pos'] as [number, number, number] };
    case 'ladder':
      return {
        kind: 'ladder',
        side: m['side'] as 'north',
        pos: m['pos'] as [number, number, number],
      };
    case 'lever':
      return { kind: 'lever', pos: m['pos'] as [number, number, number] };
    case 'flower_pot':
      return { kind: 'flower_pot', pos: m['pos'] as [number, number, number] };
    case 'bed':
      return { kind: 'bed', side: m['side'] as 'north', floor: m['floor'] as number };
    case 'chest':
      return { kind: 'chest', side: m['side'] as 'north', floor: m['floor'] as number };
    case 'barrel':
      return { kind: 'barrel', side: m['side'] as 'north', floor: m['floor'] as number };
    case 'candle':
      return { kind: 'candle', pos: m['pos'] as [number, number, number] };
    case 'campfire':
      return { kind: 'campfire', pos: m['pos'] as [number, number, number] };
    case 'furnace':
      return { kind: 'furnace', side: m['side'] as 'north', floor: m['floor'] as number };
    case 'external_stair':
      return {
        kind: 'external_stair',
        side: m['side'] as 'north',
        start_y: m['start_y'] as number,
        end_y: m['end_y'] as number,
      };
    case 'chimney':
      return {
        kind: 'chimney',
        offset: m['offset'] as [number, number],
        from_floor: m['from_floor'] as number,
        to_floor: m['to_floor'] as number,
      };
    case 'crenellation':
      return {
        kind: 'crenellation',
        top_y: m['top_y'] as number,
        spacing: m['spacing'] as number,
      };
    case 'fence_post':
      return {
        kind: 'fence_post',
        pos: m['pos'] as [number, number, number],
        height: m['height'] as number,
      };
    case 'corner_post':
      return {
        kind: 'corner_post',
        pos: m['pos'] as [number, number, number],
        height: m['height'] as number,
      };
    default:
      throw new Error(`unknown device kind "${String(kind)}"`);
  }
}

export type PlanResult = {
  plan: Plan;
  validation: ValidationResult;
  generation: GenerationResult;
};

/** Convenience: load + validate + generate. Returns all three so callers
 *  can present a unified report without re-running the validator. */
export function buildFromYaml(yamlText: string): PlanResult {
  const plan = loadPlan(yamlText);
  const validation = validatePlan(plan);
  if (!validation.ok) {
    throw new Error(
      `plan validation failed: ${validation.errors.map((e) => `${e.rule}: ${e.message}`).join('; ')}`,
    );
  }
  const generation = generateStructure(plan);
  return { plan, validation, generation };
}
