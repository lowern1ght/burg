"""Build a self-contained canvas-based gallery of Burg structures.

Generates ``tools/structures/out/index-canvas.html`` — a single HTML file
with:

  * 5 ``<canvas>`` elements, one per building, drawn in isometric projection
  * Real Minecraft 1.21.1 block textures (39 PNGs from the MC jar) embedded
    as ``data:image/png;base64,...`` URIs.
  * The 5 demo NBT files embedded as gzipped base64, decompressed and parsed
    in the browser with a tiny NBT parser (no network requests, no fetch()).

Hidden-faces optimization: for each block we draw only the top, east, and
south faces that aren't covered by a neighbour block. Pixel-corners align
because the iso projection is the same as the existing SVG renderer:

    screen_x = (x - z) * TILE_W
    screen_y = (x + z) * TILE_H - y * BLOCK_DEPTH

Each face is mapped to a parallelogram via ``ctx.setTransform`` so the
16x16 source texture stretches across the parallelogram. Adjacent cubes
share edges perfectly because the projection is consistent.

Run:
    python tools/build_gallery_canvas.py
"""

from __future__ import annotations

import base64
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

OUT_DIR = HERE / "structures" / "out"
TEX_DIR = HERE / "structures" / "textures"
INDEX_JSON = TEX_DIR / "index.json"


# Buildings to render, paired with their structure size (sx, sy, sz).
# size is needed only for canvas fitting fallback; the JS derives real
# bounds from the projected geometry anyway.
BUILDINGS: list[tuple[str, tuple[int, int, int]]] = [
    ("cottage_small",   (5, 5, 4)),
    ("smithy_stone",    (6, 5, 5)),
    ("watchtower_wood", (3, 9, 3)),
    ("market_stall",    (4, 3, 3)),
    ("bridge_section",  (6, 3, 2)),
]


# Color fallback for blocks that have no texture in the atlas. Copies the
# color dict from ``registry.py`` so unknown blocks still look reasonable.
COLOR_FALLBACK: dict[str, str] = {
    "minecraft:oak_planks":        "#b88a4e",
    "minecraft:spruce_planks":     "#7a5a32",
    "minecraft:birch_planks":      "#dcc888",
    "minecraft:dark_oak_planks":   "#4a3320",
    "minecraft:oak_log":           "#6b4a25",
    "minecraft:spruce_log":        "#3b2c1a",
    "minecraft:oak_stairs":        "#a07540",
    "minecraft:oak_slab":          "#b88a4e",
    "minecraft:oak_fence":         "#7a5a32",
    "minecraft:oak_door":          "#9c703c",
    "minecraft:oak_trapdoor":      "#8a6234",
    "minecraft:spruce_slab":       "#7a5a32",
    "minecraft:spruce_stairs":     "#7a5a32",
    "minecraft:birch_planks":      "#dcc888",
    "minecraft:cobblestone":       "#7e7e7e",
    "minecraft:stone":             "#9a9a9a",
    "minecraft:stone_bricks":      "#a0a0a0",
    "minecraft:mossy_cobblestone": "#6f8060",
    "minecraft:stone_stairs":      "#9a9a9a",
    "minecraft:stone_slab":        "#9a9a9a",
    "minecraft:glass":             "#cfe9f5",
    "minecraft:glass_pane":        "#dfeef7",
    "minecraft:torch":             "#ffcc44",
    "minecraft:lantern":           "#f5b76b",
    "minecraft:hay_block":         "#c9b14a",
    "minecraft:dirt":              "#8b6a3f",
    "minecraft:grass_block":       "#5fa84a",
    "minecraft:water":             "#3b6ec4",
    "minecraft:crafting_table":    "#7a5a32",
    "minecraft:wheat":             "#d4c042",
    "minecraft:carrots":           "#e07a2c",
    "minecraft:potatoes":          "#b59c5e",
    "minecraft:beehive":           "#e6c66e",
    "minecraft:campfire":          "#a04a2c",
    "minecraft:bookshelf":         "#9c703c",
    "minecraft:chest":             "#9c703c",
    "minecraft:barrel":            "#7a5a32",
    "minecraft:flower_pot":        "#a87347",
    "minecraft:ladder":            "#7a5a32",
    "minecraft:furnace":           "#7e7e7e",
    "minecraft:blast_furnace":     "#7e7e7e",
    "minecraft:smoker":            "#7e7e7e",
    "minecraft:anvil":             "#888888",
}


def encode_textures() -> dict[str, str]:
    """Return {block_id: 'data:image/png;base64,...'} for every entry in index.json.

    The on-disk index uses ``minecraft:block/<name>`` (asset-namespace form).
    We strip the ``block/`` path segment so the atlas is keyed by the same
    form as the NBT palette (``minecraft:<name>``), letting the JS renderer
    match texture to palette in one line of look-up.
    """
    index = json.loads(INDEX_JSON.read_text(encoding="utf-8"))
    out: dict[str, str] = {}
    for block_id, filename in index.items():
        # ``minecraft:block/oak_planks`` -> ``minecraft:oak_planks``
        key = block_id.replace(":block/", ":", 1)
        png_bytes = (TEX_DIR / filename).read_bytes()
        b64 = base64.b64encode(png_bytes).decode("ascii")
        out[key] = f"data:image/png;base64,{b64}"
    return out


def encode_nbt(name: str) -> str:
    """Read the on-disk NBT file and return it as base64 (still gzipped)."""
    nbt_bytes = (OUT_DIR / f"{name}.nbt").read_bytes()
    return base64.b64encode(nbt_bytes).decode("ascii")


# ---------------------------------------------------------------------------
# Embedded JavaScript
# ---------------------------------------------------------------------------

JS_SOURCE = r"""
// Burg canvas renderer — isometric view with real MC 1.21.1 textures.
//
// Pipeline:
//   1. Decode base64 of every texture into an <img>; build the atlas Map.
//   2. For each building: base64 -> gunzip -> parseNBT -> renderBuilding.
//
// Hidden-faces culling + depth-sort keeps the drawing painter's-algorithm
// correct. Adjacent cubes share edges because the projection is identical
// to the existing SVG renderer.

const TILE_W = 22;
const TILE_H = 11;
const BLOCK_DEPTH = 22;

// ----------- NBT parser (gzipped Java Edition structure format) ----------

async function gunzip(arrayBuffer) {
    const stream = new Response(arrayBuffer).body.pipeThrough(
        new DecompressionStream('gzip')
    );
    return await new Response(stream).arrayBuffer();
}

function base64ToBytes(b64) {
    const bin = atob(b64);
    const out = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
    return out;
}

function parseNBT(arrayBuffer) {
    const buf = new Uint8Array(arrayBuffer);
    const view = new DataView(arrayBuffer);
    let off = 0;

    const rdByte   = () => buf[off++];
    const rdShort  = () => { const v = view.getUint16(off); off += 2; return v; };
    const rdInt    = () => { const v = view.getInt32(off);  off += 4; return v; };
    const rdLong   = () => { const v = Number(view.getBigInt64(off)); off += 8; return v; };
    const rdFloat  = () => { const v = view.getFloat32(off);  off += 4; return v; };
    const rdDouble = () => { const v = view.getFloat64(off);  off += 8; return v; };
    const rdStr    = () => {
        const len = rdShort();
        const bytes = buf.subarray(off, off + len);
        off += len;
        return new TextDecoder('utf-8').decode(bytes);
    };

    function rdPayload(type) {
        switch (type) {
            case 0:  return undefined;            // TAG_End — never reached via this entry
            case 1:  return rdByte();
            case 2:  return rdShort();
            case 3:  return rdInt();
            case 4:  return rdLong();
            case 5:  return rdFloat();
            case 6:  return rdDouble();
            case 7: {
                const n = rdInt();
                const a = new Array(n);
                for (let i = 0; i < n; i++) a[i] = rdByte();
                return a;
            }
            case 8:  return rdStr();
            case 9:  {                             // TAG_List
                const elem = rdByte();
                const n = rdInt();
                const a = new Array(n);
                for (let i = 0; i < n; i++) a[i] = rdPayload(elem);
                return a;
            }
            case 10: return rdCompound();
            case 11: {                             // TAG_IntArray
                const n = rdInt();
                const a = new Array(n);
                for (let i = 0; i < n; i++) a[i] = rdInt();
                return a;
            }
            case 12: {                             // TAG_LongArray
                const n = rdInt();
                const a = new Array(n);
                for (let i = 0; i < n; i++) a[i] = rdLong();
                return a;
            }
            default:
                throw new Error(`Unknown NBT tag type ${type} at offset ${off}`);
        }
    }

    function rdCompound() {
        const obj = {};
        while (true) {
            const t = rdByte();
            if (t === 0) break;                    // TAG_End
            const name = rdStr();
            obj[name] = rdPayload(t);
        }
        return obj;
    }

    // Root: 1-byte type + 2-byte name length + name bytes + payload.
    // For Burg structure files: type=10 (Compound), name="".
    const rootType = rdByte();
    if (rootType !== 10) {
        throw new Error(`Root must be Compound (10), got ${rootType}`);
    }
    rdStr();  // skip the empty root name
    return rdCompound();
}

// --------------- projection + canvas rendering ----------------

function project(x, y, z) {
    return [(x - z) * TILE_W, (x + z) * TILE_H - y * BLOCK_DEPTH];
}

// Map a 16x16 source texture across a 4-vertex parallelogram (screen-space)
// by setting the canvas transform such that:
//
//   texture (0,0)   -> p0
//   texture (16,0)  -> p1
//   texture (0,16)  -> p3     (so texture "down" follows p3-p0 edge)
//
// The 4th corner p2 then lands at the correct (16,16) spot because
// parallelograms are affine images of the unit square.
function paintFace(ctx, image, sw, sh, p0, p1, p3) {
    const e = p0[0],          f = p0[1];
    const a = (p1[0] - e) / sw,  b = (p1[1] - f) / sw;
    const c = (p3[0] - e) / sh,  d = (p3[1] - f) / sh;
    ctx.setTransform(a, b, c, d, e, f);
    ctx.drawImage(image, 0, 0, sw, sh);
}

// Fill a parallelogram with a solid color (texture fallback).
function fillFace(ctx, p0, p1, p2, p3) {
    ctx.beginPath();
    ctx.moveTo(p0[0], p0[1]);
    ctx.lineTo(p1[0], p1[1]);
    ctx.lineTo(p2[0], p2[1]);
    ctx.lineTo(p3[0], p3[1]);
    ctx.closePath();
    ctx.fill();
}

// Polygon corners for the three visible faces of a cube at (x,y,z).
// V0, V1, V2, V3 are corners in CCW order seen from outside.
function cubeFaceVertices(x, y, z) {
    return {
        top:   [project(x,     y + 1, z    ),
                project(x + 1, y + 1, z    ),
                project(x + 1, y + 1, z + 1),
                project(x,     y + 1, z + 1)],
        east:  [project(x + 1, y,     z    ),
                project(x + 1, y + 1, z    ),
                project(x + 1, y + 1, z + 1),
                project(x + 1, y,     z + 1)],
        south: [project(x,     y,     z + 1),
                project(x + 1, y,     z + 1),
                project(x + 1, y + 1, z + 1),
                project(x,     y + 1, z + 1)],
    };
}

function renderBuilding(canvas, structure, atlas, fallback) {
    const ctx = canvas.getContext('2d');
    ctx.imageSmoothingEnabled = false;
    ctx.lineWidth = 0;

    const palette = structure.palette;
    const rawBlocks = structure.blocks.filter(b => b.state !== 0);

    // Pre-compute positions for hidden-face culling.
    const posSet = new Set(rawBlocks.map(b => `${b.pos[0]},${b.pos[1]},${b.pos[2]}`));
    const has = (x, y, z) => posSet.has(`${x},${y},${z}`);

    // Depth-sort: ascending (x+z), then descending y (taller blocks first).
    const blocks = rawBlocks.slice().sort((a, b) => {
        const da = a.pos[0] + a.pos[2];
        const db = b.pos[0] + b.pos[2];
        if (da !== db) return da - db;
        return b.pos[1] - a.pos[1];
    });

    // Compute screen-space bounds of the whole structure (corners of blocks,
    // not of placed cells only — keeps the floor cleanly laid out).
    const sx = structure.size[0], sy = structure.size[1], sz = structure.size[2];
    const allPts = [];
    for (let x = 0; x <= sx; x++) {
        for (let y = 0; y <= sy; y++) {
            for (let z = 0; z <= sz; z++) {
                const p = project(x, y, z);
                allPts.push(p);
            }
        }
    }
    let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
    for (const [px, py] of allPts) {
        if (px < minX) minX = px;
        if (px > maxX) maxX = px;
        if (py < minY) minY = py;
        if (py > maxY) maxY = py;
    }

    const margin = 30;
    const titlePad = 32;     // room for title + meta
    const widthPx  = Math.ceil(maxX - minX + 2 * margin);
    const heightPx = Math.ceil(maxY - minY + 2 * margin) + titlePad;

    canvas.width  = widthPx;
    canvas.height = heightPx;

    const ox = -minX + margin;
    const oy = -minY + margin + titlePad;

    // ----- canvas chrome -----
    ctx.fillStyle = '#1a1a1a';
    ctx.fillRect(0, 0, widthPx, heightPx);

    ctx.fillStyle = '#e6c66e';
    ctx.font = 'bold 14px ui-monospace, Menlo, Consolas, monospace';
    ctx.fillText(structure.name, margin, 22);

    ctx.fillStyle = '#888';
    ctx.font = '11px ui-monospace, Menlo, Consolas, monospace';
    ctx.fillText(
        `${sx}×${sy}×${sz} · ${rawBlocks.length} blocks · canvas iso`,
        margin, 38
    );

    ctx.setTransform(1, 0, 0, 1, ox, oy);

    // ----- blocks -----
    const usedTextures = new Set();

    for (const block of blocks) {
        const [x, y, z] = block.pos;
        const pname = palette[block.state].Name;

        const showTop   = !has(x, y + 1, z);
        const showEast  = !has(x + 1, y, z);
        const showSouth = !has(x, y, z + 1);
        if (!showTop && !showEast && !showSouth) continue;

        const img = atlas.get(pname);
        const f   = cubeFaceVertices(x, y, z);

        if (img && img.complete && img.naturalWidth > 0) {
            usedTextures.add(pname);

            // Texture source rect — large textures get cropped to a useful
            // 16x16 region (top-left for chest, top strip for lantern).
            let sx = 0, sy = 0, sw = 16, sh = 16;
            const w = img.naturalWidth, h = img.naturalHeight;
            if (w >= 16 && h >= 16) {
                if (h === 48) {                  // lantern: only the top frame
                    // top half (rows 0..16)
                    sy = 0; sh = 16;
                } else if (w === 64) {           // chest: top-left 16x16
                    sx = 0; sy = 0; sw = 16; sh = 16;
                }
            }

            if (showTop)   paintFace(ctx, img, sw, sh, f.top[0],   f.top[1],   f.top[3]);
            if (showEast)  paintFace(ctx, img, sw, sh, f.east[0],  f.east[1],  f.east[3]);
            if (showSouth) paintFace(ctx, img, sw, sh, f.south[0], f.south[1], f.south[3]);
        } else {
            // Color fallback: solid polygons.
            const color = fallback[pname] || '#888888';
            ctx.fillStyle = color;

            if (showTop)   fillFace(ctx, ...f.top);
            if (showEast)  fillFace(ctx, ...f.east);
            if (showSouth) fillFace(ctx, ...f.south);
        }
        ctx.setTransform(1, 0, 0, 1, ox, oy);  // reset for next block
    }

    return {
        usedTextures: usedTextures.size,
        width: widthPx,
        height: heightPx,
    };
}

// ------------------- bootstrap -------------------

async function loadImage(dataUri) {
    return new Promise((resolve, reject) => {
        const img = new Image();
        img.onload  = () => resolve(img);
        img.onerror = (e) => reject(e);
        img.src = dataUri;
    });
}

(async function() {
    const TEX_DATA    = __TEX_DATA__;
    const BUILD_DATA  = __BUILD_DATA__;
    const FALLBACK    = __FALLBACK__;
    const STATUS_EL   = document.getElementById('status');

    const setStatus = (msg, color) => {
        if (!STATUS_EL) return;
        STATUS_EL.textContent = msg;
        if (color && STATUS_EL.style) STATUS_EL.style.color = color;
    };

    try {
        // 1. Decode all textures into a Map<blockName, HTMLImageElement>.
        const atlas = new Map();
        const loadResults = await Promise.all(
            Object.entries(TEX_DATA).map(([name, uri]) =>
                loadImage(uri)
                    .then(img => ({ name, img, ok: true }))
                    .catch(err => ({ name, err, ok: false }))
            )
        );
        let failed = 0;
        for (const r of loadResults) {
            if (r.ok) atlas.set(r.name, r.img);
            else { failed++; console.error(`texture load failed for ${r.name}:`, r.err); }
        }
        setStatus(`loaded ${atlas.size}/${Object.keys(TEX_DATA).length} textures${failed ? ` (${failed} failed)` : ''}`);

        // 2. For each building: gunzip + parse + render.
        const usedStats = {};
        for (const [name, dims] of BUILD_DATA) {
            const canvas = document.getElementById(name);
            if (!canvas) continue;

            // Read embedded NBT base64 from a per-building script tag.
            const dataTag = document.getElementById(`nbt-${name}`);
            if (!dataTag) {
                console.error(`no <script id="nbt-${name}"> found`);
                continue;
            }
            const compressed = base64ToBytes(dataTag.textContent.trim());
            const decompressed = await gunzip(compressed.buffer);
            const root = parseNBT(decompressed);

            const structure = {
                name:    name,
                size:    [root.size[0], root.size[1], root.size[2]],
                palette: root.palette,
                blocks:  root.blocks,
            };

            const stats = renderBuilding(canvas, structure, atlas, FALLBACK);
            usedStats[name] = stats;
            console.log(`rendered ${name}: ${stats.usedTextures} textures used, ${stats.width}x${stats.height}`);
        }

        const total = Object.values(usedStats).reduce((a, b) => a + b.usedTextures, 0);
        setStatus(
            `rendered ${Object.keys(usedStats).length} buildings, ${total} texture refs across all`,
            '#7ab86b'
        );
    } catch (err) {
        console.error('renderer error:', err);
        setStatus(`renderer error: ${err && err.message ? err.message : err}`, '#d97a7a');
    }
})();
"""


def render_html(textures: dict[str, str], buildings_b64: dict[str, str]) -> str:
    """Build the final index-canvas.html string."""

    # JSON-encode the dictionaries with non-ASCII-friendly separators.
    tex_json = json.dumps(textures, separators=(",", ":"), ensure_ascii=False)
    bld_json = json.dumps(
        [(name, list(dims)) for name, dims in BUILDINGS],
        separators=(",", ":"),
    )
    fallback_json = json.dumps(COLOR_FALLBACK, separators=(",", ":"))

    # Substitute data into the JS template.
    js = (
        JS_SOURCE
        .replace("__TEX_DATA__", tex_json)
        .replace("__BUILD_DATA__", bld_json)
        .replace("__FALLBACK__", fallback_json)
    )

    # Build the per-building <script id="nbt-NAME"> tags (one per NBT).
    # We use <script type="text/plain"> with the base64 in textContent — the
    # browser won't parse it as JS, but our JS can read .textContent directly.
    nbt_scripts = []
    canvas_tags = []
    for name, dims in BUILDINGS:
        nbt_scripts.append(
            f'<script type="text/plain" id="nbt-{name}">{buildings_b64[name]}</script>'
        )
        # Canvas dimensions are filled in dynamically by the renderer; we set
        # a generous default and the renderer overwrites them. Width x Height
        # heuristic: based on structure bounds + 30 px margin + 32 px title.
        sx, sy, sz = dims
        # crude estimate, mostly cosmetic for first paint
        w = max(180, (sx + sz) * TILE_W_PRE + 60)
        h = max(180, (sx + sz) * TILE_H_PRE + sy * TILE_BLOCK_PRE + 60)
        canvas_tags.append(
            f'<canvas id="{name}" width="{w}" height="{h}"></canvas>'
        )

    nbt_blob = "\n  ".join(nbt_scripts)
    canvas_blob = "\n  ".join(canvas_tags)

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<title>Burg Demo Structure Pack — Real MC 1.21.1 Textures</title>
<style>
  :root {{ color-scheme: dark; }}
  body {{
    background: #0d0d0d;
    color: #ddd;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    margin: 0;
    padding: 24px;
  }}
  h1 {{ font-size: 24px; margin: 0 0 8px; }}
  .sub {{ color: #888; font-size: 13px; margin-bottom: 32px; max-width: 720px; line-height: 1.4; }}
  .grid {{
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
    gap: 24px;
  }}
  .card {{
    background: #161616;
    border: 1px solid #2a2a2a;
    border-radius: 8px;
    padding: 16px;
  }}
  .card .canvas-wrap {{
    background: #1a1a1a;
    border-radius: 4px;
    overflow: hidden;
    line-height: 0;
  }}
  .card canvas {{
    width: 100%;
    height: auto;
    display: block;
    background: #1a1a1a;
    image-rendering: pixelated;
  }}
  .card .meta {{
    color: #888;
    font-size: 12px;
    margin-top: 8px;
    font-family: ui-monospace, Menlo, Consolas, monospace;
  }}
  #status {{
    position: fixed;
    bottom: 12px;
    right: 16px;
    background: #161616;
    color: #7ab86b;
    border: 1px solid #2a2a2a;
    border-radius: 4px;
    padding: 6px 10px;
    font: 11px ui-monospace, Menlo, Consolas, monospace;
    z-index: 10;
  }}
</style>
</head>
<body>
<h1>Burg Demo Structure Pack — Real MC 1.21.1 Textures</h1>
<p class="sub">
  5 procedurally-generated buildings rendered isometrically on HTML5 canvas
  using actual Minecraft 1.21.1 textures (39 PNGs extracted from the game jar).
  Hidden-faces optimization; depth-sort by <code>(x+z, -y)</code>.
  Self-contained — open directly via <code>file://</code>, no network requests.
</p>
<div class="grid">
  {canvas_blob}
</div>
<div id="status">loading…</div>

<!-- Embedded gzipped NBTs (one <script type="text/plain"> per building) -->
{nbt_blob}

<!-- Renderer + bootstrap -->
<script>
{js}
</script>
</body>
</html>
"""


# Pre-computed defaults for canvas sized via JS at runtime; we still need
# them as static defaults in HTML. Using the same constants as the renderer.
TILE_W_PRE = 22
TILE_H_PRE = 11
TILE_BLOCK_PRE = 22


def main() -> int:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    textures = encode_textures()
    buildings_b64 = {name: encode_nbt(name) for name, _ in BUILDINGS}
    html = render_html(textures, buildings_b64)
    out_path = OUT_DIR / "index-canvas.html"
    out_path.write_text(html, encoding="utf-8")

    size_kb = out_path.stat().st_size / 1024
    print(f"Wrote {out_path} ({size_kb:.1f} KB)")
    print(f"  textures embedded: {len(textures)}")
    print(f"  buildings embedded: {len(buildings_b64)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
