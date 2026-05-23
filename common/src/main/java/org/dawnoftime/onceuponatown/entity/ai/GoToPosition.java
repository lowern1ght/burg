package org.dawnoftime.onceuponatown.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.entity.Npc;

public class GoToPosition {
    private static final int NAV_REFRESH_INTERVAL = 20;
    // Log navigation state every N ticks to detect stuck NPCs.
    private static final int NAV_DEBUG_INTERVAL = 100;

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(GoToPosition.class);

    private final Npc npc;
    private final BlockPos target;
    private final double speed;
    private final double arrivalRadius;
    private int ticksSinceNavRefresh = 0;
    private int totalTicks = 0;

    public GoToPosition(Npc npc, BlockPos target, double speed, double arrivalRadius) {
        this.npc = npc;
        this.target = target;
        this.speed = speed;
        this.arrivalRadius = arrivalRadius;
        npc.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
        LOGGER.debug("[OUAT-NAV] NPC {} started navigation to target={} dist={}",
            npc.getUUID(), target, String.format("%.1f", Math.sqrt(npc.distanceToSqr(Vec3.atCenterOf(target)))));
    }

    // Returns true when the NPC has reached the target.
    public boolean tick() {
        totalTicks++;
        double distSq = npc.distanceToSqr(Vec3.atCenterOf(target));
        if (distSq <= arrivalRadius * arrivalRadius) {
            npc.getNavigation().stop();
            LOGGER.debug("[OUAT-NAV] NPC {} ARRIVED at target={} after {}t", npc.getUUID(), target, totalTicks);
            return true;
        }
        // Re-issue moveTo every second so stroll goals cannot permanently override the build target.
        if (++ticksSinceNavRefresh >= NAV_REFRESH_INTERVAL) {
            ticksSinceNavRefresh = 0;
            npc.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
        }
        // Periodic stuck-detection log: distance, path status, total elapsed ticks.
        if (totalTicks % NAV_DEBUG_INTERVAL == 0) {
            boolean pathInProgress = npc.getNavigation().isInProgress();
            LOGGER.warn("[OUAT-NAV] NPC {} still MOVING toward target={} -- dist={} pathInProgress={} elapsedTicks={}",
                npc.getUUID(), target,
                String.format("%.1f", Math.sqrt(distSq)),
                pathInProgress,
                totalTicks);
        }
        return false;
    }
}
