package org.dawnoftime.onceuponatown.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.datapack.BuilderConfigDataHandler;
import org.dawnoftime.onceuponatown.entity.Npc;

public class GoToPosition {

    private final Npc npc;
    private BlockPos target;
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
    }

    // Redirect navigation to a new block target. Resets the nav refresh counter.
    public void updateTarget(BlockPos newTarget) {
        this.target = newTarget;
        this.ticksSinceNavRefresh = 0;
        npc.getNavigation().moveTo(newTarget.getX() + 0.5, newTarget.getY(), newTarget.getZ() + 0.5, speed);
    }

    // Returns true when the NPC has reached the target.
    public boolean tick() {
        totalTicks++;
        double distSq = npc.distanceToSqr(Vec3.atCenterOf(target));
        if (distSq <= arrivalRadius * arrivalRadius) {
            npc.getNavigation().stop();
            return true;
        }
        // Re-issue moveTo every second so stroll goals cannot permanently override the build target.
        if (++ticksSinceNavRefresh >= BuilderConfigDataHandler.get().pathRefreshIntervalTicks) {
            ticksSinceNavRefresh = 0;
            npc.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
        }
        return false;
    }
}
