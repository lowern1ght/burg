"""Build the demo pack: 5 NBTs + 5 SVG previews.

Run from repo root:
    python tools/build_demo_pack.py

Output:
    tools/structures/out/<name>.nbt
    tools/structures/out/<name>.svg
    tools/structures/out/index.html   (gallery page, also pushed to artifacts)
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))  # repo root for `tools.structures` import

from tools.structures.builder import StructureBuilder
from tools.structures.renderer import render_svg
from tools.structures.recipes import RECIPE_CATALOG
from tools.structures.validator import validate_structure


OUT_DIR = HERE / "structures" / "out"


def build_one(name: str, recipe_fn, defaults, params: dict) -> dict:
    """Build a structure, save NBT + SVG, validate. Return summary."""
    merged = {**defaults, **params}
    builder = recipe_fn(**merged)
    structure = builder.structure()

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    nbt_path = OUT_DIR / f"{name}.nbt"
    svg_path = OUT_DIR / f"{name}.svg"

    builder.save(nbt_path)
    svg_content = render_svg(
        builder,
        title=f"{name} ({merged['width']}x{merged['depth']}x{merged['height']})",
        show_legend=True,
    )
    svg_path.write_text(svg_content, encoding="utf-8")

    issues = validate_structure(nbt_path)
    return {
        "name": name,
        "nbt_path": str(nbt_path.relative_to(HERE.parent)),
        "svg_path": str(svg_path.relative_to(HERE.parent)),
        "block_count": structure.block_count,
        "size": structure.size,
        "issues": issues,
        "ok": len(issues) == 0,
    }


def build_index_html(summaries: list) -> str:
    """Render a gallery HTML page that embeds each SVG."""
    html = ['<!DOCTYPE html>', '<html lang="en"><head>',
            '<meta charset="utf-8"/>',
            '<title>Burg Demo Structure Pack</title>',
            '<style>',
            '  :root { color-scheme: dark; }',
            '  body { background:#0d0d0d; color:#ddd; font-family: -apple-system, sans-serif; margin: 0; padding: 24px; }',
            '  h1 { font-size: 24px; margin: 0 0 8px; }',
            '  .sub { color:#888; font-size: 13px; margin-bottom: 32px; }',
            '  .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 24px; }',
            '  .card { background:#161616; border:1px solid #2a2a2a; border-radius:8px; padding:16px; }',
            '  .card h2 { margin: 0 0 4px; font-size: 16px; color:#e6c66e; font-family: monospace; }',
            '  .card .meta { color:#888; font-size: 12px; margin-bottom: 12px; }',
            '  .card svg { width: 100%; height: auto; background:#1a1a1a; border-radius:4px; }',
            '  .card .status { font-size: 12px; margin-top: 8px; padding: 6px 8px; border-radius: 4px; }',
            '  .card .status.ok { color:#7ab86b; background:#1a2a1a; }',
            '  .card .status.fail { color:#d97a7a; background:#2a1a1a; }',
            '  .card .issues { font-size: 11px; color:#d9a87a; margin-top: 8px; font-family: monospace; }',
            '</style></head><body>',
            '<h1>Burg Demo Structure Pack</h1>',
            '<p class="sub">5 procedurally-generated buildings · isometric previews · NBT static analysis</p>',
            '<div class="grid">']
    for s in summaries:
        svg = (OUT_DIR / f"{s['name']}.svg").read_text(encoding="utf-8")
        status_class = "ok" if s["ok"] else "fail"
        status_text = f"OK · {s['block_count']} blocks · validated" if s["ok"] else f"FAILED · {len(s['issues'])} issues"
        issues_html = ""
        if not s["ok"]:
            issues_html = '<div class="issues">' + "<br>".join(s["issues"]) + "</div>"
        html.append(
            f'<div class="card">'
            f'<h2>{s["name"]}</h2>'
            f'<div class="meta">{s["size"][0]}×{s["size"][1]}×{s["size"][2]} · '
            f'<a href="{s["nbt_path"]}" style="color:#7ab8d9">.nbt</a> · '
            f'<a href="{s["svg_path"]}" style="color:#7ab8d9">.svg</a></div>'
            f'{svg}'
            f'<div class="status {status_class}">{status_text}</div>'
            f'{issues_html}'
            f'</div>'
        )
    html.append('</div></body></html>')
    return "\n".join(html)


def main():
    summaries = []
    for name, (fn, _label, defaults) in RECIPE_CATALOG.items():
        # Allow CLI override of params via "name:x=7,y=5,z=3"
        params = {}
        for arg in sys.argv[1:]:
            if ":" in arg and "=" in arg:
                _, kv = arg.split(":", 1)
                if "=" in kv:
                    k, v = kv.split("=", 1)
                    try:
                        params[k] = int(v)
                    except ValueError:
                        pass
        print(f"Building {name} (params={params})...")
        s = build_one(name, fn, defaults, params)
        summaries.append(s)
        status = "OK" if s["ok"] else f"FAIL ({len(s['issues'])} issues)"
        print(f"  -> {s['block_count']} blocks, size={s['size']}, {status}")

    # Save summary JSON
    (OUT_DIR / "summary.json").write_text(
        json.dumps(summaries, indent=2), encoding="utf-8"
    )
    # Render gallery
    (OUT_DIR / "index.html").write_text(build_index_html(summaries), encoding="utf-8")

    # Exit code
    failed = sum(1 for s in summaries if not s["ok"])
    print(f"\nDone. {len(summaries)} structures. {failed} failed validation.")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())