/**
 * TypeScript types for the build-plan DSL.
 *
 * A plan describes a building as a stack of named floors (each with a primary
 * material and a layout hint) plus a list of devices the generator must place.
 * The plan is YAML on disk, parsed into these shapes, then validated and
 * translated into a Lodestone Structure.
 *
 * Material vocabulary is a closed string-literal union — the engine's checker
 * pipeline is calibrated over the same id set (`docs/05-craft/DEVICES.md`).
 * Adding a new material here without updating `engine/appearance.ts` is a
 * silent failure waiting to happen; the validator will catch unknown ids.
 *
 * Device kinds are a discriminated union on `kind`. Each kind declares the
 * parameters the generator needs; missing required fields are caught at the
 * type level by the union exhaustiveness check in `validate.ts`.
 */

import type { Structure } from '@mattzh72/lodestone';

// ── material + layout vocabulary ────────────────────────────────────────

/**
 * Block ids known to the engine. Short form (no "minecraft:" prefix) — matches
 * `BlockInfo.id` in `engine/appearance.ts`. Extend when adding new materials;
 * the validator checks every emitted id against the engine's predicate set.
 */
export type MaterialId =
  | 'oak_planks'
  | 'oak_log'
  | 'oak_slab'
  | 'oak_stairs'
  | 'oak_fence'
  | 'oak_door'
  | 'oak_trapdoor'
  | 'oak_pressure_plate'
  | 'oak_leaves'
  | 'stripped_oak_log'
  | 'cobblestone'
  | 'mossy_cobblestone'
  | 'cobblestone_slab'
  | 'cobblestone_stairs'
  | 'cobblestone_wall'
  | 'stone'
  | 'stone_slab'
  | 'stone_stairs'
  | 'stone_bricks'
  | 'stone_brick_slab'
  | 'stone_brick_stairs'
  | 'stone_brick_wall'
  | 'andesite'
  | 'dirt'
  | 'coarse_dirt'
  | 'dirt_path'
  | 'grass_block'
  | 'farmland'
  | 'mud'
  | 'podzol'
  | 'rooted_dirt'
  | 'smooth_stone'
  | 'white_terracotta'
  | 'glass_pane'
  | 'iron_bars'
  | 'hay_block'
  | 'bookshelf'
  | 'chiseled_bookshelf'
  | 'bamboo_block'
  | 'bamboo_planks'
  | 'bamboo_mosaic'
  | 'moss_block'
  | 'moss_carpet'
  | 'hanging_roots'
  | 'white_wool'
  | 'yellow_wool'
  | 'red_wool'
  | 'brown_wool'
  | 'lime_wool'
  | 'composter'
  | 'barrel'
  | 'crafting_table'
  | 'cartography_table'
  | 'fletching_table'
  | 'smithing_table'
  | 'loom'
  | 'jukebox'
  | 'note_block'
  | 'bell'
  | 'torch'
  | 'soul_torch'
  | 'lantern'
  | 'soul_lantern'
  | 'candle'
  | 'flower_pot';

/**
 * Layout hint — a tag the generator reads to pick which wall/floor pattern
 * to apply on a given Y-range. The author corpus measured in
 * `docs/05-craft/DEVICES.md` gave us five idioms; the DSL keeps them as
 * strings so future layouts are cheap to add without type churn.
 */
export type LayoutHint =
  | 'ground'      // terrain + plot boundary + first wall course
  | 'residential' // walls + windows + floor inside
  | 'commercial'  // counter + work bay + door open to the street
  | 'battlements' // crenellation course + roof walk
  | 'roof';       // stair or slab pitch + ridge

/** A single Y-range of a building, with its material and layout. */
export type Floor = {
  /** Inclusive Y range — `[yStart, yEnd]`. Adjacent floors must share a boundary. */
  range: [number, number];
  /** Primary material used for walls and floors within this range. */
  material: MaterialId;
  /** Layout hint the generator uses to pick devices for this floor. */
  layout: LayoutHint;
};

// ── devices ─────────────────────────────────────────────────────────────

/**
 * Devices are the named patterns from `docs/05-craft/BUILD_LANGUAGE.md` and
 * `DEVICES.md`. The union is discriminated on `kind` so each shape carries
 * only the parameters that device actually uses — `door_north` has no
 * `start_y`, `chimney` has no `spacing`. `validate.ts` enforces required
 * fields per kind.
 */
export type Device =
  | { kind: 'door'; side: Side; floor: number }
  | { kind: 'window'; side: Side; floor: number; width?: number }
  | { kind: 'torch'; pos: [number, number, number] }
  | { kind: 'ladder'; side: Side; pos: [number, number, number] }
  | { kind: 'lever'; pos: [number, number, number] }
  | { kind: 'flower_pot'; pos: [number, number, number] }
  | { kind: 'bed'; side: Side; floor: number }
  | { kind: 'chest'; side: Side; floor: number }
  | { kind: 'barrel'; side: Side; floor: number }
  | { kind: 'candle'; pos: [number, number, number] }
  | { kind: 'campfire'; pos: [number, number, number] }
  | { kind: 'furnace'; side: Side; floor: number }
  | { kind: 'external_stair'; side: Side; start_y: number; end_y: number }
  | { kind: 'chimney'; offset: [number, number]; from_floor: number; to_floor: number }
  | { kind: 'crenellation'; top_y: number; spacing: number }
  | { kind: 'fence_post'; pos: [number, number, number]; height: number }
  | { kind: 'corner_post'; pos: [number, number, number]; height: number };

/** Compass direction used to place facade-attached devices. */
export type Side = 'north' | 'south' | 'east' | 'west';

// ── rules ───────────────────────────────────────────────────────────────

/** A rule name the validator knows how to enforce. The string form keeps the
 *  YAML schema readable; the validator resolves the name at runtime. */
export type GuardRule =
  | 'no_floating_roof'
  | 'no_slab_rider'
  | 'no_hanging_roof'
  | 'no_cantilever'
  | 'no_orphan_fence';

export type ComplianceRule =
  | 'constant_footprint'
  | 'material_ladder'
  | 'contiguous_floors';

export type Rules = {
  guard: GuardRule[];
  compliance: ComplianceRule[];
};

// ── output ──────────────────────────────────────────────────────────────

export type Output = {
  /** Default NBT name (e.g. "watchtower_lvl1"). */
  name: string;
  /** Subpath under `common/src/main/resources/data/onceuponatown/structure/`. */
  path: string;
};

// ── plan + result types ─────────────────────────────────────────────────

/** A complete build plan, the top-level YAML document. */
export type Plan = {
  name: string;
  /** Schema version — bump on breaking changes; the parser rejects unknown versions. */
  version: 1;
  structure: {
    footprint: [number, number];
    height: number;
    origin: [number, number, number];
  };
  floors: Floor[];
  devices: Device[];
  rules: Rules;
  output: Output;
};

/** Result of validating a plan. */
export type ValidationResult = {
  ok: boolean;
  errors: ValidationError[];
  warnings: ValidationWarning[];
};

export type ValidationError = {
  rule: string;
  message: string;
  pos?: [number, number, number];
};

export type ValidationWarning = {
  rule: string;
  message: string;
  pos?: [number, number, number];
};

/** Result of generating a structure from a validated plan. */
export type GenerationResult = {
  structure: Structure;
  /** Number of solid blocks placed (excludes air). */
  blockCount: number;
  /** World-space bounding box of the generated structure. */
  bbox: [number, number, number];
};
