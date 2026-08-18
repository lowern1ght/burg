package org.lowern1ght.burg.tick;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.lowern1ght.burg.network.NetworkHelper;
import org.lowern1ght.burg.town.LevelTowns;
import org.lowern1ght.burg.town.Town;

public class EraManager {

    // Called each server tick. Currently a no-op: era advancement is player-triggered via C2SAdvanceEraPacket.
    // Reserved for future automated era-eligibility checks or notifications.
    public static void tick(Town town, ServerLevel level, long gameTime, long anchorKey) {
        // no automatic checks yet
    }

    // Processes a player-requested era transition and sends targeted updates to all watchers.
    // Returns true if the transition succeeded.
    public static boolean advance(Town town, String pathId, ServerLevel level, BlockPos anchorPos) {
        boolean advanced = town.advanceEra(pathId);
        if (!advanced) return false;
        LevelTowns.get(level).markDirty();
        NetworkHelper.pushEraUpdateToWatchers(level, town, anchorPos);
        NetworkHelper.pushBuildingListToWatchers(level, town, anchorPos);
        NetworkHelper.pushStockToWatchers(level, town, anchorPos);
        return true;
    }
}
