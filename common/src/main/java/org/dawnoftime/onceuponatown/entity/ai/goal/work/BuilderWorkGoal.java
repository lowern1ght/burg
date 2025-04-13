package org.dawnoftime.onceuponatown.entity.ai.goal.work;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.Utils;
import org.dawnoftime.onceuponatown.building.ConstructionProject;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.entity.ai.SimpleStateMachine;
import org.dawnoftime.onceuponatown.entity.ai.SimpleStateMachine.State;
import org.dawnoftime.onceuponatown.entity.ai.goal.NpcGoal;

import java.util.EnumSet;

public class BuilderWorkGoal extends NpcGoal {
    private ConstructionProject project;
    private int preparingNextStepCountdown;
    private int readingPlanCountdown;
    private int timeSinceLastSuccessfulStep;
    private boolean lastStepSuccessful;
    private boolean superBuilder = false;

    public BuilderWorkGoal(Npc builder) {
        super(builder);
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));

        State goToConstructionSite = new State(this::goToConstructionSite);
        State readPlan = new State(this::readPlan).onStart(this::startReadingPlan).onStop(this::stopReadingPlan);
        State prepareNextStep = new State(this::prepareNextStep).onStart(this::moveToNextStepPos);
        State executeStep = new State(this::executeStep);

        stateMachine = new SimpleStateMachine(goToConstructionSite, readPlan, prepareNextStep, executeStep)
                .setInitialState(goToConstructionSite)
                .addTransition(goToConstructionSite, readPlan, this::closeEnoughToConstructionSite)
                .addTransition(readPlan, goToConstructionSite, () -> !closeEnoughToConstructionSite())
                .addTransition(readPlan, prepareNextStep, () -> readingPlanCountdown <= 0)
                .addTransition(prepareNextStep, goToConstructionSite, () -> !closeEnoughToConstructionSite())
                .addTransition(prepareNextStep, executeStep, () -> (preparingNextStepCountdown <= 0) && (closeEnoughToExecuteStep() || (timeSinceLastSuccessfulStep >= 2000)))
                .addTransition(executeStep, goToConstructionSite, () -> !closeEnoughToConstructionSite())
                .addTransition(executeStep, readPlan, () -> lastStepSuccessful && npc.getRandom().nextInt(1000) < 4)
                .addTransition(executeStep, prepareNextStep, () -> lastStepSuccessful);
    }

    @Override
    public boolean canUse() {
        return project != null || getTownConstructionProject() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return project != null && !project.isCompleted();
    }

    private boolean closeEnoughToConstructionSite() { //Todo : check if npc is in construction site boundingbox + 10 blocks
        return npc.distanceToSqr(npcPos().getX(), npcPos().getY(), npcPos().getZ()) <= 20;
    }

    @Override
    public void start() {
        npc.setCrossingArms(false);
        npc.getNavigation().setMaxVisitedNodesMultiplier(10.0F);
        if (project == null) {
            project = getTownConstructionProject();
        }
        readingPlanCountdown = npc.getRandom().nextInt(adjustedTickDelay(20 * 2), adjustedTickDelay(20 * 6));
    }

    @Override
    public void stop() {
        npc.getNavigation().resetMaxVisitedNodesMultiplier();
        project = null;
        npc.freeHands();
        if (project.isCompleted()) {
            npc.playSound(SoundEvents.VILLAGER_CELEBRATE, 2.0F, 0.9F);
        }
    }

    public void reset() {
        stateMachine.reset();
        preparingNextStepCountdown = 0;
        readingPlanCountdown = 0;
        timeSinceLastSuccessfulStep = 0;
        lastStepSuccessful = false;
    }

    @Override
    public void tick() {
        if (project != null && !project.isCompleted()) {
            stateMachine.tick();
        }
    }

    private void goToConstructionSite() {
        npc.getNavigation().moveTo(project.getPosition().getX(), project.getPosition().getY(), project.getPosition().getZ(), npc.getAttributeValue(Attributes.MOVEMENT_SPEED));
    }

    private void prepareNextStep() {
        if (this.preparingNextStepCountdown <= 3) {
            ConstructionProject.NextAction nextAction = project.getNextStepType();
            switch (nextAction) {
                case PLACE_BLOCK -> {
                    npc.holdInMainHand(project.getNextStepState().getBlock().asItem().getDefaultInstance());
                }
                case DESTROY_BLOCK -> {

                }
            }
        }
        lookAtNextStepPos();
        moveToNextStepPos();
        --this.preparingNextStepCountdown;
    }

    protected Vec3 getRandomPosAwayFromStepPos() {
        return DefaultRandomPos.getPos(npc, 4, 3);
    }

    private void executeStep() {
        ++timeSinceLastSuccessfulStep;
        lookAtNextStepPos();
        if (npcPos().equals(project.getNextStepPos()) && npc.getNavigation().isDone()) {
            Vec3 vec3 = getRandomPosAwayFromStepPos();
            if (vec3 != null) {
                npc.moveTo(vec3.x, vec3.y, vec3.z);
            } else {
                npc.getJumpControl().jump();
            }
            return;
        }
        switch (project.getNextStepType()) {
            case PLACE_BLOCK -> {
                Block nextBlock = project.getNextStepState().getBlock();
                if (project.nextStep()) {
                    lastStepSuccessful = true;
                    timeSinceLastSuccessfulStep = 0;
                    preparingNextStepCountdown = npc.getRandom().nextInt(6 - 1, 6 + 1);
                    npc.swing(InteractionHand.MAIN_HAND);
                    if (!project.isCompleted() && project.getNextStepState().getBlock() != nextBlock) {
                        npc.freeMainHand();
                    }
                    return;
                }
            }
            case DESTROY_BLOCK -> {
                if (project.nextStep()) {
                    lastStepSuccessful = true;
                    timeSinceLastSuccessfulStep = 0;
                    preparingNextStepCountdown = npc.getRandom().nextInt(6 - 1, 6 + 1);
                    npc.swing(InteractionHand.MAIN_HAND);
                    return;
                }
            }
        }
    }

    private void executePlaceBlockStep() {

    }

    private void executeDestroyBlockStep() {

    }

    private void executePlaceEntityStep() {

    }

    private void executeRemoveEntityStep() {

    }

    private void readPlan() {
        --this.readingPlanCountdown;
    }



    private void lookAtNextStepPos() {
        Vec3 toLookAt = project.getNextStepPos().getCenter();
        npc.getLookControl().setLookAt(toLookAt.x, toLookAt.y, toLookAt.z);
    }

    private void moveToNextStepPos() {
        BlockPos nextStepPos = project.getNextStepPos();
        if (npc.distanceToSqr(nextStepPos.getX(), nextStepPos.getY(), nextStepPos.getZ()) > 8) {
            if (npc.getNavigation().isDone()) {
                npc.getNavigation().moveTo(nextStepPos.getX(), nextStepPos.getY(), nextStepPos.getZ(), speedModifier);
            }
        } else {
            npc.getNavigation().stop();
        }
    }

    private boolean closeEnoughToExecuteStep() {
        return superBuilder || npc.distanceToSqr(project.getNextStepPos().getCenter()) <= 5 * 5;
    }

    private void startReadingPlan() {
        readingPlanCountdown = npc.getRandom().nextInt(adjustedTickDelay(20 * 2), adjustedTickDelay(20 * 6));
        npc.getNavigation().stop();
        npc.holdInMainHand(new ItemStack(Items.FILLED_MAP));
        npc.freeOffHand();
        npc.playSound(SoundEvents.BOOK_PAGE_TURN);
        npc.setReading(true);
    }

    private void stopReadingPlan() {
        npc.freeMainHand();
        npc.playSound(SoundEvents.BOOK_PAGE_TURN);
        npc.setReading(false);
    }

    private ConstructionProject getTownConstructionProject() {
        if (!npc.level().isClientSide() && npc.level() instanceof ServerLevel serverLevel) {
            return Utils.getNearestTown(serverLevel, npc.blockPosition(), 100).getPendingProject();
        }
        return null;
    }

    public void assignConstructionProject(ConstructionProject project) {
        this.project = project;
    }

    private void showDebugInfo() {
        /*
        Player player = builder.getLevel().getNearestPlayer(builder, 30D);
        if (player != null) {
            player.sendSystemMessage(
            Component.literal("Done : " + project.isCompleted()).withStyle(ChatFormatting.GOLD).append(
            Component.literal(" | Block : " + project.getProgression() + "/" + project.getBlocksQuantity()).withStyle(ChatFormatting.BLUE).append(
            Component.literal(" | Next : " + BuiltInRegistries.BLOCK.getKey(project.nextBlock()) + ", " +
            project.nextBlockPos().getX() + " " +
            project.nextBlockPos().getY() + " " +
            project.nextBlockPos().getZ()).withStyle(ChatFormatting.AQUA))));

            player.displayClientMessage(
            Component.literal(stateMachine.getCurrentState().getName() + " | ").withStyle(ChatFormatting.DARK_GREEN).append(
            Component.literal("Waiting : " + placeAttemptCooldown).withStyle(ChatFormatting.GOLD).append(
            Component.literal(" | Put Away : " + stopCheckingPlanCooldown).withStyle(ChatFormatting.BLUE).append(
            Component.literal(" | Next Check : " + nextPlanCheckCooldown).withStyle(ChatFormatting.AQUA)))),true);
        }

         */
    }
}