package org.lowern1ght.burg.behavior.road;

import net.minecraft.resources.ResourceLocation;

/**
 * Resolves a {@link RoadSegment} to the NBT structure id that should be
 * placed for it.
 *
 * <p>Decoupled from the planner so the planner does not need to know which
 * structure ids exist. The default implementation is
 * {@link RoadLayerFromStructures}, which maps by {@link RoadType}.
 * Modded worlds or future phases can swap in a different layer that picks
 * variants by slope, era, or town orientation.
 */
public interface RoadLayer {

    /** The NBT structure id to use for this segment. */
    ResourceLocation pieceFor(RoadSegment segment);
}
