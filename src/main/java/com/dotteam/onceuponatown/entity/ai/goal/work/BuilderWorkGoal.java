package com.dotteam.onceuponatown.entity.ai.goal.work;

import com.dotteam.onceuponatown.construction.ConstructionProject;
import com.dotteam.onceuponatown.entity.Npc;
import com.dotteam.onceuponatown.entity.ai.SimpleStateMachine;
import com.dotteam.onceuponatown.entity.ai.goal.NpcGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

import static com.dotteam.onceuponatown.entity.ai.SimpleStateMachine.State;

public class BuilderWorkGoal extends NpcGoal {
    private ConstructionProject project;
    private int preparingNextStepCountdown;
    private int readingPlanCountdown;
    private int timeSinceLastSuccessfulStep;
    private boolean lastStepSuccessful;
    private int blockBreakTime;
    private int lastBreakProgress = -1;

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

    public boolean canUse() {
        return project != null || getTownConstructionProject() != null;
    }

    public boolean canContinueToUse() {
        return project != null && !project.isCompleted();
    }

    private boolean closeEnoughToConstructionSite() { //Todo : check if npc is in construction site boundingbox + 10 blocks
        return npc.distanceToSqr(npcPos().getX(), npcPos().getY(), npcPos().getZ()) <= 20;
    }

    public void start() {
        npc.getNavigation().setMaxVisitedNodesMultiplier(10.0F);
        if (project == null) {
            project = getTownConstructionProject();
        }
        readingPlanCountdown =  npc.getRandom().nextInt(adjustedTickDelay(20 * 2), adjustedTickDelay(20 * 6));
    }

    public void stop() {
        npc.getNavigation().resetMaxVisitedNodesMultiplier();
        project = null;
        npc.freeHands();
        if (project.isCompleted()) {
            npc.playSound(SoundEvents.VILLAGER_CELEBRATE,2.0F,0.9F);
        }
    }

    public void reset() {
        stateMachine.reset();
        preparingNextStepCountdown = 0;
        readingPlanCountdown = 0;
        timeSinceLastSuccessfulStep = 0;
        lastStepSuccessful = false;
    }

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
            ConstructionProject.ProjectStep nextStep = project.getNextStep();
            switch (nextStep.type()) {
                case PLACE_BLOCK -> {
                    npc.holdInMainHand(nextStep.blockState().getBlock().asItem().getDefaultInstance());
                }
                case REMOVE_BLOCK -> {

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
        ConstructionProject.ProjectStep nextStep = project.getNextStep();
        lookAtNextStepPos();
        if (npcPos().equals(nextStep.blockPos()) && npc.getNavigation().isDone()) {
            npc.moveTo(getRandomPosAwayFromStepPos());
            return;
        }
        switch (nextStep.type()) {
            case PLACE_BLOCK -> {
                Block nextBlock = nextStep.blockState().getBlock();
                if (project.executeNextStep()) {
                    lastStepSuccessful = true;
                    timeSinceLastSuccessfulStep = 0;
                    preparingNextStepCountdown = npc.getRandom().nextInt(6 - 1, 6 + 1);
                    npc.swing(InteractionHand.MAIN_HAND);
                    if (!project.isCompleted() && project.getNextStep().blockState().getBlock() != nextBlock) {
                        npc.freeMainHand();
                    }
                    return;
                }
            }
            case REMOVE_BLOCK -> {


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

    private BlockPos getNextStepPos() {
        return project.getNextStep().blockPos();
    }

    private void lookAtNextStepPos() {
        Vec3 toLookAt = getNextStepPos().getCenter();
        npc.getLookControl().setLookAt(toLookAt.x, toLookAt.y, toLookAt.z);
    }

    private void moveToNextStepPos() {
        BlockPos nextStepPos = getNextStepPos();
        if (npc.distanceToSqr(nextStepPos.getX(), nextStepPos.getY(), nextStepPos.getZ()) > 8) {
            if (npc.getNavigation().isDone()) {
                npc.getNavigation().moveTo(nextStepPos.getX(), nextStepPos.getY(), nextStepPos.getZ(), speedModifier);
            }
        } else {
            npc.getNavigation().stop();
        }
    }

    private boolean closeEnoughToExecuteStep() {
        /*
        int x = this.project.nextBlockPos().getX();
        int y = this.project.nextBlockPos().getY();
        int z = this.project.nextBlockPos().getZ();

         */
        return false;
        //return BuildingProjectCommand.superBuilder || npc.distanceToSqr(x, y, z) <= MAX_REACH_DIST;
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
        return npc.getTown().getCurrentConstructionProject();
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