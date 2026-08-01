import { describe, expect, it } from 'vitest';
import { gunzipSync } from 'node:zlib';
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { AIR_BLOCK, isAir, parseNbt, serializeNbt } from '../NBT';
import type { ParsedStructure } from '../NBT';

// Fixtures live in the read-only mod resources tree (sibling of studio/).
const REPO_ROOT = path.resolve(__dirname, '../../../..');
const STRUCT = path.join(
  REPO_ROOT,
  'common/src/main/resources/data/onceuponatown/structure',
);
const FIXTURES = {
  street3: path.join(STRUCT, 'plains/streets/street_3.nbt'),
  house2: path.join(STRUCT, 'plains/houses/house_2_lvl1.nbt'),
  watchtower: path.join(STRUCT, 'military/watchtower/watchtower.nbt'),
} as const;

/** Decompress a real .nbt file and parse it. */
function loadFixture(file: string): ParsedStructure {
  const compressed = readFileSync(file);
  const decompressed = gunzipSync(compressed);
  return parseNbt(new Uint8Array(decompressed));
}

describe('NBT parser — real structure fixtures', () => {
  describe('street_3.nbt (481 bytes, palette-only)', () => {
    const structure = loadFixture(FIXTURES.street3);

    it('has the expected bounding box [15, 1, 9]', () => {
      expect(structure.size).toEqual([15, 1, 9]);
    });

    it('materialises a dense block grid of size x*y*z', () => {
      const [x, y, z] = structure.size;
      expect(structure.blocks).toHaveLength(x * y * z);
    });

    it('keeps every non-air block from the source', () => {
      // street_3 ships 42 dirt_path + 4 jigsaw = 46 non-air blocks.
      const nonAir = structure.blocks.filter((block) => !isAir(block));
      expect(nonAir).toHaveLength(46);
    });

    it('fills unlisted cells with air and keeps listed ones', () => {
      // size = [15,1,9] → index = y*9*15 + z*15 + x
      const idx = (x: number, y: number, z: number) => y * 9 * 15 + z * 15 + x;

      expect(structure.blocks).toContainEqual(AIR_BLOCK);
      // (0,0,1) is dirt_path in the source (first listed block).
      expect(structure.blocks[idx(0, 0, 1)]).toMatchObject({ id: 'minecraft:dirt_path' });
      // (0,0,0) is a jigsaw marker in the source, not air.
      expect(isAir(structure.blocks[idx(0, 0, 0)])).toBe(false);
      // (0,0,2) is never listed → materialised as air.
      expect(isAir(structure.blocks[idx(0, 0, 2)])).toBe(true);
    });
  });

  describe('house_2_lvl1.nbt (5039 bytes, explicit air)', () => {
    const structure = loadFixture(FIXTURES.house2);

    it('has the expected bounding box [12, 12, 12]', () => {
      expect(structure.size).toEqual([12, 12, 12]);
    });

    it('materialises a dense block grid of size x*y*z', () => {
      const [x, y, z] = structure.size;
      expect(structure.blocks).toHaveLength(x * y * z);
    });

    it('collapses both listed-air and unlisted cells to the air block', () => {
      // The source lists 1138 explicit air blocks; they must read back as air,
      // and every other empty cell must also be air.
      const nonAir = structure.blocks.filter((block) => !isAir(block));
      // 1464 total blocks in the file, 1138 of them air → 326 non-air.
      expect(nonAir).toHaveLength(1464 - 1138);
      for (const block of structure.blocks) {
        expect(typeof block.id).toBe('string');
      }
    });
  });

  describe('watchtower.nbt (1791 bytes, no air in palette)', () => {
    const structure = loadFixture(FIXTURES.watchtower);

    it('has the expected bounding box [9, 12, 8]', () => {
      expect(structure.size).toEqual([9, 12, 8]);
    });

    it('materialises a dense block grid of size x*y*z', () => {
      const [x, y, z] = structure.size;
      expect(structure.blocks).toHaveLength(x * y * z);
    });

    it('keeps every non-air block from the source', () => {
      const nonAir = structure.blocks.filter((block) => !isAir(block));
      // The watchtower ships 301 placed blocks, none of them air.
      expect(nonAir).toHaveLength(301);
    });
  });

  describe('round-trip (parse -> serialize -> parse) on all three fixtures', () => {
    for (const [name, file] of Object.entries(FIXTURES)) {
      it(`round-trips ${name} to an identical ParsedStructure`, () => {
        const first = loadFixture(file);
        const serialized = serializeNbt(first);
        const second = parseNbt(serialized);

        expect(second.size).toEqual(first.size);
        expect(second.entities).toEqual(first.entities);
        // Deep equality ignores object key order, which is what we want: the
        // rebuilt palette may reorder entries, but every cell must resolve to
        // the same { id, properties }.
        expect(second.blocks).toEqual(first.blocks);
      });
    }
  });

  describe('error handling', () => {
    it('rejects a non-compound root', () => {
      // TAG_Int (3) as root type, followed by a name + payload.
      expect(() => parseNbt(new Uint8Array([3, 0, 1, 65, 0, 0, 0, 1]))).toThrow(/compound/);
    });
  });
});
