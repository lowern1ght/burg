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

    /** How close two idle people have to be to notice each other. */
    private static final double CHATTER_RANGE = 3.5;

    private final Npc npc;
    private ActivityInstance activity;
    private int performTicks = 0;
    private int idleTicks = 0;
    /** Where they were sent to sleep, so the walk is not restarted every tick. */
    private GoToPosition walkingHome;

    public WorkShift(Npc npc) {
        this.npc = npc;
    }

    public void tick() {
        if (!(npc.level() instanceof ServerLevel level)) return;
        BlockPos anchor = npc.getTownAnchorPos();
        if (anchor == null) return;
        Town town = LevelTowns.get(level).getTownAt(anchor).orElse(null);
        if (town == null) return;

        // THE DAY DECIDES FIRST. A trade is what somebody does between getting up and going to
        // bed, not a thing they do around the clock -- and the owner asked for this before any of
        // the rest of it: "если закат то идти спать".
        org.dawnoftime.onceuponatown.people.DayPhase phase =
            org.dawnoftime.onceuponatown.people.DayPhase.of(level.getDayTime());
        if (phase.isRestingTime()) {
            goHome(level, town, phase);
            return;
        }
        if (npc.isSleeping()) {
            // Dawn. Out of bed before anything else, or the walk to work starts from a lie-down.
            npc.stopSleeping();
            walkingHome = null;
        }
        // Whatever they were doing last night, they are on their feet now. Clearing it here rather
        // than in each branch means a pose can never outlive the reason for it -- which is how a
        // town ends up with somebody sitting down all day.
        npc.setNpcPose(org.dawnoftime.onceuponatown.entity.NpcPose.STANDING);
        if (!phase.isWorkingTime()) return;      // dawn: awake, not yet at work

        if (activity == null) {
            // Idling is the honest default: a settler with nowhere to work wanders, and the base
            // goals on Npc already do that. Looking for work every ten seconds rather than every
            // tick keeps a town of jobless people from scanning its own building list forever.
            chatter(level);
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
     * Two idle people who happen to be near each other turn and talk.
     *
     * <p>The cheapest liveliness available, and it costs one nearby-entity query on the idle path
     * only — the people with work to do never run it. A settlement where the jobless stand
     * motionless reads as a mod with the AI switched off; the same people facing each other and
     * gesturing reads as a village, and nothing else about them has to change.
     *
     * <p>Deliberately one-sided: each of them independently notices the other and turns, rather
     * than one of them "starting a conversation" that has to be tracked, agreed and ended. There is
     * no state to get out of sync because there is no state.
     */
    private void chatter(ServerLevel level) {
        Npc other = null;
        double best = Double.MAX_VALUE;
        for (Npc candidate : level.getEntitiesOfClass(Npc.class,
                npc.getBoundingBox().inflate(CHATTER_RANGE),
                n -> n != npc && n.getRole() == Npc.Role.SETTLER && !n.isSleeping())) {
            double d = candidate.distanceToSqr(npc);
            if (d < best) { best = d; other = candidate; }
        }
        if (other == null) {
            npc.setNpcPose(org.dawnoftime.onceuponatown.entity.NpcPose.STANDING);
            return;
        }
        npc.getLookControl().setLookAt(other, 30.0F, 30.0F);
        npc.setNpcPose(org.dawnoftime.onceuponatown.entity.NpcPose.TALKING);
    }

    /**
     * Stop work, walk to your own bed, and lie in it once it is dark.
     *
     * <p>The bed is a POSITION on the person's record, assigned by {@link
     * org.dawnoftime.onceuponatown.tick.Homes}, so this is a walk to somewhere real rather than to
     * a building somebody is notionally a resident of. Somebody with no bed simply stops working
     * and stays out — which is the visible consequence of a town that has not built enough houses,
     * and it should be visible.
     *
     * <p>The trade is released on the way, not kept overnight: a claim held by somebody asleep is
     * a workplace nobody else can take, and the assignment pass runs every ten seconds.
     */
    private void goHome(ServerLevel level, Town town,
                        org.dawnoftime.onceuponatown.people.DayPhase phase) {
        if (activity != null) release(town);

        org.dawnoftime.onceuponatown.people.Person person = npc.person();
        if (person == null || !person.hasHome()) {
            // Nowhere to sleep. They sit down where they are and doze, which is the honest picture
            // of a town that has not built enough houses -- and far better than a person left
            // standing bolt upright in the street until dawn, which reads as a broken schedule
            // rather than as a consequence.
            npc.getNavigation().stop();
            npc.setNpcPose(phase.isSleepingTime()
                ? org.dawnoftime.onceuponatown.entity.NpcPose.DOZING
                : org.dawnoftime.onceuponatown.entity.NpcPose.SITTING);
            return;
        }

        BlockPos bed = BlockPos.of(person.homeKey());
        if (!org.dawnoftime.onceuponatown.tick.Homes.stillABed(level, bed)) {
            // The bed has gone -- upgraded away, or broken. Give it up and let the next
            // assignment pass find another, rather than walking to a coordinate for ever.
            person.setHomeKey(0L);
            LevelTowns.get(level).markDirty();
            walkingHome = null;
            return;
        }

        if (npc.isSleeping()) return;

        if (npc.blockPosition().closerThan(bed, 2.0)) {
            if (phase.isSleepingTime()) {
                npc.getNavigation().stop();
                npc.startSleeping(bed);
            }
            return;
        }

        if (walkingHome == null) {
            walkingHome = new GoToPosition(
                npc, bed, BuilderConfigDataHandler.get().walkSpeed, 1.5);
        }
        if (walkingHome.tick()) walkingHome = null;   // arrived; next tick lies down
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
