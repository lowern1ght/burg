package org.lowern1ght.burg.town;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;

import java.util.List;

/**
 * The engine-side state of one in-progress construction: the building being
 * placed, where it is being placed, what rotation, what the engine already
 * paid for from the town's stock, and the matching queue entry (so a cancel
 * or completion can find its row).
 *
 * @param defId canonical {@code building_def_id} being placed
 * @param placementPos world position where the building is being assembled
 * @param rotation rotation applied to the building's footprint (one of the four cardinal rotations)
 * @param connectionPos position of the connection point the building is being grown from
 * @param connectionDir the direction the connection extends in (typically the side of {@code connectionPos} facing the new building)
 * @param connectionTarget the def id of the building {@code connectionPos} already belongs to (or null for the bridgehead piece)
 * @param entryConnectorPos the position of the entry-connector block the NPC builder is approaching
 * @param cost the items already paid out from the town's stock; defensive copy on construction
 * @param queueDefId the def id under which the queue entry was registered (mirrors {@code defId} for new builds; for upgrades it is the def id of the building being upgraded)
 * @param queueEntryId the queue entry's monotonic id; matches the entry the engine is consuming
 */
public record ActiveBuildState(
    String defId,
    BlockPos placementPos,
    Rotation rotation,
    BlockPos connectionPos,
    Direction connectionDir,
    String connectionTarget,
    BlockPos entryConnectorPos,
    List<ItemCost> cost,
    String queueDefId,
    long queueEntryId
) {}
