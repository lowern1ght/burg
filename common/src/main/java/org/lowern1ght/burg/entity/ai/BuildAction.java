package org.lowern1ght.burg.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.lowern1ght.burg.building.schematic.SchematicBlock;
import org.lowern1ght.burg.entity.Npc;

import java.util.List;

public interface BuildAction {
    // World position the NPC walks toward during the MOVING phase.
    BlockPos getTargetPos();
    // World-space origin: add localPos from SchematicBlock to get world coordinates.
    BlockPos getOrigin();

    // True for terrain-matched (road/pond) buildings placed in a single instant call.
    boolean isInstant();
    // For instant actions: performs the full placement. Returns true on success.
    boolean executeInstant(ServerLevel level, Npc npc);

    // For block-by-block actions: returns the ordered block list to place.
    // Called once on the first BUILDING tick.
    List<SchematicBlock> prepareBlocks(ServerLevel level, Npc npc);

    // Called when the NPC arrives at the target (MOVING -> BUILDING transition).
    // Use to start pre-build animations (e.g. reading the plan).
    default void onArrived(Npc npc) {}

    // Called after all blocks are placed (or after executeInstant for instant builds).
    void onComplete(ServerLevel level, Npc npc);

    boolean isFailed();

    // Writes action-specific save data. BuildGoal adds build_progress on top.
    // Write nothing for actions that do not support mid-progress persistence.
    void saveTo(CompoundTag tag);
}
