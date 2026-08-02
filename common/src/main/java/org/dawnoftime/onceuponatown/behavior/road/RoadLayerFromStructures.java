package org.dawnoftime.onceuponatown.behavior.road;

import net.minecraft.resources.ResourceLocation;

/**
 * Default {@link RoadLayer} that maps each {@link RoadType} to a single
 * canonical NBT structure under {@code data/onceuponatown/structure/streets/}.
 *
 * <p>The streets NBTs are authored by the corpus pipeline; for the planning
 * slice this layer just picks the canonical piece per kind. A future phase
 * may pick among variants (e.g. {@code street_step_1/2/3} by terrain
 * feature) — that's a layer swap, not a planner change.
 */
public final class RoadLayerFromStructures implements RoadLayer {

    private static final String NS = "onceuponatown";
    private static final String PREFIX = "streets/";

    @Override
    public ResourceLocation pieceFor(RoadSegment segment) {
        return switch (segment.type()) {
            case STREET  -> ResourceLocation.fromNamespaceAndPath(NS, PREFIX + "street_step");
            case BRIDGE  -> ResourceLocation.fromNamespaceAndPath(NS, PREFIX + "street_bridge_3");
            case CULVERT -> ResourceLocation.fromNamespaceAndPath(NS, PREFIX + "street_culvert");
        };
    }
}
