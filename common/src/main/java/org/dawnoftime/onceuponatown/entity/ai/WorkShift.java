package org.dawnoftime.onceuponatown.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.dawnoftime.onceuponatown.datapack.BuilderConfigDataHandler;
import org.dawnoftime.onceuponatown.datapack.SettlerJobsDataHandler;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.PlacedBuilding;
import org.dawnoftime.onceuponatown.town.Town;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A settler's working day: claim a trade, walk to it, stand at the bench, and get better at it.
 *
 * <p>Deliberately a second, smaller runner rather than a reuse of {@link SimpleStateMachine}. The
 * two share the phase shape — travel, approach the block, perform — and they share {@link
 * ActivityInstance} and {@link GoToPosition}, which is where the shape actually lives. What they
 * do not share is policy: for the builder an activity is <b>filler</b> between builds and is
 * interrupted the moment unclaimed work appears in the queue, while for a settler the trade IS the
 * day. Folding both policies into one class would put the queue-scanning brain inside every
 * villager in town.
 *
 * <p><b>The duplication of the ~80 lines of phase ticking is intentional and temporary.</b> The
 * repo's own rule is that two copies of one rule drift and the drifted one is the copy nobody
 * watches, so this is named rather than left to be discovered: `SimpleStateMachine`'s copy is the
 * one to retire, and it is not being retired here because the builder is the only part of the town
 * that currently works end to end, and rewriting it while the rest of the cast is being replaced
 * would leave nothing standing to compare against. Move it when a settler has been seen working
 * in game.
 */
public class WorkShift {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkShift.class);

    /** How often an unemployed settler looks for work. Rare on purpose; idling is a state, not a bug. */
    private static final int JOB_HUNT_EVERY = 200;

    private final Npc npc;
    private ActivityInstance activity;
    private int performTicks = 0;
    private int idleTicks = 0;

    public WorkShift(Npc npc) {
        this.npc = npc;
    }

    public void tick() {
        if (!(npc.level() instanceof ServerLevel level)) return;
        BlockPos anchor = npc.getTownAnchorPos();
        if (anchor == null) return;
        Town town = LevelTowns.get(level).getTownAt(anchor).orElse(null);
        if (town == null) return;

        if (activity == null) {
            // Idling is the honest default: a settler with nowhere to work wanders, and the base
            // goals on Npc already do that. Looking for work every ten seconds rather than every
            // tick keeps a town of jobless people from scanning its own building list forever.
            if (++idleTicks < JOB_HUNT_EVERY) return;
            idleTicks = 0;
            claim(level, town);
            return;
        }

        // The building can be gone: destroyed, or the def removed from the datapack. Let the job go
        // rather than walking to a coordinate forever.
        if (!town.getBuildings().contains(activity.targetBuilding)) {
            release(town);
            return;
        }

        switch (activity.phase) {
            case TRAVELING -> tickTraveling(level);
            case APPROACHING -> tickApproaching();
            case PERFORMING -> tickPerforming(level, town);
        }
    }

    /**
     * Take a trade at a building that has one and that nobody has taken.
     *
     * <p>Claims are held on the {@link Town} and persisted, so a settler keeps the same trade
     * across a reload. Deriving "is this taken" by scanning for other settlers instead would fail
     * exactly when it matters — while their chunks are unloaded, which is most of the time.
     */
    private void claim(ServerLevel level, Town town) {
        List<PlacedBuilding> candidates = new ArrayList<>();
        for (PlacedBuilding b : town.getBuildings()) {
            if (town.getJobHolder(b.worldPos) != null) continue;
            if (SettlerJobsDataHandler.jobsAt(b.getDefId()).isEmpty()) continue;
            candidates.add(b);
        }
        if (candidates.isEmpty()) return;

        // Nearest first: a person takes the work at hand, not the work across the valley.
        candidates.sort(java.util.Comparator.comparingDouble(
            b -> b.worldPos.distSqr(npc.blockPosition())));
        PlacedBuilding chosen = candidates.get(0);
        List<ActivityDef> jobs = SettlerJobsDataHandler.jobsAt(chosen.getDefId());
        ActivityDef def = jobs.get(npc.getRandom().nextInt(jobs.size()));

        if (!town.claimJob(chosen.worldPos, npc.getUUID())) return;
        npc.setJobSite(chosen.worldPos);
        activity = new ActivityInstance(def, chosen, ActivityInstance.Phase.TRAVELING,
            new GoToPosition(npc, chosen.worldPos, BuilderConfigDataHandler.get().walkSpeed, 6.0));
        LevelTowns.get(level).markDirty();
        LOGGER.debug("[OUAT-WORK] settler took '{}' at {}", chosen.getDefId(), chosen.worldPos);
    }

    private void release(Town town) {
        town.releaseJob(npc.getUUID());
        npc.setJobSite(null);
        npc.freeHands();
        npc.getNavigation().stop();
        activity = null;
        performTicks = 0;
    }

    private void tickTraveling(ServerLevel level) {
        if (!activity.goToPosition.tick()) return;

        String targetBlockId = activity.def.targetBlock();
        if (targetBlockId == null) {
            enterPerforming();
            return;
        }
        Block block = BuiltInRegistries.BLOCK
            .getOptional(ResourceLocation.parse(targetBlockId)).orElse(null);
        if (block == null) {
            LOGGER.warn("[OUAT-WORK] job for '{}' names target_block '{}', which is not a block",
                activity.targetBuilding.getDefId(), targetBlockId);
            enterPerforming();
            return;
        }

        // Scanned inside THIS building's box only. That is what lets a job name a common id: the
        // lumberjack's fifteen lying oak logs are his timber stack, and the same id in a house wall
        // three streets away is not a workplace.
        BoundingBox bb = activity.targetBuilding.bb;
        BlockPos found = null;
        if (bb != null) {
            double best = Double.MAX_VALUE;
            for (BlockPos p : BlockPos.betweenClosed(
                    new BlockPos(bb.minX(), bb.minY(), bb.minZ()),
                    new BlockPos(bb.maxX(), bb.maxY(), bb.maxZ()))) {
                if (!level.getBlockState(p).is(block)) continue;
                double d = p.distSqr(npc.blockPosition());
                if (d < best) { best = d; found = p.immutable(); }
            }
        }
        if (found == null) {
            // The bench is not there. Stand and work anyway rather than freeze: the building is
            // still the workplace, and refusing the trade over a missing block would leave a
            // whole trade permanently unfillable for a reason invisible from inside the game.
            enterPerforming();
            return;
        }
        activity.approachTargetPos = found;
        activity.approachGoTo = new GoToPosition(
            npc, found, BuilderConfigDataHandler.get().walkSpeed, 1.5);
        activity.phase = ActivityInstance.Phase.APPROACHING;
    }

    private void tickApproaching() {
        if (activity.approachGoTo == null) { enterPerforming(); return; }
        if (activity.approachGoTo.tick()) enterPerforming();
    }

    private void enterPerforming() {
        activity.phase = ActivityInstance.Phase.PERFORMING;
        performTicks = 0;
        String held = activity.def.heldItem();
        if (held != null && !held.equals("minecraft:air")) {
            BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(held))
                .ifPresent(item -> npc.holdInMainHand(new ItemStack(item)));
        }
    }

    private void tickPerforming(ServerLevel level, Town town) {
        npc.getNavigation().stop();
        BlockPos look = activity.approachTargetPos != null
            ? activity.approachTargetPos : activity.targetBuilding.worldPos;
        npc.getLookControl().setLookAt(
            look.getX() + 0.5, look.getY() + 0.5, look.getZ() + 0.5, 10f, 10f);

        // The swing is the same 25-tick beat the builder uses, so a working settler and a working
        // builder read as the same act.
        if (performTicks % 25 == 0 && activity.def.animationType() != AnimationType.CRAFT) {
            npc.swing(InteractionHand.MAIN_HAND);
        }
        performTicks++;

        SettlerJobsDataHandler.Config cfg = SettlerJobsDataHandler.get();
        if (performTicks < cfg.workTicks()) return;

        // A shift is done. This is the moment the work stops being decoration: the building is
        // stamped with the time and with the skill of whoever worked it, and ProductionManager
        // reads that stamp. Nothing here touches the stock directly -- production stays in one
        // place, and this only tells it that somebody turned up.
        performTicks = 0;
        npc.setSkill(Math.min(cfg.maxSkill(), npc.getSkill() + cfg.skillPerShift()));
        activity.targetBuilding.recordWork(level.getGameTime(), npc.getSkill());
        LevelTowns.get(level).markDirty();
        LOGGER.debug("[OUAT-WORK] shift done at '{}', worker skill now {}",
            activity.targetBuilding.getDefId(), npc.getSkill());
    }

    /** Called when the settler is removed, so its trade does not stay claimed by a dead man. */
    public void onRemoved(Town town) {
        if (town != null) town.releaseJob(npc.getUUID());
    }
}
