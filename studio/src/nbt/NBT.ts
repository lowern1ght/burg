// Burg Studio — NBT (Named Binary Tag) parser for Minecraft structure (.nbt) files.
//
// FORMAT NOTE (measured, not asserted): the .nbt files in this repo use the
// STANDARD Minecraft structure format (the one structure blocks / `/save` emit),
// NOT the chunk-section bit-packed `data` Int_Array encoding. Concretely, after
// gzip-decompression the root compound holds:
//
//   "" (root compound)
//     size:        List<Int>   [x, y, z]            // bounding box, NOT a compound
//     palette:     List<Compound> { Name: String, Properties?: Compound<String> }
//     blocks:      List<Compound> { pos: List<Int>[3], state: Int, nbt?: Compound }
//     entities:    List<Compound> { pos: List<Double>[3], nbt: { id: String, ... } }
//     block_entities: List<Compound>  (newer block-entity storage; parsed, not rendered)
//     DataVersion: Int
//
// `blocks` is SPARSE: a cell is listed only when it carries a block. Air is
// either absent entirely or, in some saves, present as an explicit
// `minecraft:air` palette entry. There is no bit-packing and no `data` array.

export interface ParsedBlock {
  id: string;
  properties: Record<string, string>;
}

export interface ParsedEntity {
  pos: [number, number, number];
  id: string;
}

export interface ParsedStructure {
  size: [number, number, number];
  /** Dense block grid, length = size[0] * size[1] * size[2], indexed `y*sizeZ*sizeX + z*sizeX + x`. Air cells are `{ id: "minecraft:air", properties: {} }`. */
  blocks: ParsedBlock[];
  entities: ParsedEntity[];
}

export const AIR_BLOCK: Readonly<ParsedBlock> = Object.freeze({
  id: 'minecraft:air',
  properties: {},
});

export function isAir(block: ParsedBlock): boolean {
  return block.id === AIR_BLOCK.id;
}

// ---------------------------------------------------------------------------
// Tag type ids
// ---------------------------------------------------------------------------

const TAG_END = 0;
const TAG_BYTE = 1;
const TAG_SHORT = 2;
const TAG_INT = 3;
const TAG_LONG = 4;
const TAG_FLOAT = 5;
const TAG_DOUBLE = 6;
const TAG_BYTE_ARRAY = 7;
const TAG_STRING = 8;
const TAG_LIST = 9;
const TAG_COMPOUND = 10;
const TAG_INT_ARRAY = 11;
const TAG_LONG_ARRAY = 12;

// ---------------------------------------------------------------------------
// Generic reader -> untyped JS value tree
//   byte/short/int  -> number
//   long            -> bigint
//   float/double    -> number
//   string          -> string
//   byte_array      -> number[]
//   int_array       -> number[]
//   long_array      -> bigint[]
//   list            -> unknown[]
//   compound        -> object with null prototype (insertion-ordered)
// ---------------------------------------------------------------------------

type NbtValue = number | bigint | string | boolean | unknown[] | Record<string, unknown>;

class NbtReader {
  private readonly view: DataView;
  private pos = 0;
  private readonly decoder = new TextDecoder('utf-8');

  constructor(private readonly bytes: Uint8Array) {
    this.view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  }

  readByte(): number {
    return this.view.getInt8(this.pos++);
  }

  readShort(): number {
    const v = this.view.getInt16(this.pos);
    this.pos += 2;
    return v;
  }

  readInt(): number {
    const v = this.view.getInt32(this.pos);
    this.pos += 4;
    return v;
  }

  readLong(): bigint {
    const v = this.view.getBigInt64(this.pos);
    this.pos += 8;
    return v;
  }

  readFloat(): number {
    const v = this.view.getFloat32(this.pos);
    this.pos += 4;
    return v;
  }

  readDouble(): number {
    const v = this.view.getFloat64(this.pos);
    this.pos += 8;
    return v;
  }

  readString(): string {
    const len = this.view.getUint16(this.pos);
    this.pos += 2;
    const s = this.decoder.decode(this.bytes.subarray(this.pos, this.pos + len));
    this.pos += len;
    return s;
  }

  readPayload(type: number): NbtValue {
    switch (type) {
      case TAG_BYTE:
        return this.readByte();
      case TAG_SHORT:
        return this.readShort();
      case TAG_INT:
        return this.readInt();
      case TAG_LONG:
        return this.readLong();
      case TAG_FLOAT:
        return this.readFloat();
      case TAG_DOUBLE:
        return this.readDouble();
      case TAG_BYTE_ARRAY: {
        const n = this.readInt();
        const out: number[] = new Array(n);
        for (let i = 0; i < n; i++) out[i] = this.view.getInt8(this.pos++);
        return out;
      }
      case TAG_STRING:
        return this.readString();
      case TAG_LIST: {
        const elementType = this.readByte();
        const n = this.readInt();
        const out: unknown[] = new Array(n);
        for (let i = 0; i < n; i++) out[i] = this.readPayload(elementType);
        return out;
      }
      case TAG_COMPOUND:
        return this.readCompound();
      case TAG_INT_ARRAY: {
        const n = this.readInt();
        const out: number[] = new Array(n);
        for (let i = 0; i < n; i++) out[i] = this.readInt();
        return out;
      }
      case TAG_LONG_ARRAY: {
        const n = this.readInt();
        const out: bigint[] = new Array(n);
        for (let i = 0; i < n; i++) out[i] = this.readLong();
        return out;
      }
      case TAG_END:
        throw new Error('NBT: unexpected TAG_End payload');
      default:
        throw new Error(`NBT: unknown tag type ${type} at byte ${this.pos - 1}`);
    }
  }

  readCompound(): Record<string, unknown> {
    const obj: Record<string, unknown> = Object.create(null);
    let type: number;
    while ((type = this.readByte()) !== TAG_END) {
      const name = this.readString();
      obj[name] = this.readPayload(type);
    }
    return obj;
  }
}

// ---------------------------------------------------------------------------
// Structure extraction (generic tree -> ParsedStructure)
// ---------------------------------------------------------------------------

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? (value as unknown[]) : [];
}

function asCompound(value: unknown): Record<string, unknown> {
  return (value && typeof value === 'object') ? (value as Record<string, unknown>) : Object.create(null);
}

function propertiesOf(raw: unknown): Record<string, string> {
  const compound = asCompound(raw);
  const out: Record<string, string> = {};
  for (const key of Object.keys(compound)) {
    const v = compound[key];
    out[key] = typeof v === 'string' ? v : String(v ?? '');
  }
  return out;
}

function readSize(root: Record<string, unknown>): [number, number, number] {
  const raw = root.size;
  if (Array.isArray(raw) && raw.length >= 3) {
    return [Number(raw[0]), Number(raw[1]), Number(raw[2])];
  }
  if (raw && typeof raw === 'object') {
    const c = raw as Record<string, unknown>;
    return [Number(c.x), Number(c.y), Number(c.z)];
  }
  throw new Error('NBT: structure has no valid size tag');
}

function toStructure(root: Record<string, unknown>): ParsedStructure {
  const [sizeX, sizeY, sizeZ] = readSize(root);
  const total = sizeX * sizeY * sizeZ;
  if (!Number.isFinite(total) || total < 0) {
    throw new Error(`NBT: invalid size product ${total} for size [${sizeX},${sizeY},${sizeZ}]`);
  }

  const palette = asArray(root.palette).length > 0
    ? asArray(root.palette)
    : asArray(root.palettes)[0] instanceof Array
      ? (asArray(root.palettes)[0] as unknown[])
      : [];

  const paletteEntries = palette.map((entry) => {
    const c = asCompound(entry);
    return {
      id: typeof c.Name === 'string' ? c.Name : 'minecraft:air',
      properties: propertiesOf(c.Properties),
    } satisfies ParsedBlock;
  });

  // Dense grid, every cell air until a block places into it. Air cells share one
  // frozen reference; nothing mutates a ParsedBlock.
  const blocks: ParsedBlock[] = new Array(total).fill(AIR_BLOCK);

  for (const rawBlock of asArray(root.blocks)) {
    const c = asCompound(rawBlock);
    const pos = asArray(c.pos);
    const x = Number(pos[0]);
    const y = Number(pos[1]);
    const z = Number(pos[2]);
    const state = Number(c.state);
    if (!Number.isInteger(x) || !Number.isInteger(y) || !Number.isInteger(z) || !Number.isInteger(state)) {
      continue;
    }
    if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) {
      continue;
    }
    const entry = paletteEntries[state] ?? AIR_BLOCK;
    const index = y * sizeZ * sizeX + z * sizeX + x;
    blocks[index] = { id: entry.id, properties: { ...entry.properties } };
  }

  const entities: ParsedEntity[] = asArray(root.entities).map((rawEntity) => {
    const c = asCompound(rawEntity);
    const pos = asArray(c.pos).length === 3 ? asArray(c.pos) : asArray(asCompound(c.nbt).Pos);
    const nbt = asCompound(c.nbt);
    const id = typeof nbt.id === 'string' ? nbt.id : 'unknown';
    return { pos: [Number(pos[0] ?? 0), Number(pos[1] ?? 0), Number(pos[2] ?? 0)], id };
  });

  return { size: [sizeX, sizeY, sizeZ], blocks, entities };
}

// ---------------------------------------------------------------------------
// Purpose-built writer (reconstructs the known structure shape with explicit
// tag types, so there is no int/double ambiguity).
// ---------------------------------------------------------------------------

const DEFAULT_DATA_VERSION = 3465;

class NbtWriter {
  private readonly parts: number[] = [];
  private readonly encoder = new TextEncoder();

  private u8(v: number): void {
    this.parts.push(v & 0xff);
  }

  private i16(v: number): void {
    this.parts.push((v >> 8) & 0xff, v & 0xff);
  }

  private i32(v: number): void {
    this.parts.push((v >>> 24) & 0xff, (v >>> 16) & 0xff, (v >>> 8) & 0xff, v & 0xff);
  }

  private f64(v: number): void {
    const buf = new ArrayBuffer(8);
    new DataView(buf).setFloat64(0, v);
    const u = new Uint8Array(buf);
    for (const b of u) this.parts.push(b);
  }

  private string(s: string): void {
    const b = this.encoder.encode(s);
    this.i16(b.length);
    for (const x of b) this.parts.push(x);
  }

  /** Named tag header: type byte + name, then the payload written by `body`. */
  private entry(type: number, name: string, body: () => void): void {
    this.u8(type);
    this.string(name);
    body();
  }

  /** Compound body: `entries` writes each child tag, then TAG_End. */
  private compoundBody(entries: () => void): void {
    entries();
    this.u8(TAG_END);
  }

  toBytes(): Uint8Array {
    return new Uint8Array(this.parts);
  }

  writeStructure(s: ParsedStructure): void {
    const [sizeX, sizeY, sizeZ] = s.size;

    // Root named compound "".
    this.u8(TAG_COMPOUND);
    this.string('');
    this.compoundBody(() => {
      // size: List<Int> [x, y, z]
      this.entry(TAG_LIST, 'size', () => {
        this.u8(TAG_INT);
        this.i32(3);
        this.i32(sizeX);
        this.i32(sizeY);
        this.i32(sizeZ);
      });

      // Build a palette from non-air blocks (first-occurrence order, deterministic).
      const palette: ParsedBlock[] = [];
      const keyToIndex = new Map<string, number>();
      const placed: { x: number; y: number; z: number; state: number }[] = [];
      for (let y = 0; y < sizeY; y++) {
        for (let z = 0; z < sizeZ; z++) {
          for (let x = 0; x < sizeX; x++) {
            const block = s.blocks[y * sizeZ * sizeX + z * sizeX + x];
            if (!block || isAir(block)) continue;
            const key = blockKey(block);
            let state = keyToIndex.get(key);
            if (state === undefined) {
              state = palette.length;
              palette.push(block);
              keyToIndex.set(key, state);
            }
            placed.push({ x, y, z, state });
          }
        }
      }

      // entities: List<Compound>
      this.entry(TAG_LIST, 'entities', () => {
        this.u8(TAG_COMPOUND);
        this.i32(s.entities.length);
        for (const e of s.entities) {
          this.compoundBody(() => {
            this.entry(TAG_LIST, 'pos', () => {
              this.u8(TAG_DOUBLE);
              this.i32(3);
              this.f64(e.pos[0]);
              this.f64(e.pos[1]);
              this.f64(e.pos[2]);
            });
            this.entry(TAG_COMPOUND, 'nbt', () => {
              this.compoundBody(() => {
                this.entry(TAG_STRING, 'id', () => this.string(e.id));
              });
            });
          });
        }
      });

      // blocks: List<Compound> { pos: List<Int>[3], state: Int }
      this.entry(TAG_LIST, 'blocks', () => {
        this.u8(TAG_COMPOUND);
        this.i32(placed.length);
        for (const p of placed) {
          this.compoundBody(() => {
            this.entry(TAG_LIST, 'pos', () => {
              this.u8(TAG_INT);
              this.i32(3);
              this.i32(p.x);
              this.i32(p.y);
              this.i32(p.z);
            });
            this.entry(TAG_INT, 'state', () => this.i32(p.state));
          });
        }
      });

      // palette: List<Compound> { Name, Properties? }
      this.entry(TAG_LIST, 'palette', () => {
        this.u8(TAG_COMPOUND);
        this.i32(palette.length);
        for (const entryBlock of palette) {
          this.compoundBody(() => {
            this.entry(TAG_STRING, 'Name', () => this.string(entryBlock.id));
            const keys = Object.keys(entryBlock.properties);
            if (keys.length > 0) {
              this.entry(TAG_COMPOUND, 'Properties', () => {
                this.compoundBody(() => {
                  for (const k of keys) {
                    this.entry(TAG_STRING, k, () => this.string(entryBlock.properties[k]));
                  }
                });
              });
            }
          });
        }
      });

      this.entry(TAG_INT, 'DataVersion', () => this.i32(DEFAULT_DATA_VERSION));
    });
  }
}

function blockKey(block: ParsedBlock): string {
  const props = Object.keys(block.properties).sort();
  return `${block.id}|${props.map((k) => `${k}=${block.properties[k]}`).join(',')}`;
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Parse a DECOMPRESSED NBT byte stream into a structure.
 *
 * `parseNbt` is deliberately synchronous and pure: gzip is I/O and cannot be
 * done synchronously in a browser, so the compression layer lives in
 * {@link parseNbtFile} / {@link serializeNbtFile}. Tests decompress with
 * `node:zlib` and call this directly.
 */
export function parseNbt(bytes: Uint8Array): ParsedStructure {
  const reader = new NbtReader(bytes);
  const rootType = reader.readByte();
  if (rootType !== TAG_COMPOUND) {
    throw new Error(`NBT: root tag must be a compound, got type ${rootType}`);
  }
  reader.readString(); // root name (usually "")
  const root = reader.readCompound();
  return toStructure(root);
}

/** Serialize a structure to DECOMPRESSED NBT bytes (the inverse of {@link parseNbt}). */
export function serializeNbt(structure: ParsedStructure): Uint8Array {
  const writer = new NbtWriter();
  writer.writeStructure(structure);
  return writer.toBytes();
}

/** True when `bytes` begins with the gzip magic (0x1f 0x8b). */
function isGzip(bytes: Uint8Array): boolean {
  return bytes.length >= 2 && bytes[0] === 0x1f && bytes[1] === 0x8b;
}

// `DecompressionStream`/`CompressionStream` are typed with `WritableStream<
// BufferSource>`, which TS 5.7's `Uint8Array<ArrayBufferLike>` default will not
// structurally satisfy through `pipeThrough`. At runtime every chunk here is an
// ArrayBuffer-backed Uint8Array, so a single boundary cast is type-honest.
type ByteTransform = ReadableWritablePair<Uint8Array, Uint8Array>;

async function gunzip(bytes: Uint8Array): Promise<Uint8Array> {
  if (!isGzip(bytes)) return bytes;
  const stream = byteStream(bytes).pipeThrough(
    new DecompressionStream('gzip') as unknown as ByteTransform,
  );
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

async function gzip(bytes: Uint8Array): Promise<ArrayBuffer> {
  const stream = byteStream(bytes).pipeThrough(
    new CompressionStream('gzip') as unknown as ByteTransform,
  );
  return await new Response(stream).arrayBuffer();
}

/** Wrap raw bytes in a ReadableStream without going through Blob (sidesteps the
 *  Uint8Array<ArrayBufferLike> vs BlobPart lib-dom friction under TS 5.7). */
function byteStream(bytes: Uint8Array): ReadableStream<Uint8Array> {
  return new ReadableStream({
    start(controller) {
      controller.enqueue(bytes);
      controller.close();
    },
  });
}

/**
 * Parse a gzip-compressed .nbt file (the bytes from `File.arrayBuffer()` or a
 * raw disk read). Works in the browser (DecompressionStream) and in Node 18+.
 */
export async function parseNbtFile(buffer: ArrayBuffer | Uint8Array): Promise<ParsedStructure> {
  const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
  const decompressed = await gunzip(bytes);
  return parseNbt(decompressed);
}

/** Serialize a structure back to a gzip-compressed .nbt file. */
export async function serializeNbtFile(structure: ParsedStructure): Promise<ArrayBuffer> {
  return gzip(serializeNbt(structure));
}
