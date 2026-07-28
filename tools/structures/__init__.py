"""tools/structures — Burg structure authoring tools.

The style pipeline (run `python -m structures.stylekit --help` from tools/):

    nbtio       Robust structure-NBT I/O. Voxels = position -> BlockState.
    anatomy     Zone detection: ground / floor / walls / roof, shell box,
                roof profile, wall columns, interior and exterior parts.
    corpus      Measure the author's 127 NBTs. Source of every threshold.
    critic      Gate a candidate against the measured corpus.
    assemble    Derive variants by reusing the author's own voxels.
    render_png  PNG isometric + orthographic views — so an agent can look.
    appearance  Per-block colour and shape for the renderer.
    stylekit    CLI tying the above into generate -> gate -> look.

Older, still used by the demo pack scripts:

    registry    Block palette + known Minecraft renames (1.20 -> 1.21).
    builder     Parametric StructureBuilder (write NBT structures).
    validator   Static NBT validator (block IDs, palette).
    renderer    Isometric SVG renderer. Prefer render_png — an agent cannot
                read SVG, which is why style fixes used to be guesswork.
"""

__version__ = "0.2.0"
