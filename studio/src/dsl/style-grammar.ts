export type BuildingFamily =
  | 'house'
  | 'job'
  | 'garden'
  | 'street'
  | 'starter'
  | 'wall_segment'
  | 'wall_corner'
  | 'wall_tower'
  | 'gatehouse'
  | 'watchtower'
  | 'armory'
  | 'barracks'
  | 'training_yard';

export interface RungDimensions {
  width: number;
  depth: number;
  height: number;
  observedIn?: string;
}

export interface MaterialLadder {
  always: string[];
  common: string[];
  rare: string[];
  banned: string[];
}

export interface DeviceVocabulary {
  required: Array<{ kind: string; rule: string }>;
  common: string[];
  signature: string[];
}

export interface CompositionRule {
  rule: string;
  source: string;
}

export interface FamilyGrammar {
  family: BuildingFamily;
  rungs: Record<string, RungDimensions>;
  materials: MaterialLadder;
  devices: DeviceVocabulary;
  composition: CompositionRule[];
  antiPatterns: CompositionRule[];
  donors: Record<string, string>;
}

export interface StyleGrammar {
  plains: Record<string, FamilyGrammar>;
  military: Record<string, FamilyGrammar>;
  bands: {
    mirrorSymmetryMax: number;
    densityMedian: number;
    paletteMedian: number;
  };
}

const HOUSES: FamilyGrammar = {
  family: 'house',
  rungs: {
    'variantA.lvl0': { width: 9, depth: 11, height: 5, observedIn: 'house.nbt' },
    'variantA.lvl6': { width: 9, depth: 11, height: 9, observedIn: 'house_lvl6.nbt' },
    'variantB.lvl0': { width: 13, depth: 12, height: 7, observedIn: 'house_2.nbt' },
    'variantB.lvl6': { width: 13, depth: 12, height: 13, observedIn: 'house_2_lvl6.nbt' },
    'variantC.lvl0': { width: 14, depth: 12, height: 11, observedIn: 'house_3.nbt' },
    'variantC.lvl6': { width: 14, depth: 12, height: 12, observedIn: 'house_3_lvl6.nbt' },
  },
  materials: {
    always: [
      'oak_planks',
      'oak_log[axis=y]',
      'oak_slab',
      'cobblestone',
      'coarse_dirt',
      'grass_block',
      'dirt',
    ],
    common: [
      'oak_stairs',
      'oak_fence',
      'glass_pane',
      'oak_door',
      'cobblestone_stairs',
      'torch',
      'lantern',
      'oak_trapdoor',
      'dirt_path',
    ],
    rare: [
      'yellow_wool',
      'yellow_bed',
      'candle',
      'potted_dandelion',
      'potted_oak_sapling',
      'decorated_pot',
      'mossy_cobblestone',
      'mossy_cobblestone_stairs',
      'orange_bed',
      'allium',
      'red_wall_banner',
      'flower_pot',
    ],
    banned: [
      'minecraft:grass',
      'stone_bricks',
      'deepslate',
      'cobbled_deepslate',
      'nether_bricks',
      'blackstone',
      'quartz_block',
      'iron_bars',
      'smooth_stone',
      'polished_andesite',
      'polished_granite',
      'polished_diorite',
    ],
  },
  devices: {
    required: [
      { kind: 'door', rule: 'lower+upper oak_door pair at corner — never centred.' },
      { kind: 'slab_cap', rule: 'oak_slab[type=top] runs the perimeter at the eave course.' },
    ],
    common: ['torch', 'lantern', 'oak_fence window', 'glass_pane window', 'barrel', 'chest'],
    signature: ['yellow_bed', 'candle', 'flower_pot', 'potted_dandelion'],
  },
  composition: [
    {
      rule: 'door sits one cell in from a corner — never centred; x ≠ floor(w/2).',
      source: 'plains-style.md §4 y=1, SKILL.md §3 anti-pattern 2',
    },
    {
      rule: 'apron is asymmetric (median mirror X = 0.25 in the corpus); dirt_path clusters by the door, 4 to 8 cells long, never on a centre column.',
      source: 'plains-style.md §10 ground grammar, SKILL.md §1 bands',
    },
    {
      rule: 'cobblestone lower course uniform, NOT dithered — plains differs from fortification gradient ramps.',
      source: 'plains-style.md §14 "Dithered random cobblestone field" → No',
    },
    {
      rule: 'roof is stair-pitched from height ≥ 7; slab-only cap is valid only when height ≤ 6.',
      source: 'plains-style.md §9 rule 1, SKILL.md §1 "31% of builds ≤6 tall pitch with stairs"',
    },
    {
      rule: 'roof_ridge axis = Z; oak_stairs[facing] tall side faces the interior ("facing is the tall side").',
      source: 'plains-style.md §9 rule 2 + 3',
    },
    {
      rule: 'upper storey floor is oak_planks full-block, not a slab.',
      source: 'plains-style.md §4 y=1',
    },
    {
      rule: 'a chimney at the ridge, off-centre, 1-2 cells thick, with cobblestone_stairs collar at the roofline.',
      source: 'plains-style.md §11 device 1',
    },
    {
      rule: 'apron Y extends 1-3 cells beyond the cobblestone plinth — never flush with the wall.',
      source: 'plains-style.md §10 rule 6',
    },
  ],
  antiPatterns: [
    {
      rule: 'door at x = floor(w/2) — makes the whole build mirror-symmetric (gate-score > 0.5).',
      source: 'plains-style.md §13 anti-pattern 2',
    },
    {
      rule: 'dressed stone (stone_bricks, polished_*) on a cottage — reads as a transplanted keep.',
      source: 'plains-style.md §13 anti-pattern 3',
    },
    {
      rule: 'iron_bars on a wall opening — fortification vocabulary; plains windows are glass_pane.',
      source: 'plains-style.md §13 anti-pattern 5',
    },
    {
      rule: 'all-log walls (oak_log[axis=y] with no oak_planks) — reads as a palisade fort, not a cottage.',
      source: 'plains-style.md §13 anti-pattern 6',
    },
    {
      rule: 'a craft workstation (crafting_table, furnace, smoker) in a house — makes it a kitchen, not a home.',
      source: 'plains-style.md §13 anti-pattern 8',
    },
    {
      rule: 'pancake roof (cover_share < 0.45 with no stairs) — single most common stylistic failure.',
      source: 'plains-style.md §13 anti-pattern 1',
    },
    {
      rule: 'minecraft:grass — removed in 1.20.3; 28 corpus files fail the gate on this.',
      source: 'SKILL.md "Two repo bugs"',
    },
  ],
  donors: {
    'variantA.lvl0': 'house.nbt',
    'variantA.lvl6': 'house_lvl6.nbt',
    'variantB.lvl0': 'house_2.nbt',
    'variantB.lvl6': 'house_2_lvl6.nbt',
    'variantC.lvl0': 'house_3.nbt',
    'variantC.lvl6': 'house_3_lvl6.nbt',
  },
};

const JOBS: FamilyGrammar = {
  family: 'job',
  rungs: {
    carpenter: { width: 15, depth: 10, height: 10, observedIn: 'carpenter_lvl7.nbt' },
    merchant_shop: { width: 15, depth: 15, height: 13, observedIn: 'merchant_shop_lvl5.nbt' },
    kitchen: { width: 13, depth: 11, height: 13, observedIn: 'kitchen_lvl6.nbt' },
    leather_workshop: { width: 20, depth: 12, height: 10, observedIn: 'leather_workshop_lvl6.nbt' },
    pig_farm: { width: 26, depth: 19, height: 12, observedIn: 'pig_farm_lvl8.nbt' },
    wheat_farm: { width: 26, depth: 20, height: 11, observedIn: 'wheat_farm_lvl6.nbt' },
    granary: { width: 15, depth: 11, height: 9, observedIn: 'granary_lvl7.nbt' },
    workshop: { width: 9, depth: 5, height: 8, observedIn: 'workshop.nbt' },
    beekeeper: { width: 18, depth: 15, height: 13, observedIn: 'beekeeper_lvl6.nbt' },
  },
  materials: {
    always: [
      'oak_planks',
      'oak_log[axis=y]',
      'oak_slab',
      'cobblestone',
      'coarse_dirt',
      'grass_block',
      'dirt',
      'oak_fence',
    ],
    common: [
      'oak_stairs',
      'glass_pane',
      'oak_door',
      'dirt_path',
      'cobblestone_stairs',
      'torch',
      'lantern',
      'oak_trapdoor',
      'barrel',
      'chest',
      'mossy_cobblestone',
      'lantern',
    ],
    rare: [
      'bee_nest',
      'beehive',
      'honey_block',
      'honeycomb_block',
      'lectern',
      'note_block',
      'oak_wall_sign',
      'oak_wall_hanging_sign',
      'cauldron',
      'water_cauldron',
      'smooth_stone',
      'smoker',
      'chiseled_bookshelf',
      'cobweb',
      'mud',
      'lime_wool',
      'lime_bed',
      'carved_pumpkin',
      'white_terracotta',
      'allium',
      'potted_red_tulip',
      'yellow_wool',
      'yellow_bed',
      'orange_bed',
      'red_bed',
      'brown_bed',
      'brown_wool',
      'red_wall_banner',
      'redstone_torch',
      'oak_button',
      'white_candle',
    ],
    banned: [
      'minecraft:grass',
      'stone_bricks',
      'deepslate',
      'cobbled_deepslate',
      'nether_bricks',
      'blackstone',
      'quartz_block',
      'iron_bars',
      'polished_andesite',
      'polished_granite',
      'polished_diorite',
      'chiseled_stone_bricks',
    ],
  },
  devices: {
    required: [
      { kind: 'front_bay_door', rule: 'lower+upper oak_door pair, threshold cobblestone_stairs[facing=out,half=bottom].' },
      { kind: 'workstation', rule: 'one trade workstation + adjacent fuel stack (per family).' },
      { kind: 'storage', rule: 'a chest, barrel, or composter at a back corner on every rung.' },
    ],
    common: ['torch', 'lantern', 'oak_fence wall opening', 'glass_pane front window', 'campfire', 'crafting_table'],
    signature: [
      'barrel+chest+chiseled_bookshelf+lectern (carpenter)',
      'lectern+note_block+white_terracotta+oak_wall_hanging_sign (merchant_shop)',
      'smoker+potted_red_tulip (kitchen)',
      'cauldron+water_cauldron+smooth_stone+white_terracotta (leather_workshop)',
      'mud+brown_wool+smoker+red_wall_banner (pig_farm)',
      'lime_wool+lime_bed+carved_pumpkin (wheat_farm)',
      'cobweb in eaves (granary)',
      'bee_nest+beehive+honey_block (beekeeper)',
    ],
  },
  composition: [
    {
      rule: 'apron is asymmetric with at least one dirt_path cluster; dirt_path length 4-8 cells, never full-length runway.',
      source: 'plains-style.md §5 universal, §10 rule 2',
    },
    {
      rule: 'cobblestone lower course even when the upper storey is plastered (white_terracotta at L5 unlock).',
      source: 'plains-style.md §5 universal',
    },
    {
      rule: 'storage device at a back corner on every rung (chest, barrel, or composter).',
      source: 'plains-style.md §5 universal',
    },
    {
      rule: 'light source at a corner post at every storey level — never centred on a column.',
      source: 'plains-style.md §5 universal, §4 lighting rhythm',
    },
    {
      rule: 'workstation + adjacent fuel stack when a craft trade (furnace+coal, smoker+coal, beehive+flowers).',
      source: 'plains-style.md §5 universal',
    },
    {
      rule: 'three-bay separation: front sales, storage, back workshop — the bay-separation IS the trade.',
      source: 'plains-style.md §5 merchant_shop anatomy',
    },
    {
      rule: 'family signature must survive to the top rung — 11/11 of beekeeper signature blocks are still at the top.',
      source: 'plains-style.md §5, §12 family fingerprints',
    },
    {
      rule: 'workshop ladders are monotonic-additive: workstations never removed, only added across rungs.',
      source: 'military-style.md §10 work ladder (the same idiom applies to plains workshops)',
    },
  ],
  antiPatterns: [
    {
      rule: 'paved streets inside the bbox (stone, smooth_stone, polished_*) — reads as a courtyard, not a yard.',
      source: 'plains-style.md §13 anti-pattern 4',
    },
    {
      rule: 'a trade signature on the wrong family — beehive is beekeeper only, cauldron is leather_workshop only, cobweb is granary+pig_farm only.',
      source: 'plains-style.md §13 anti-pattern 11, §12 family fingerprints',
    },
    {
      rule: 'iron_bars on a wall opening — fortification vocabulary; plains windows are glass_pane.',
      source: 'plains-style.md §13 anti-pattern 5',
    },
    {
      rule: 'a perimeter all of one terrain block — 100% grass_block or 100% coarse_dirt reads as a stadium.',
      source: 'plains-style.md §13 anti-pattern 10',
    },
    {
      rule: 'pig_farm without mud — reads as a wheat barn. The trade is the shape of the inside, not the tradesheet.',
      source: 'plains-style.md §5 "bay-separation IS the trade"',
    },
    {
      rule: 'pig pen yard clad with smooth_stone — yard must stay mud/coarse_dirt under foot (ADR-0003).',
      source: 'plains-style.md §5 "yard devices", ADR-0003',
    },
    {
      rule: 'water_cauldron when the recipe needs a quench trough — full cauldron is a water source and spreads.',
      source: 'military-style.md §16 anti-pattern 11 (the family-equivalent trap on plains)',
    },
  ],
  donors: {
    carpenter: 'carpenter_lvl7.nbt',
    merchant_shop: 'merchant_shop_lvl5.nbt',
    kitchen: 'kitchen_lvl6.nbt',
    leather_workshop: 'leather_workshop_lvl6.nbt',
    pig_farm: 'pig_farm_lvl8.nbt',
    wheat_farm: 'wheat_farm_lvl6.nbt',
    granary: 'granary_lvl7.nbt',
    workshop: 'workshop.nbt',
    beekeeper: 'beekeeper_lvl6.nbt',
  },
};

const GARDENS: FamilyGrammar = {
  family: 'garden',
  rungs: {
    well: { width: 11, depth: 11, height: 5, observedIn: 'well.nbt' },
    fountain_place: { width: 13, depth: 13, height: 6, observedIn: 'fountain_place.nbt' },
    lone_garden: { width: 16, depth: 16, height: 8, observedIn: 'lone_garden.nbt' },
    lone_place: { width: 14, depth: 13, height: 8, observedIn: 'lone_place.nbt' },
    wild_spot: { width: 12, depth: 8, height: 8, observedIn: 'wild_spot.nbt' },
  },
  materials: {
    always: [
      'grass_block',
      'coarse_dirt',
      'dirt_path',
      'oak_fence',
    ],
    common: [
      'cobblestone',
      'mossy_cobblestone',
      'oak_leaves',
      'oak_slab',
      'stone',
      'stone_slab',
      'dirt',
      'moss_block',
      'flower_pot',
    ],
    rare: [
      'bell',
      'mossy_cobblestone_stairs',
      'mossy_cobblestone_wall',
      'mossy_cobblestone_slab',
      'yellow_wool',
      'seagrass',
      'white_tulip',
      'carved_pumpkin',
      'moss_carpet',
      'hay_block',
    ],
    banned: [
      'minecraft:grass',
      'stone_bricks',
      'deepslate',
      'cobbled_deepslate',
      'nether_bricks',
      'blackstone',
      'quartz_block',
      'iron_bars',
      'iron_door',
      'iron_trapdoor',
      'oak_door',
      'oak_log',
      'oak_planks',
    ],
  },
  devices: {
    required: [
      { kind: 'open_air', rule: 'no roof, no walls — a garden that grows a roof has been built wrong.' },
    ],
    common: ['oak_fence low rail', 'oak_leaves clumps', 'flower_pot'],
    signature: [
      'bell+mossy_cobblestone family (fountain_place)',
      'mossy_cobblestone_wall+seagrass (lone_garden)',
      'moss_block+stone+stone_slab+flower_pot (well)',
      'oak_leaves+oak_fence+flower_pot (lone_place)',
      'hay_block+short_grass+flower_pot (wild_spot)',
    ],
  },
  composition: [
    {
      rule: 'open-air construction — no roof, no door, no walls.',
      source: 'plains-style.md §6 "no roof, no door"',
    },
    {
      rule: 'asymmetric apron, two- or three-block terrain mix (grass_block + coarse_dirt + dirt), never a uniform perimeter.',
      source: 'plains-style.md §6 + §10 rule 6',
    },
    {
      rule: 'single-feature plots — one signature device per family, not a hybrid.',
      source: 'plains-style.md §3 §6 "yard devices"',
    },
    {
      rule: 'paths radiating from the centre feature, never on a centred column.',
      source: 'plains-style.md §6 fountain anatomy',
    },
    {
      rule: 'leaves clumps come from a nearby tree, not the centre tree — never a centred tree.',
      source: 'plains-style.md §6, §10 rule 5',
    },
  ],
  antiPatterns: [
    {
      rule: 'a garden with a roof or door — built wrong.',
      source: 'plains-style.md §6 closing line',
    },
    {
      rule: 'centred fountain with a symmetric path — reads as a village square, not a yard.',
      source: 'plains-style.md §13 anti-pattern 9',
    },
    {
      rule: 'a perimeter all of one terrain block — 100% grass_block or 100% coarse_dirt reads as a stadium.',
      source: 'plains-style.md §13 anti-pattern 10',
    },
    {
      rule: 'centred tree or centred well — a yard with a tree at the centre is the symmetry violation that wins from 30 steps away.',
      source: 'plains-style.md §10 rule 5',
    },
  ],
  donors: {
    well: 'well.nbt',
    fountain_place: 'fountain_place.nbt',
    lone_garden: 'lone_garden.nbt',
    lone_place: 'lone_place.nbt',
    wild_spot: 'wild_spot.nbt',
  },
};

const STREETS: FamilyGrammar = {
  family: 'street',
  rungs: {
    default: { width: 4, depth: 18, height: 1, observedIn: 'street_1.nbt' },
  },
  materials: {
    always: ['dirt_path'],
    common: [],
    rare: [],
    banned: [
      'oak_planks',
      'oak_log',
      'oak_slab',
      'oak_stairs',
      'oak_fence',
      'cobblestone',
      'mossy_cobblestone',
      'stone',
      'stone_bricks',
      'glass_pane',
      'oak_door',
      'torch',
      'lantern',
      'bed',
      'chest',
      'barrel',
    ],
  },
  devices: {
    required: [
      { kind: 'jigsaw', rule: 'jigsaw markers at the path ends — worldgen rotates these and lays them between buildings.' },
    ],
    common: [],
    signature: [],
  },
  composition: [
    {
      rule: 'height is ALWAYS 1; street tiles are 1-cell-tall rail-tile-like patches.',
      source: 'plains-style.md §2 Streets are special',
    },
    {
      rule: 'only dirt_path + jigsaw markers — no walls, no roof, no decor.',
      source: 'plains-style.md §2, §3 streets',
    },
    {
      rule: 'two-block deep, 8 to 17 cells along the path.',
      source: 'plains-style.md §3 streets',
    },
    {
      rule: 'worldgen rotates these and lays them between buildings — they are connectors, not buildings.',
      source: 'plains-style.md §2 Streets are special',
    },
  ],
  antiPatterns: [
    {
      rule: 'adding walls, roof, or decor — a street is a connector, not a building.',
      source: 'plains-style.md §2',
    },
    {
      rule: 'height > 1 — breaks the 1-cell-tall rail-tile contract.',
      source: 'plains-style.md §2',
    },
  ],
  donors: {
    default: 'street_1.nbt',
  },
};

const STARTERS: FamilyGrammar = {
  family: 'starter',
  rungs: {
    settlement: { width: 15, depth: 14, height: 7, observedIn: 'settlement.nbt' },
    settlement_2: { width: 15, depth: 14, height: 7, observedIn: 'settlement_2.nbt' },
    settlement_3: { width: 15, depth: 14, height: 7, observedIn: 'settlement_3.nbt' },
    settlement_lvl1: { width: 15, depth: 14, height: 9, observedIn: 'settlement_lvl1.nbt' },
  },
  materials: {
    always: [
      'grass_block',
      'coarse_dirt',
      'dirt_path',
      'oak_fence',
      'oak_planks',
      'oak_log[axis=y]',
      'dirt',
      'oak_slab',
    ],
    common: ['oak_leaves', 'cobblestone', 'stone', 'stone_slab', 'oak_stairs'],
    rare: [
      'stripped_oak_log',
      'rooted_dirt',
      'mud',
      'oak_button',
      'smoker',
      'carrots',
      'potatoes',
      'blast_furnace',
    ],
    banned: [
      'minecraft:grass',
      'stone_bricks',
      'deepslate',
      'cobbled_deepslate',
      'nether_bricks',
      'blackstone',
      'quartz_block',
      'iron_bars',
      'iron_door',
      'iron_trapdoor',
    ],
  },
  devices: {
    required: [
      { kind: 'town_anchor', rule: 'one onceuponatown:town_anchor mod jigsaw at the centre — without it the settlement never becomes a village candidate.' },
      { kind: 'multi_building', rule: 'several distinct buildings in one bounding box, separated by fences and joined by dirt_path.' },
    ],
    common: ['crafting_table', 'oak_door', 'torch', 'wall_torch', 'oak_trapdoor'],
    signature: ['town_anchor', 'multi-building sub-recipes', 'asymmetric apron'],
  },
  composition: [
    {
      rule: 'one onceuponatown:town_anchor mod jigsaw at the centre — the worldgen rotation pipeline looks for it.',
      source: 'plains-style.md §7 "don\'t drop the town_anchor"',
    },
    {
      rule: 'several distinct buildings in one bbox, separated by fences, joined by dirt_path.',
      source: 'plains-style.md §7 starter anatomy',
    },
    {
      rule: 'paths weave between buildings; apron is asymmetric (""≈ next to ≈"").',
      source: 'plains-style.md §7',
    },
    {
      rule: 'three settlement recipes — settlement, settlement_2, settlement_3 — choose which, do not mix.',
      source: 'plains-style.md §12 family fingerprints',
    },
  ],
  antiPatterns: [
    {
      rule: 'a settlement without the onceuponatown:town_anchor — never becomes a village candidate.',
      source: 'plains-style.md §7',
    },
    {
      rule: 'mixing recipes across settlements (e.g. settlement_2 mud/oak_button/smoker with settlement_3 carrots/potatoes).',
      source: 'plains-style.md §12 family fingerprints',
    },
    {
      rule: 'a starter that is a single building — if the starter is single-building, the village reads single-building all the way out.',
      source: 'plains-style.md §7',
    },
    {
      rule: 'centred axis-symmetric apron — settlements must read as the village\'s first jigsaw, not a model.',
      source: 'plains-style.md §10 ground grammar',
    },
  ],
  donors: {
    settlement: 'settlement.nbt',
    settlement_2: 'settlement_2.nbt',
    settlement_3: 'settlement_3.nbt',
    settlement_lvl1: 'settlement_lvl1.nbt',
  },
};

const WALL_SEGMENT: FamilyGrammar = {
  family: 'wall_segment',
  rungs: {
    lvl0: { width: 6, depth: 8, height: 7, observedIn: 'wall_segment.nbt' },
    lvl1: { width: 6, depth: 8, height: 10, observedIn: 'wall_segment_lvl1.nbt' },
    lvl2: { width: 6, depth: 8, height: 10, observedIn: 'wall_segment_lvl2.nbt' },
    lvl3: { width: 6, depth: 8, height: 10, observedIn: 'wall_segment_lvl3.nbt' },
    lvl4: { width: 6, depth: 8, height: 12, observedIn: 'wall_segment_lvl4.nbt' },
  },
  materials: {
    always: [
      'cobblestone',
      'oak_log[axis=y]',
      'coarse_dirt',
      'grass_block',
      'dirt',
      'dirt_path',
      'oak_fence',
      'packed_mud',
    ],
    common: [
      'mossy_cobblestone',
      'oak_planks',
      'oak_slab',
      'oak_stairs',
      'cobblestone_slab',
      'cobblestone_stairs',
      'stone',
      'andesite',
      'tuff',
      'stripped_oak_log',
      'wall_torch',
    ],
    rare: [
      'stone_bricks',
      'stone_brick_slab',
      'stone_brick_stairs',
      'red_wall_banner',
      'white_wall_banner',
      'oak_leaves',
      'short_grass',
    ],
    banned: [
      'minecraft:grass',
      'iron_bars',
      'iron_door',
      'iron_trapdoor',
      'deepslate',
      'cobbled_deepslate',
      'nether_bricks',
      'blackstone',
      'basalt',
      'quartz_block',
      'smooth_quartz',
      'sandstone',
      'red_sandstone',
      'prismarine',
      'polished_andesite',
      'polished_granite',
      'polished_diorite',
      'chiseled_stone_bricks',
      'stone_brick_wall',
    ],
  },
  devices: {
    required: [
      { kind: 'jigsaw_pair', rule: 'jigsaw connectors on z=0 and z=7 — one cell entry, one cell exit.' },
      { kind: 'walk', rule: 'walk at WALK=7 (lvl1..4) or y=4 (lvl0 rampart); HEAD_CLEAR=2 always.' },
    ],
    common: ['wall_torch on the walk', 'oak_fence inner rail (lvl0) or stone inner rail (lvl2+)'],
    signature: ['merlons (lvl2+)', 'oversailing parapet on brackets (lvl3+)', 'dressed walk surface (lvl3+)', 'hoarding gallery (lvl4)'],
  },
  composition: [
    {
      rule: 'three-cell section: outer-A_OUT=2, mid-A_MID=3, inner-A_IN=4 — THICK=3 total.',
      source: 'military-style.md §4 anatomy, wall.py §"invariants"',
    },
    {
      rule: 'BODY_TOP=6 at tiers 1..4; WALK=BODY_TOP+1=7; HEAD_CLEAR=2 above the walk.',
      source: 'military-style.md §4, wall.py §"HEAD_CLEAR"',
    },
    {
      rule: 'gradient ramp at the wall face — at y=1 mossy cobble, y=3 cobble, y=5 stone with andesite/tuff mixed; dithered, not layered.',
      source: 'military-style.md §4, wall.py §"gradient ramps"',
    },
    {
      rule: 'piers at index 0, last, and every 4 cells (% 4 == 0); pier bias=0.7 toward the clean end.',
      source: 'military-style.md §4, wall.py §"column_styles"',
    },
    {
      rule: 'ground apron Y is wider than the plinth — coarse_dirt + packed_mud close to the wall, grass_block only at the plot edge.',
      source: 'military-style.md §4, wall.py §"ground"',
    },
    {
      rule: 'lay_ground varies the apron cell by cell — a uniform apron is a stadium, not a curtain.',
      source: 'military-style.md §4, wall.py §"ground"',
    },
  ],
  antiPatterns: [
    {
      rule: 'polished stone block (polished_andesite, polished_granite, chiseled_stone_bricks) — reads as a palace.',
      source: 'military-style.md §16 anti-pattern 13',
    },
    {
      rule: 'deepslate anywhere — banned outright from the military set.',
      source: 'military-style.md §16 anti-pattern 14, RULINGS.md 2026-07-29',
    },
    {
      rule: 'iron_bars on a wall opening — palace vocabulary; the fortification loophole opener is oak_fence or glass_pane.',
      source: 'military-style.md §16 anti-pattern 10',
    },
    {
      rule: 'no headroom above the walk — a piece that renders well but lacks headroom is impassable and fails traverse.check_route.',
      source: 'military-style.md §16 anti-pattern 5',
    },
    {
      rule: 'external torches or lanterns on the OUTER parapet — lighting the outside is lighting the ground the attacker stands on. Interior walk lights only.',
      source: 'military-style.md §16 anti-pattern 7',
    },
    {
      rule: '*_wall blocks as merlons — garden coping, not merlons. Full blocks only, two courses and no more.',
      source: 'military-style.md §16 anti-pattern 6',
    },
    {
      rule: 'a hoarding gallery built the wrong way — floor is the wall top carried outward on brackets, the roof clears two cells above; built the other way it leaves one cell of headroom and nothing can stand there.',
      source: 'military-style.md §16 anti-pattern 15',
    },
    {
      rule: 'seven rebars lighter than the cell — fence whose props disagree with its neighbours, slab fitted to a stair, roof block hanging on nothing — all caught by check_fabric, including fence_faults, roof_faults, slab_faults.',
      source: 'military-style.md §16 anti-pattern 17',
    },
  ],
  donors: {
    lvl0: 'wall_segment.nbt',
    lvl1: 'wall_segment_lvl1.nbt',
    lvl2: 'wall_segment_lvl2.nbt',
    lvl3: 'wall_segment_lvl3.nbt',
    lvl4: 'wall_segment_lvl4.nbt',
  },
};

const WALL_CORNER: FamilyGrammar = {
  family: 'wall_corner',
  rungs: {
    lvl0: { width: 9, depth: 9, height: 7, observedIn: 'wall_corner.nbt' },
    lvl1: { width: 9, depth: 9, height: 10, observedIn: 'wall_corner_lvl1.nbt' },
    lvl2: { width: 9, depth: 9, height: 10, observedIn: 'wall_corner_lvl2.nbt' },
    lvl3: { width: 9, depth: 9, height: 10, observedIn: 'wall_corner_lvl3.nbt' },
    lvl4: { width: 9, depth: 9, height: 13, observedIn: 'wall_corner_lvl4.nbt' },
  },
  materials: {
    always: [
      'cobblestone',
      'coarse_dirt',
      'grass_block',
      'dirt',
      'dirt_path',
      'oak_fence',
      'packed_mud',
      'oak_log',
    ],
    common: [
      'mossy_cobblestone',
      'oak_planks',
      'oak_slab',
      'oak_stairs',
      'stripped_oak_log',
      'cobblestone_slab',
      'cobblestone_stairs',
      'stone',
      'tuff',
      'wall_torch',
    ],
    rare: [
      'stone_bricks',
      'stone_brick_slab',
      'oak_leaves',
      'short_grass',
    ],
    banned: [
      'minecraft:grass',
      'iron_bars',
      'iron_door',
      'iron_trapdoor',
      'deepslate',
      'cobbled_deepslate',
      'nether_bricks',
      'blackstone',
      'basalt',
      'quartz_block',
      'smooth_quartz',
      'polished_andesite',
      'polished_granite',
      'polished_diorite',
      'chiseled_stone_bricks',
      'stone_brick_wall',
      'sandstone',
      'red_sandstone',
      'prismarine',
    ],
  },
  devices: {
    required: [
      { kind: 'open_platform', rule: 'elbow cells SOLID to the walk, OPEN above — closed bastion dammed the walk at every corner; the open platform is what lets a player turn without jumping.' },
      { kind: 'two_arms', rule: 'two arms leave the elbow at (x0..hi, z0..hi) = (2..4, 2..4); arm B has entry=True (east_up), arm A has entry=False (south_up).' },
    ],
    common: ['wall_torch', 'merlons (lvl2+)'],
    signature: ['open platform at the elbow', 'merlons around the platform'],
  },
  composition: [
    {
      rule: 'elbow at (x0..hi, z0..hi) = (2..4, 2..4); solid to walk, open above.',
      source: 'military-style.md §5 anatomy, wall.py §"compose_corner"',
    },
    {
      rule: 'arm A runs west→south (outer face west); arm B runs south→west (outer face north); each is a straight_run of 4 stations.',
      source: 'military-style.md §5, wall.py §"compose_corner"',
    },
    {
      rule: 'tier-by-tier tracks the segment — same five-tier ladder.',
      source: 'military-style.md §5',
    },
    {
      rule: 'at tier 4 (hoarding) the gallery wraps the corner in two broken runs meeting at the elbow posts.',
      source: 'military-style.md §5, wall.py §"_elbow"',
    },
    {
      rule: 'ground is laid under the stations AND the elbow cells — six corners, not four.',
      source: 'military-style.md §5, wall.py §"compose_corner"',
    },
    {
      rule: 'travelling the chain the outside is on the RIGHT, matching the straight segment; backwards the ring builds inside-out with the loops facing the courtyard.',
      source: 'military-style.md §5, wall.py docstring at compose_corner',
    },
  ],
  antiPatterns: [
    {
      rule: 'a solid bastion at the elbow — dammed the walk at all four corners ("the single worst thing a wall piece can do").',
      source: 'military-style.md §5',
    },
    {
      rule: 'continuous hoarding gallery at the corner — reads as an overhang, not as architecture. Two-cell gap at the elbow.',
      source: 'military-style.md §5, wall.py §"_elbow"',
    },
    {
      rule: 'no headroom above the walk — fails traverse.check_route.',
      source: 'military-style.md §16 anti-pattern 5',
    },
    {
      rule: 'polished stone variants — palace vocabulary.',
      source: 'military-style.md §16 anti-pattern 13',
    },
  ],
  donors: {
    lvl0: 'wall_corner.nbt',
    lvl1: 'wall_corner_lvl1.nbt',
    lvl2: 'wall_corner_lvl2.nbt',
    lvl3: 'wall_corner_lvl3.nbt',
    lvl4: 'wall_corner_lvl4.nbt',
  },
};

const WALL_TOWER: FamilyGrammar = {
  family: 'wall_tower',
  rungs: {
    lvl0: { width: 7, depth: 9, height: 10, observedIn: 'wall_tower.nbt' },
    lvl1: { width: 7, depth: 9, height: 12, observedIn: 'wall_tower_lvl1.nbt' },
    lvl2: { width: 7, depth: 9, height: 12, observedIn: 'wall_tower_lvl2.nbt' },
    lvl3: { width: 7, depth: 9, height: 12, observedIn: 'wall_tower_lvl3.nbt' },
    lvl4: { width: 7, depth: 9, height: 15, observedIn: 'wall_tower_lvl4.nbt' },
  },
  materials: {
    always: [
      'cobblestone',
      'oak_log[axis=y]',
      'oak_door',
      'ladder',
      'coarse_dirt',
      'grass_block',
      'dirt',
      'dirt_path',
      'packed_mud',
    ],
    common: [
      'mossy_cobblestone',
      'oak_planks',
      'oak_slab',
      'oak_stairs',
      'oak_fence',
      'stripped_oak_log',
      'stone',
      'cobblestone_stairs',
      'wall_torch',
    ],
    rare: [
      'stone_bricks',
      'stone_brick_slab',
      'oak_leaves',
      'short_grass',
    ],
    banned: [
      'minecraft:grass',
      'iron_bars',
      'iron_door',
      'iron_trapdoor',
      'deepslate',
      'cobbled_deepslate',
      'nether_bricks',
      'blackstone',
      'basalt',
      'quartz_block',
      'smooth_quartz',
      'polished_andesite',
      'polished_granite',
      'polished_diorite',
      'chiseled_stone_bricks',
      'stone_brick_wall',
    ],
  },
  devices: {
    required: [
      { kind: 'internal_ladder', rule: 'ladder[facing=climb] at LADDER_Z=2 from y=1 to y=tower_rise+1.' },
      { kind: 'door_pair', rule: 'oak_door[half=lower,half=upper] at y=2..3, on the front face, second cell in (front_cells[1]) — never centred.' },
    ],
    common: ['wall_torch on the walk', 'merlons (lvl2+)'],
    signature: ['ladder at fixed LADDER_Z=2', 'arrow slits at y=BODY_TOP-2', 'projecting parapet on brackets (lvl3+)'],
  },
  composition: [
    {
      rule: 'shaft TOWER_SIDE=5 wide, THICK=3 deep; LADDER_Z=2 fixed.',
      source: 'military-style.md §6, wall.py §"compose_tower" + §"LADDER_Z"',
    },
    {
      rule: 'tower_rise = tier.walk + 3; ladder climbs to tower_rise + 1 = WALK + 4.',
      source: 'military-style.md §6',
    },
    {
      rule: 'arrow slits cut BEFORE the ladder is placed; slits at y=BODY_TOP-2; placed at piers and bays at random, 35% skip.',
      source: 'military-style.md §6, wall.py §"LADDER_Z" + §"arrow_loops"',
    },
    {
      rule: 'from tier 2 (cobble) onward, the crown goes through _merlons with one-cell oversails on piers at tiers with oversail=True.',
      source: 'military-style.md §6',
    },
    {
      rule: 'the hoarding gallery does NOT run on the tower — it is a curtain device; the tower gets merlons + dressed walk + brackets.',
      source: 'military-style.md §6',
    },
    {
      rule: 'towers are impassable without a climb route — build_one calls usable(vox, kind="wall_tower"), which runs check_route for both the walk and the climb.',
      source: 'military-style.md §6, build_military.py §"usable"',
    },
  ],
  antiPatterns: [
    {
      rule: 'centred door — at the centre cell of the front face makes the whole tower mirror-symmetric; always at the second cell in.',
      source: 'military-style.md §16 anti-pattern 2',
    },
    {
      rule: 'no internal ladder — square tower becomes a tower-shaped decoration.',
      source: 'military-style.md §15 watchtower fingerprint mirrored',
    },
    {
      rule: 'cutting arrow slits at the ladder coordinate — the two passes must agree. Cutting one here left a rung with nothing behind it and a gap in the climb.',
      source: 'military-style.md §6, wall.py §"LADDER_Z"',
    },
    {
      rule: 'ladder[facing] set to the wall side — every rung ends in mid-air; facing names the direction the support is — opposite the wall it is fixed to.',
      source: 'military-style.md §6, wall.py §"compose_tower"',
    },
    {
      rule: 'no headroom above the walk — fails traverse.check_route.',
      source: 'military-style.md §16 anti-pattern 5',
    },
  ],
  donors: {
    lvl0: 'wall_tower.nbt',
    lvl1: 'wall_tower_lvl1.nbt',
    lvl2: 'wall_tower_lvl2.nbt',
    lvl3: 'wall_tower_lvl3.nbt',
    lvl4: 'wall_tower_lvl4.nbt',
  },
};

const GATEHOUSE: FamilyGrammar = {
  family: 'gatehouse',
  rungs: {
    lvl0: { width: 6, depth: 8, height: 9, observedIn: 'gatehouse.nbt' },
    lvl1: { width: 6, depth: 8, height: 11, observedIn: 'gatehouse_lvl1.nbt' },
    lvl2: { width: 6, depth: 8, height: 11, observedIn: 'gatehouse_lvl2.nbt' },
    lvl3: { width: 6, depth: 8, height: 11, observedIn: 'gatehouse_lvl3.nbt' },
    lvl4: { width: 6, depth: 8, height: 14, observedIn: 'gatehouse_lvl4.nbt' },
  },
  materials: {
    always: [
      'cobblestone',
      'oak_log',
      'oak_door',
      'coarse_dirt',
      'grass_block',
      'dirt',
      'dirt_path',
      'packed_mud',
    ],
    common: [
      'mossy_cobblestone',
      'oak_planks',
      'oak_slab',
      'oak_stairs',
      'oak_fence',
      'cobblestone_stairs',
      'cobblestone_slab',
      'stone',
      'stripped_oak_log',
      'wall_torch',
    ],
    rare: [
      'stone_bricks',
      'stone_brick_slab',
      'stone_brick_stairs',
      'oak_leaves',
      'short_grass',
    ],
    banned: [
      'minecraft:grass',
      'iron_bars',
      'iron_door',
      'iron_trapdoor',
      'deepslate',
      'cobbled_deepslate',
      'nether_bricks',
      'blackstone',
      'basalt',
      'quartz_block',
      'smooth_quartz',
      'polished_andesite',
      'polished_granite',
      'polished_diorite',
      'chiseled_stone_bricks',
      'stone_brick_wall',
    ],
  },
  devices: {
    required: [
      { kind: 'gate_passage', rule: 'two inner cells (x=2..3) at y=2..3 are EMPTY — the gate passage.' },
      { kind: 'door_pair', rule: 'oak_door[half=lower,half=upper] at y=1..2 closes the gate.' },
      { kind: 'stepped_flight', rule: 'three cobblestone_stairs[facing=out,half=bottom] at y=1..0 climbing from courtyard to gate threshold.' },
    ],
    common: ['wall_torch on the walk', 'merlons (lvl2+)'],
    signature: ['empty gate passage at x=2..3', 'oak_log guard room above the gate', 'inner ladder at the back of the passage'],
  },
  composition: [
    {
      rule: 'flanking piers at x=1 and x=4 are the same mass as the curtain\'s walk stations.',
      source: 'military-style.md §7, wall.py §"compose_gate"',
    },
    {
      rule: 'above the gate (y=5..7) is a chamber of oak_log posts with a peaked roof — the guard room, accessed by the inner ladder at the back of the passage.',
      source: 'military-style.md §7',
    },
    {
      rule: 'tier 4 (hoarding) carries the worked stone_brick field with a stair-pitched gable over the guard room, and the gate itself is flanked by stone_brick stairs.',
      source: 'military-style.md §7',
    },
    {
      rule: 'the gate is walked both ways — usable(vox, kind="gatehouse") runs the courtyard climb route from (A_IN+1, 1, sz-1) to the gate\'s own walk elevation (NOT wall.WALK).',
      source: 'military-style.md §7, build_military.py §"usable"',
    },
  ],
  antiPatterns: [
    {
      rule: 'flight facing INTO the gate — the tread face stood between the player and the door.',
      source: 'military-style.md §7, wall.py §"_flight"',
    },
    {
      rule: 'unwalkable gate — goal must be the piece\'s own walk elevation, not the module constant wall.WALK; otherwise the level-0 gate reads as unclimbable when the flight was sound.',
      source: 'military-style.md §7, build_military.py §"usable"',
    },
    {
      rule: 'centred gate — the gate passage is at x=2..3 of a 6-cell-wide piece; flanking piers carry the asymmetry by their mass + the door + step.',
      source: 'military-style.md §16 anti-pattern 4',
    },
    {
      rule: 'no headroom above the walk — fails traverse.check_route.',
      source: 'military-style.md §16 anti-pattern 5',
    },
  ],
  donors: {
    lvl0: 'gatehouse.nbt',
    lvl1: 'gatehouse_lvl1.nbt',
    lvl2: 'gatehouse_lvl2.nbt',
    lvl3: 'gatehouse_lvl3.nbt',
    lvl4: 'gatehouse_lvl4.nbt',
  },
};

const WATCHTOWER: FamilyGrammar = {
  family: 'watchtower',
  rungs: {
    lvl0: { width: 9, depth: 8, height: 12, observedIn: 'watchtower.nbt' },
    lvl1: { width: 9, depth: 8, height: 15, observedIn: 'watchtower_lvl1.nbt' },
    lvl2: { width: 9, depth: 8, height: 15, observedIn: 'watchtower_lvl2.nbt' },
    lvl3: { width: 9, depth: 8, height: 13, observedIn: 'watchtower_lvl3.nbt' },
    lvl4: { width: 9, depth: 8, height: 13, observedIn: 'watchtower_lvl4.nbt' },
    lvl5: { width: 9, depth: 9, height: 18, observedIn: 'watchtower_lvl5.nbt' },
    lvl6: { width: 11, depth: 9, height: 18, observedIn: 'watchtower_lvl6.nbt' },
  },
  materials: {
    always: [
      'cobblestone',
      'oak_log[axis=y]',
      'oak_door',
      'ladder',
      'coarse_dirt',
      'grass_block',
      'dirt',
      'dirt_path',
      'packed_mud',
    ],
    common: [
      'mossy_cobblestone',
      'oak_planks',
      'oak_slab',
      'oak_stairs',
      'oak_fence',
      'stripped_oak_log',
      'cobblestone_stairs',
      'cobblestone_slab',
      'stone',
      'wall_torch',
      'barrel',
      'chest',
      'campfire',
    ],
    rare: [
      'glass_pane',
      'red_wall_banner',
      'white_wall_banner',
      'lantern',
      'furnace',
      'stone_bricks',
    ],
    banned: [
      'minecraft:grass',
      'iron_bars',
      'iron_door',
      'iron_trapdoor',
      'deepslate',
      'cobbled_deepslate',
      'nether_bricks',
      'blackstone',
      'basalt',
      'quartz_block',
      'smooth_quartz',
      'polished_andesite',
      'polished_granite',
      'polished_diorite',
      'chiseled_stone_bricks',
      'stone_brick_wall',
    ],
  },
  devices: {
    required: [
      { kind: 'internal_ladder', rule: 'ladder[facing=climb] at LADDER_Z=2 from y=1 to y=y=1+wall_h inclusive.' },
      { kind: 'door_pair', rule: 'oak_door[half=lower] at y=2 and oak_door[half=upper] at y=3 on the front face, second cell in (never centred).' },
      { kind: 'arrow_slit', rule: 'one glass_pane (50%) or oak_fence (50%) per storey on i % 3 == 1 courses, on 1 or 2 randomly chosen sides.' },
    ],
    common: ['barrel in the yard', 'chest at the top rung', 'campfire warmth'],
    signature: [
      'open_deck (lvl0..2)',
      'battlements (lvl3..4)',
      'pitched_roof (lvl5..6)',
      'glass_pane windows (lvl4+)',
      'banner (lvl2+)',
      'lantern (lvl5..6)',
    ],
  },
  composition: [
    {
      rule: 'shaft 3×4 (shell=3, shell_z=4) — intentionally uneven span. A 3×3 shaft is mirror-symmetric by construction (~0.72 against a corpus median of 0.34). A 3×4 span has no centre column.',
      source: 'military-style.md §8 anatomy, compose.py §"compose_tower" header docstring',
    },
    {
      rule: 'quoins follow the course they sit in — stone at the stone courses, oak_log[axis=y] at the timber courses. Earlier an oak_log post was run up through the stone storeys.',
      source: 'military-style.md §8',
    },
    {
      rule: 'storey break on i % 3 == 2: a horizontal stripped_oak_log[axis=axis] ring courses through the wall plane — NOT a projecting slab band.',
      source: 'military-style.md §8, compose.py §"compose_tower" string course',
    },
    {
      rule: 'cobblestone_stairs[half=bottom, facing=OPPOSITE(front)] sits one cell outside the door as the doorstep — facing names the tall half, so the inward-climbed step has its tall side toward the door.',
      source: 'military-style.md §8, compose.py §"compose_tower" doorstep comment',
    },
    {
      rule: 'trapdoor-height platform at every storey break (y=1+s*3, for s in 1..storeys-1) covers the interior EXCEPT the ladder column — the player has something to step OFF onto at the top of every rung.',
      source: 'military-style.md §8',
    },
    {
      rule: 'crown branches by level: open_deck (lvl0..2), battlements (lvl3..4), pitched_roof (lvl5..6).',
      source: 'military-style.md §8, compose.py §"compose_tower" open_deck/battlement/pitched_roof branches',
    },
    {
      rule: 'yard stores keyed off the footprint, NOT the level — barrel count went 5, 2, 3, 2, 3, 3, 5 across the ladder when rolled per level; fixed quadrant gives monotonic counts.',
      source: 'military-style.md §8, compose.py §"_scatter_props"',
    },
  ],
  antiPatterns: [
    {
      rule: 'square shaft (3×3) — mirror-symmetric by construction; ~0.72 against corpus median. Use an even span (3×4).',
      source: 'military-style.md §16 anti-pattern 1',
    },
    {
      rule: 'external flight of stairs on a tower — _ring returns cells in raster order, so consecutive treads are not adjacent; the climb is the internal ladder, nothing else.',
      source: 'military-style.md §16 anti-pattern 3',
    },
    {
      rule: 'centred door — at the centre cell of the front face makes the whole tower mirror-symmetric.',
      source: 'military-style.md §16 anti-pattern 2',
    },
    {
      rule: 'projecting slab band at every third course — stacks four ledges up the shaft and turns the tower into a wedding cake. The projecting band belongs to the facade pass exactly once, at the stone/timber transition.',
      source: 'military-style.md §8',
    },
    {
      rule: '1-cell machicolation on a 3-wide shaft — reads as a mushroom cap. The crown oversails by one cell only when min(inner_x, inner_z) >= 5.',
      source: 'military-style.md §8, compose.py §"compose_tower" battlement branch',
    },
    {
      rule: 'stores appearing, moving, disappearing across rungs — barrel count 5, 2, 3, 2, 3, 3, 5 is a bug. Yard quadrant keyed off footprint, not level.',
      source: 'military-style.md §16 anti-pattern 9',
    },
    {
      rule: 'stair-pitched gable producing 6 downhill stairs — ROOF_TO_STAIRS=False in build_military.py, closed until the uphill direction is decided per cell.',
      source: 'military-style.md §16 anti-pattern 16',
    },
  ],
  donors: {
    lvl0: 'watchtower.nbt',
    lvl1: 'watchtower_lvl1.nbt',
    lvl2: 'watchtower_lvl2.nbt',
    lvl3: 'watchtower_lvl3.nbt',
    lvl4: 'watchtower_lvl4.nbt',
    lvl5: 'watchtower_lvl5.nbt',
    lvl6: 'watchtower_lvl6.nbt',
  },
};

const ARMORY: FamilyGrammar = {
  family: 'armory',
  rungs: {
    lvl0: { width: 14, depth: 14, height: 11, observedIn: 'armory.nbt' },
    lvl1: { width: 14, depth: 14, height: 13, observedIn: 'armory_lvl1.nbt' },
    lvl2: { width: 14, depth: 14, height: 15, observedIn: 'armory_lvl2.nbt' },
    lvl3: { width: 14, depth: 14, height: 17, observedIn: 'armory_lvl3.nbt' },
    lvl4: { width: 14, depth: 14, height: 19, observedIn: 'armory_lvl4.nbt' },
    lvl5: { width: 14, depth: 14, height: 21, observedIn: 'armory_lvl5.nbt' },
  },
  materials: {
    always: [
      'cobblestone',
      'oak_log[axis=y]',
      'oak_planks',
      'oak_door',
      'furnace',
      'crafting_table',
      'coarse_dirt',
      'grass_block',
      'dirt',
      'dirt_path',
      'packed_mud',
    ],
    common: [
      'mossy_cobblestone',
      'oak_slab',
      'oak_stairs',
      'oak_fence',
      'stripped_oak_log',
      'cobblestone_stairs',
      'cobblestone_slab',
      'stone',
      'wall_torch',
      'barrel',
      'chest',
      'lantern',
      'campfire',
      'cobblestone_wall',
    ],
    rare: [
      'stone_bricks',
      'stone_brick_slab',
      'stonecutter',
      'cauldron',
      'anvil',
      'orange_bed',
      'glass_pane',
      'oak_trapdoor',
      'oak_pressure_plate',
      'oak_leaves',
      'chain',
    ],
    banned: [
      'minecraft:grass',
      'iron_bars',
      'iron_door',
      'iron_trapdoor',
      'deepslate',
      'cobbled_deepslate',
      'nether_bricks',
      'blackstone',
      'basalt',
      'quartz_block',
      'smooth_quartz',
      'polished_andesite',
      'polished_granite',
      'polished_diorite',
      'chiseled_stone_bricks',
      'stone_brick_wall',
    ],
  },
  devices: {
    required: [
      { kind: 'tapering_chimney', rule: '2×2 cobblestone shoulder at the base, narrowing to a 2-wide shaft of cobblestone (lower) or stone_bricks (top rung) above the eaves (eaves_y = max(4, roof_y - 4)). 8 cells past the ridge.' },
      { kind: 'fireproofed_roof', rule: 'oak_slab cells within 3 cells of either chimney column are swapped for stone_brick_slab[type] — a wooden roof over a forge catches fire.' },
      { kind: 'workstation', rule: 'forge: lvl0 crafting_table, lvl3 stonecutter, lvl4 cauldron EMPTY (NEVER water_cauldron), lvl5 anvil. Iron-cost-gated.' },
    ],
    common: ['barrel', 'chest', 'campfire', 'firewood horizontal oak_log[axis=x] stacks'],
    signature: [
      'tapering chimney past the ridge',
      'open work bay on the long face near the forge',
      'firewood store (6 oak_log[axis=x] max + charcoal barrel)',
      'oak_trapdoor mounted flat as a shield (lvl4+)',
    ],
  },
  composition: [
    {
      rule: 'stretched donor house_3.nbt + 5 levels of house_3_lvlN.nbt; lengths kept along the donor\'s long axis.',
      source: 'military-style.md §2 eight families, compose.py §"stretched_recipes"',
    },
    {
      rule: 'open work bay: a wall panel omitted on the long face nearest the forge, from y=3 up to the roof. Posts at the edges stay; only the infill goes.',
      source: 'military-style.md §10 anatomy, compose.py §"armory_chimney" — "open work bay"',
    },
    {
      rule: 'firewood store: horizontal oak_log[axis=x] stacks alternating with charcoal barrel, max 6 cells, near the forge.',
      source: 'military-style.md §10, compose.py §"armory_chimney" — firewood store',
    },
    {
      rule: 'workstation placement in a niche — walled on three sides, one way in, well away from the door. Author\'s 196 workstations: 54% exactly one free side, 11% walled in, 21% two sides, 10% open.',
      source: 'military-style.md §10, compose.py §"work_spots"',
    },
    {
      rule: 'stone ladder: from rung 5 (FORTIFY_FROM_LEVEL=5), fortify_stone paints stone_bricks into the cobble field at 22-32% of the cobble at rung 5, 32% at rung 6. Mossy cobble converts only at the quoins and piers.',
      source: 'military-style.md §10, compose.py §"fortify_stone"',
    },
    {
      rule: 'dressed stone is 15-25% of the field at the top rung — minority accent, never the field.',
      source: 'military-style.md §10, compose.py §"fortify_stone" docstring',
    },
  ],
  antiPatterns: [
    {
      rule: 'anvil at rung 0 — a settlement that has just thrown up an earth bank cannot own the most expensive workstation in the game (31 iron). The rung a tool arrives at is the rung its iron becomes plausible.',
      source: 'military-style.md §16 anti-pattern 12, compose.py §"ARMOURY_LADDER" header',
    },
    {
      rule: 'water_cauldron as a quench trough — a full cauldron is a water source and spreads. EMPTY only.',
      source: 'military-style.md §16 anti-pattern 11, compose.py §"WORK_ITEM.cauldron"',
    },
    {
      rule: 'deepslate anywhere — banned outright from the military set.',
      source: 'military-style.md §16 anti-pattern 14, RULINGS.md 2026-07-29',
    },
    {
      rule: 'polished stone variants — palace vocabulary.',
      source: 'military-style.md §16 anti-pattern 13',
    },
    {
      rule: 'a wooden roof over the forge — fires the building. The forge\'s fireproofed roof patch is required.',
      source: 'military-style.md §10, compose.py §"armory_chimney" fireproofed roof',
    },
    {
      rule: 'chimney below the eaves — the chimney grows the declared box if needed; cells above the eaves are silently clipped on save otherwise.',
      source: 'military-style.md §10, compose.py §"armory_chimney" header docstring',
    },
  ],
  donors: {
    lvl0: 'armory.nbt',
    lvl1: 'armory_lvl1.nbt',
    lvl2: 'armory_lvl2.nbt',
    lvl3: 'armory_lvl3.nbt',
    lvl4: 'armory_lvl4.nbt',
    lvl5: 'armory_lvl5.nbt',
  },
};

const BARRACKS: FamilyGrammar = {
  family: 'barracks',
  rungs: {
    lvl0: { width: 13, depth: 15, height: 7, observedIn: 'barracks.nbt' },
    lvl1: { width: 13, depth: 15, height: 8, observedIn: 'barracks_lvl1.nbt' },
    lvl2: { width: 13, depth: 15, height: 9, observedIn: 'barracks_lvl2.nbt' },
    lvl3: { width: 13, depth: 15, height: 10, observedIn: 'barracks_lvl3.nbt' },
    lvl4: { width: 13, depth: 15, height: 11, observedIn: 'barracks_lvl4.nbt' },
    lvl5: { width: 13, depth: 15, height: 12, observedIn: 'barracks_lvl5.nbt' },
    lvl6: { width: 12, depth: 15, height: 13, observedIn: 'barracks_lvl6.nbt' },
  },
  materials: {
    always: [
      'cobblestone',
      'oak_log[axis=y]',
      'stripped_oak_log',
      'oak_planks',
      'oak_door',
      'coarse_dirt',
      'grass_block',
      'dirt',
      'dirt_path',
      'packed_mud',
    ],
    common: [
      'mossy_cobblestone',
      'oak_slab',
      'oak_stairs',
      'oak_fence',
      'cobblestone_stairs',
      'cobblestone_slab',
      'stone',
      'wall_torch',
      'barrel',
      'chest',
      'campfire',
      'cobblestone_wall',
      'oak_trapdoor',
    ],
    rare: [
      'stone_bricks',
      'stone_brick_slab',
      'white_bed',
      'orange_bed',
      'red_wall_banner',
      'white_wall_banner',
      'glass_pane',
      'oak_fence_gate',
      'oak_pressure_plate',
      'bookshelf',
      'white_wool',
      'yellow_wool',
      'chain',
      'oak_leaves',
    ],
    banned: [
      'minecraft:grass',
      'iron_bars',
      'iron_door',
      'iron_trapdoor',
      'deepslate',
      'cobbled_deepslate',
      'nether_bricks',
      'blackstone',
      'basalt',
      'quartz_block',
      'smooth_quartz',
      'polished_andesite',
      'polished_granite',
      'polished_diorite',
      'chiseled_stone_bricks',
      'stone_brick_wall',
    ],
  },
  devices: {
    required: [
      { kind: 'colonnade', rule: 'oak_log posts at 4-cell spacing carrying a stripped_oak_log beam on ONE ground-floor long face — the lower-coordinate long face, so asymmetry survives. Posts at the corners and every 4 cells; bays opened from wall_lo+1 to lintel_y.' },
      { kind: 'bed', rule: 'white_bed per soldier at rungs 0..3 (4 residents), +1 at rungs 3 and 5; rung 5 has 10 orange_bed (donor\'s own). Monotonic-additive, never removes.' },
    ],
    common: ['barrel', 'chest', 'campfire', 'oak_fence_gate (lvl5+)'],
    signature: [
      'open timber colonnade on one long face',
      'garrison dressing (campfire, no hay_bales, no decorated pots)',
      'banners from rung 4 onward (red_wall_banner or white_wall_banner)',
      'cap_pillars at rung ≥4 (oak_stairs[half=bottom] facing toward building centre)',
    ],
  },
  composition: [
    {
      rule: 'stretched donor house_2.nbt + 6 levels of house_2_lvlN.nbt; lengths kept along the donor\'s long axis.',
      source: 'military-style.md §2 eight families, compose.py §"stretched_recipes"',
    },
    {
      rule: 'colonnade runs along ONE long face only — the lower-coordinate long face — so asymmetry survives. Two courses of beam give the colonnade a substantial header.',
      source: 'military-style.md §9, compose.py §"barracks_arcade" header docstring',
    },
    {
      rule: 'oak_planks deck one cell out from the face at wall_lo is the projected walkway; oak_fence low rail sits on the deck at wall_lo+1. Two of the posts carry an oak_leaves planter.',
      source: 'military-style.md §9',
    },
    {
      rule: 'lower storey is cobblestone at the plinth + oak_planks above (donor\'s own grammar). At rung 5 fortify_stone paints stone_bricks into the cobble field at 22-32% — quoins and piers only, never the field.',
      source: 'military-style.md §9',
    },
    {
      rule: 'banners from rung 4 onward (banners and i >= 4 in build_military.py §"stretched_recipes"). A poor early building should not be flying colours.',
      source: 'military-style.md §9',
    },
    {
      rule: 'yard uses the same militarize pass as the watchtower — trodden ground close to the wall, anvil removed (anvil belongs in armory), campfire as warmth, barrels and chests only — never hay_bales or decorated pots.',
      source: 'military-style.md §9, compose.py §"militarize"',
    },
  ],
  antiPatterns: [
    {
      rule: 'cap_pillars applied at low rungs — the user confirmed the empty spots in low-level houses are meant to look unfinished. Apply only at rung ≥ 4.',
      source: 'military-style.md §16 anti-pattern 8, compose.py §"cap_pillars" docstring',
    },
    {
      rule: 'a colonnade on BOTH long faces — collapses the asymmetry that survives from the donor.',
      source: 'military-style.md §9',
    },
    {
      rule: 'removed beds across rungs — beds are monotonic-additive, never removed.',
      source: 'military-style.md §9',
    },
    {
      rule: 'banners at rung ≤ 3 — a poor early building should not be flying colours.',
      source: 'military-style.md §9',
    },
    {
      rule: 'iron_bars on a wall opening — palace vocabulary; the loophole opener is oak_fence or glass_pane.',
      source: 'military-style.md §16 anti-pattern 10',
    },
    {
      rule: 'deepslate anywhere — banned outright from the military set.',
      source: 'military-style.md §16 anti-pattern 14',
    },
  ],
  donors: {
    lvl0: 'barracks.nbt',
    lvl1: 'barracks_lvl1.nbt',
    lvl2: 'barracks_lvl2.nbt',
    lvl3: 'barracks_lvl3.nbt',
    lvl4: 'barracks_lvl4.nbt',
    lvl5: 'barracks_lvl5.nbt',
    lvl6: 'barracks_lvl6.nbt',
  },
};

const TRAINING_YARD: FamilyGrammar = {
  family: 'training_yard',
  rungs: {
    lvl0: { width: 13, depth: 11, height: 7, observedIn: 'training_yard.nbt' },
    lvl1: { width: 15, depth: 11, height: 9, observedIn: 'training_yard_lvl1.nbt' },
    lvl2: { width: 15, depth: 13, height: 9, observedIn: 'training_yard_lvl2.nbt' },
  },
  materials: {
    always: [
      'oak_log[axis=y]',
      'stripped_oak_log',
      'cobblestone',
      'mossy_cobblestone',
      'coarse_dirt',
      'dirt_path',
      'packed_mud',
      'dirt',
      'campfire',
    ],
    common: [
      'oak_planks',
      'oak_stairs',
      'grass_block',
      'oak_fence',
    ],
    rare: [
      'oak_fence_gate',
      'oak_leaves',
      'hay_block',
      'short_grass',
    ],
    banned: [
      'minecraft:grass',
      'iron_bars',
      'iron_door',
      'iron_trapdoor',
      'deepslate',
      'cobbled_deepslate',
      'nether_bricks',
      'blackstone',
      'basalt',
      'quartz_block',
      'smooth_quartz',
      'polished_andesite',
      'polished_granite',
      'polished_diorite',
      'chiseled_stone_bricks',
      'stone_brick_wall',
      'smooth_stone',
      'white_terracotta',
      'glass_pane',
      'oak_door',
      'oak_planks',
      'oak_slab',
    ],
  },
  devices: {
    required: [
      { kind: 'open_air', rule: 'no roof over the drill ring. The canopy is one wall of a gable, not a roof over the whole ring.' },
      { kind: 'palisade', rule: 'oak_log[axis=y] ring posts at one-cell intervals (or two-cell on battlemented rungs), carrying a stripped_oak_log[axis=axis] beam on top.' },
    ],
    common: ['campfire at the front (warmth source only)'],
    signature: [
      'oak_log[axis=y] palisade ring',
      'oak_planks canopy over oak_stairs gable at the back',
      'battlemented ring (lvl1+) with cobblestone in piers and merlons',
      'canopy count grows 1 → 2 → 3 across rungs',
    ],
  },
  composition: [
    {
      rule: 'open-air construction — fence perimeter around the drill ring, no roof over the whole yard.',
      source: 'military-style.md §11 anatomy, compose.py §"compose_yard"',
    },
    {
      rule: 'interior yard carries NO buildings and NO workstations — only a campfire at the front; the author never places a workshop in a yard.',
      source: 'military-style.md §11',
    },
    {
      rule: 'battlemented rungs: cobblestone replaces oak_log ring posts in piers (every 4 cells); merlons (one-block high) on the beam between piers.',
      source: 'military-style.md §11',
    },
    {
      rule: 'canopy count grows from 1 (back) at lvl0 to 2 (back+left) at lvl1 to 3 (back+left+right) at lvl2.',
      source: 'military-style.md §11',
    },
    {
      rule: 'yard widens to 13×11 → 13×11 → 15×13 across the ladder — the interior grows with the canopy.',
      source: 'military-style.md §11',
    },
  ],
  antiPatterns: [
    {
      rule: 'a smooth_stone floor inside the ring — reads as a parade ground, not a training ground (plains-style.md §13 anti-pattern 4 applies in reverse).',
      source: 'military-style.md §11',
    },
    {
      rule: 'a roof over the whole drill ring — the canopy is one wall of a gable, not the whole yard.',
      source: 'military-style.md §11',
    },
    {
      rule: 'a workshop or workstation inside the yard — the author never places a workshop in a yard.',
      source: 'military-style.md §11',
    },
    {
      rule: 'iron_bars on a wall opening — palace vocabulary; the loophole opener is oak_fence or glass_pane.',
      source: 'military-style.md §16 anti-pattern 10',
    },
    {
      rule: 'a yard with battlements and three canopies reading as a parade ground — ladder stops at 2 because a yard with battlements and three canopies is a parade ground, not a training ground.',
      source: 'military-style.md §2 eight families',
    },
  ],
  donors: {
    lvl0: 'training_yard.nbt',
    lvl1: 'training_yard_lvl1.nbt',
    lvl2: 'training_yard_lvl2.nbt',
  },
};

const BANDS_STYLE_GRAMMAR = {
  mirrorSymmetryMax: 0.685,
  densityMedian: 0.25,
  paletteMedian: 139,
} as const;

export const STYLE_GRAMMAR: StyleGrammar = {
  plains: {
    house: HOUSES,
    job: JOBS,
    garden: GARDENS,
    street: STREETS,
    starter: STARTERS,
  },
  military: {
    wall_segment: WALL_SEGMENT,
    wall_corner: WALL_CORNER,
    wall_tower: WALL_TOWER,
    gatehouse: GATEHOUSE,
    watchtower: WATCHTOWER,
    armory: ARMORY,
    barracks: BARRACKS,
    training_yard: TRAINING_YARD,
  },
  bands: BANDS_STYLE_GRAMMAR,
};
