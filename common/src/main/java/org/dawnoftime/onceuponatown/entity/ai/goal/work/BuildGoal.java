package org.dawnoftime.onceuponatown.entity.ai.goal.work;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.building.BuildProject;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.entity.ai.SimpleStateMachine;
import org.dawnoftime.onceuponatown.entity.ai.SimpleStateMachine.State;
import org.dawnoftime.onceuponatown.entity.ai.goal.NpcGoal;
import org.dawnoftime.onceuponatown.registry.ItemRegistry;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.EnumSet;

public class BuildGoal extends NpcGoal {
    private static final boolean SUPER_BUILDER = false;
    private static final double REACH_DIST = 5.0D;
    private static final int BUILD_SPEED = 6; // Ticks in between each step
    private BuildProject project;
    private int prepareStepCountdown;
    private int readPlanCountdown;
    private int ticksSinceLastSuccessfulStep;
    private boolean lastStepSuccessful;

    public BuildGoal(Npc builder) {
        super(builder);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));

        State goToSite = new State("go to site").onTick(this::goToSite).onStop(this::arriveAtSite);
        State readPlan = new State("read plan").onTick(this::readPlan).onStart(this::startReadPlan).onStop(this::stopReadPlan);
        State prepareStep = new State("prepare step").onTick(this::prepareStep).onStart(this::startPrepareStep);
        State doStep = new State("do step").onTick(this::doStep);

        stateMachine = new SimpleStateMachine(goToSite, readPlan, prepareStep, doStep)
            .addTransition(goToSite, readPlan, this::closeEnoughToSite)
            .addTransition(readPlan, goToSite, () -> !closeEnoughToSite())
            .addTransition(readPlan, prepareStep, () -> readPlanCountdown <= 0)
            .addTransition(prepareStep, goToSite, () -> !closeEnoughToSite())
            .addTransition(prepareStep, doStep, () -> (prepareStepCountdown <= 0) && (canReachStep() || (ticksSinceLastSuccessfulStep >= 20 * 3)))
            .addTransition(doStep, goToSite, () -> !closeEnoughToSite())
            .addTransition(doStep, readPlan, () -> lastStepSuccessful && npc.getRandom().nextInt(1000) < 4)
            .addTransition(doStep, prepareStep, () -> lastStepSuccessful);
    }

    @Override
    public boolean canUse() {
        return (project != null && project.isAvailable()) || tryGetPendingProject();
    }

    @Override
    public boolean canContinueToUse() {
        return project.isAvailable();
    }

    @Override
    public void start() {
        npc.getNavigation().setMaxVisitedNodesMultiplier(10.0F);
        npc.setCrossingArms(false);
        npc.freeHands();
    }

    @Override
    public void stop() {
        npc.getNavigation().resetMaxVisitedNodesMultiplier();
        npc.freeHands();
        npc.setCrossingArms(true);
        npc.stopBreakingBlock();
        if (project.isCompleted()) {
            npc.playSound(SoundEvents.VILLAGER_CELEBRATE, 2.0F, 0.9F);
        }
    }

    @Override
    public void tick() {
        stateMachine.tick();
        //showDebugInfo();
    }

    private boolean closeEnoughToSite() {
        return project.getBoundingBox().inflatedBy(5).isInside(npc.blockPosition());
    }

    private void goToSite() {
        npc.getNavigation().moveTo(project.getOriginPos().getX(), project.getOriginPos().getY(), project.getOriginPos().getZ(), speedModifier);
    }

    private void arriveAtSite() {

    }

    private boolean canReachStep() {
        BlockPos nextStepPos = project.getNextStepPos();
        Vec3 blockCenter = nextStepPos.getCenter();

        Vec3 normalized = npc.getViewVector(0.0F).normalize();
        boolean isLookingAt;
        if (Math.abs(normalized.y) > 0.5) {
            isLookingAt = true;
        } else {
            Vec3 dirVector = new Vec3(blockCenter.x() - npc.getX(), blockCenter.y() - npc.getEyeY(), blockCenter.z() - npc.getZ());
            double distToBlock = dirVector.length();
            dirVector = dirVector.normalize();
            //dirVector = new Vec3(dirVector.x, normalized.y, dirVector.z);
            double scalarProduct = normalized.dot(dirVector);
            double threshold = 1.0 - 0.025 / distToBlock;
            isLookingAt = scalarProduct > threshold;
        }

        if (nextStepPos != null) {
            return SUPER_BUILDER
                || (isLookingAt
                && Math.sqrt(npc.distanceToSqr(nextStepPos.getX(), nextStepPos.getY(), nextStepPos.getZ())) <= REACH_DIST);
        }
        return false;
    }

    private void lookAtCenter(BlockPos pos) {
        if (pos != null) {
            Vec3 toLookAt = pos.getCenter();
            npc.getLookControl().setLookAt(toLookAt.x, toLookAt.y, toLookAt.z);
        }
    }

    private void startPrepareStep() {
        int attempt = 0;
        while (attempt < 100 &&
            project.getNextAction() == BuildProject.Action.NOTHING ||
            project.getNextAction() == BuildProject.Action.SPAWN_ENTITY
        ) {
            ++attempt;
            project.nextStep();
        }
    }

    private void prepareStep() {
        ++ticksSinceLastSuccessfulStep;
        BlockPos nextStepPos = project.getNextStepPos();
        if (nextStepPos != null) {
            lookAtCenter(nextStepPos);
            double distToStep = Math.sqrt(npc.distanceToSqr(nextStepPos.getX(), nextStepPos.getY(), nextStepPos.getZ()));
            if (distToStep > REACH_DIST) {
                npc.getNavigation().moveTo(nextStepPos.getX(), nextStepPos.getY(), nextStepPos.getZ(), speedModifier);
            } else {
                npc.getNavigation().stop();
            }
            if (distToStep <= (2 * REACH_DIST) && prepareStepCountdown <= 3) {
                var nextAction = project.getNextAction();
                switch (nextAction) {
                    case PLACE_BLOCK -> {
                        BlockState nextStepState = project.getNextStepState();
                        if (nextStepState != null) {
                            npc.holdInMainHand(nextStepState.getBlock().asItem().getDefaultInstance());
                        }
                    }
                    case DESTROY_BLOCK -> {
                        BlockState nextStepState = project.getNextStepState();
                        if (nextStepState != null) {
                            /*
                            ItemStack stack = ItemStack.EMPTY;
                            if (nextStepState.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
                                stack = Items.WOODEN_SHOVEL.getDefaultInstance();
                            } else if (nextStepState.is(BlockTags.MINEABLE_WITH_AXE)) {
                                stack = Items.WOODEN_AXE.getDefaultInstance();
                            } else if (nextStepState.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
                                stack = Items.WOODEN_PICKAXE.getDefaultInstance();
                            } else if (nextStepState.is(BlockTags.MINEABLE_WITH_HOE)) {
                                stack = Items.WOODEN_HOE.getDefaultInstance();
                            }

                             */
                            npc.holdInOffHand(Items.WOODEN_SHOVEL.getDefaultInstance());
                        }
                    }
                }
            }
        }
        --prepareStepCountdown;
    }

    private void doStep() {
        ++ticksSinceLastSuccessfulStep;
        lastStepSuccessful = false;
        BlockPos nextStepPos = project.getNextStepPos();
        if (nextStepPos != null) {
            lookAtCenter(nextStepPos);
            var entities = npc.level().getEntitiesOfClass(LivingEntity.class, new AABB(nextStepPos));
            if (entities.contains(npc)) {
                Vec3 vec3 = DefaultRandomPos.getPosAway(npc, 3, 3, nextStepPos.getCenter());
                if (vec3 != null) {
                    npc.getNavigation().moveTo(vec3.x, vec3.y, vec3.z, speedModifier);
                }
            } else {
                var nextAction = project.getNextAction();
                if (nextAction != BuildProject.Action.DESTROY_BLOCK) {
                    npc.stopBreakingBlock();
                }
                switch (nextAction) {
                    case PLACE_BLOCK -> {
                        if (project.nextStep()) {
                            npc.swing(InteractionHand.MAIN_HAND);
                            lastStepSuccessful = true;
                            ticksSinceLastSuccessfulStep = 0;
                            prepareStepCountdown = BUILD_SPEED; //npc.getRandom().nextInt(BUILD_SPEED - 1, BUILD_SPEED + 1);
                        }
                    }
                    case DESTROY_BLOCK -> {
                        if (npc.getAttackedBlock() == null || !npc.getAttackedBlock().equals(nextStepPos)) {
                            npc.startBreakingBlock(nextStepPos);
                        } else {
                            if (npc.tickBreakBlock(InteractionHand.OFF_HAND)) {
                                if (project.nextStep()) {
                                    lastStepSuccessful = true;
                                    ticksSinceLastSuccessfulStep = 0;
                                    prepareStepCountdown = BUILD_SPEED; //npc.getRandom().nextInt(BUILD_SPEED - 1, BUILD_SPEED + 1);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void startReadPlan() {
        readPlanCountdown = npc.getRandom().nextInt(adjustedTickDelay(20 * 2), adjustedTickDelay(20 * 6));
        npc.getNavigation().stop();
        npc.holdInMainHand(ItemRegistry.REGISTRY.TOWN_SCROLL.get().getDefaultInstance());
        npc.freeOffHand();
        npc.playSound(SoundEvents.BOOK_PAGE_TURN);
        npc.setReading(true);
    }

    private void readPlan() {
        --readPlanCountdown;
    }

    private void stopReadPlan() {
        npc.freeMainHand();
        npc.holdInOffHand(Items.WOODEN_SHOVEL.getDefaultInstance());
        npc.playSound(SoundEvents.BOOK_PAGE_TURN);
        npc.setReading(false);
    }

    private boolean tryGetPendingProject() {
        Town town = npc.getTown();
        if (town != null) {
            project = town.getPendingProject();
        }
        return project != null;
    }

    public void reset() {
        stateMachine.reset();
        prepareStepCountdown = 0;
        readPlanCountdown = 0;
        ticksSinceLastSuccessfulStep = 0;
        lastStepSuccessful = false;
    }

    private void showDebugInfo() {
        var nearbyPlayers = npc.level().getEntitiesOfClass(Player.class, AABB.ofSize(npc.position(), 50.0D, 50.0D, 50.0D));
        for (Player player : nearbyPlayers) {
            player.displayClientMessage(
                Component.literal(stateMachine.getCurrentState().getName() + " | ").withStyle(ChatFormatting.DARK_GREEN).append(
                    Component.literal("Waiting : " + prepareStepCountdown).withStyle(ChatFormatting.GOLD).append(
                        Component.literal(" | Stop read : " + readPlanCountdown).withStyle(ChatFormatting.BLUE).append(
                            Component.literal(" | Time since succ : " + ticksSinceLastSuccessfulStep).withStyle(ChatFormatting.AQUA)))), true);
        }
    }
}