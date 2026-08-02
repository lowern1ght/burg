package org.dawnoftime.onceuponatown.behavior.path;

/**
 * The kind of road piece a {@link RoadSegment} resolves into.
 *
 * <p>Each kind maps to a separate NBT structure chosen by
 * {@link RoadLayerFromStructures}. The taxonomy is intentionally small:
 *
 * <ul>
 *   <li>{@link #STREET} — flat terrain, uses straight or turn pieces.</li>
 *   <li>{@link #BRIDGE} — spans water; the only piece that goes over an
 *       obstacle instead of through it.</li>
 *   <li>{@link #CULVERT} — goes under something (a road, a fence, a wall);
 *       rare for now, but the data exists so the layer can grow.</li>
 * </ul>
 *
 * <p>Adding a new kind is a two-step: extend the enum, then extend the
 * switch in {@link RoadLayerFromStructures#pieceFor}. The classifier
 * ({@link RoadPlanner#classifyFromPath}) only emits STREET and BRIDGE for
 * the 2D planning slice; CULVERT is reserved for a future phase that
 * models Y-aware routing.
 */
public enum RoadType {
    STREET,
    BRIDGE,
    CULVERT
}
