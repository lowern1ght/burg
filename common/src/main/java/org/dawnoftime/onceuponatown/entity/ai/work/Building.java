package org.dawnoftime.onceuponatown.entity.ai.work;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.building.BuildProject;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.entity.ai.controlflow.Task;
import org.dawnoftime.onceuponatown.entity.ai.controlflow.Tasks;
import org.dawnoftime.onceuponatown.entity.ai.core.GoToPosition;
import org.dawnoftime.onceuponatown.entity.ai.core.SetMemory;
import org.dawnoftime.onceuponatown.registry.ItemRegistry;

import static org.dawnoftime.onceuponatown.building.BuildProject.Action.NOTHING;

public class Building {
    private static final boolean SUPER_BUILDER = false;
    private static final double REACH_DIST = 5.0D;
    private static final int BUILD_SPEED = 6; // Ticks in between each step

    public static Task<Npc> create() {
        Tasks<Npc> tasks = new Tasks<>();

        var executeStep = tasks.sequence("Execute step");

        executeStep.step(tasks.tryAll("Maybe read plan")
            .option(0, tasks.ticking("Read plan")
                .onStart(npc -> {
                    npc.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                    npc.holdInMainHand(ItemRegistry.REGISTRY.TOWN_SCROLL.get().getDefaultInstance());
                    npc.freeOffHand();
                    npc.playSound(SoundEvents.BOOK_PAGE_TURN);
                    npc.setReading(true);
                })
                .onStop(npc -> {
                    npc.freeMainHand();
                    npc.playSound(SoundEvents.BOOK_PAGE_TURN);
                    npc.setReading(false);
                })
                .maxDuration(npc -> npc.getRandom().nextInt((20 * 2), (20 * 6)))
                .probability(0.05F)
                .assemble()
            )
            .assemble()
        );

        executeStep.step(tasks.single("Hold material", npc -> {
            BuildProject project = npc.getAssignedProject();
            var nextAction = project.getNextAction();
                BlockState nextStepState = project.getNextStepState();
            if (nextAction == NOTHING || nextStepState == null) {
                return;
            }
            switch (nextAction) {
                case PLACE_BLOCK -> npc.holdInOffHand(nextStepState.getBlock().asItem().getDefaultInstance());
                case DESTROY_BLOCK -> {
                    ItemStack stack;
                    if (nextStepState.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
                        stack = Items.WOODEN_SHOVEL.getDefaultInstance();
                    } else if (nextStepState.is(BlockTags.MINEABLE_WITH_AXE)) {
                        stack = Items.WOODEN_AXE.getDefaultInstance();
                    } else if (nextStepState.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
                        stack = Items.WOODEN_PICKAXE.getDefaultInstance();
                    } else if (nextStepState.is(BlockTags.MINEABLE_WITH_HOE)) {
                        stack = Items.WOODEN_HOE.getDefaultInstance();
                    } else {
                        stack = Items.STICK.getDefaultInstance();
                    }
                    npc.holdInMainHand(stack);
                }
            }
        }).assemble());


        executeStep.step(new SetMemory<>("Look at step", MemoryModuleType.LOOK_TARGET,
                npc -> new BlockPosTracker(npc.getAssignedProject().getNextStepPos().getCenter()))
        );

        executeStep.step(new DoNothing(5, 10));

        executeStep.step(tasks.ticking("Execute step")
            .stopIf(npc -> {
                boolean stop = false;
                BuildProject project = npc.getAssignedProject();
                var nextAction = project.getNextAction();
                var nextStepPos = project.getNextStepPos();
                if (nextAction != BuildProject.Action.DESTROY_BLOCK) {
                    npc.stopBreakingBlock();
                }
                switch (nextAction) {
                    case PLACE_BLOCK -> {
                        if (project.nextStep()) {
                            npc.swing(InteractionHand.OFF_HAND);
                            stop = true;
                        }
                    }
                    case DESTROY_BLOCK -> {
                        if (npc.getAttackedBlock() == null || !npc.getAttackedBlock().equals(nextStepPos)) {
                            npc.startBreakingBlock(nextStepPos);
                        } else if (npc.tickBreakBlock(InteractionHand.MAIN_HAND) && project.nextStep()) {
                            stop = true;
                        }
                    }
                }
                int attempt = 0;
                while (attempt < 100 &&
                    project.getNextAction() == NOTHING ||
                    project.getNextAction() == BuildProject.Action.SPAWN_ENTITY
                ) {
                    ++attempt;
                    project.nextStep();
                }
                return stop;
            })
            .assemble()
        );

        var executeStepOrGetCloser = tasks.firstValid("Execute step or get closer")
            .option(0, new GoToPosition<>("Get closer", npc -> npc.getAssignedProject().getNextStepPos(), 5, 1.0F))
            .option(1, executeStep.assemble())
            .assemble();

        var root = tasks.repeat("Building logic", executeStepOrGetCloser)
            .onStart(npc -> {
                npc.getNavigation().setMaxVisitedNodesMultiplier(10.0F);
                npc.freeHands();
                npc.setCrossingArms(false);
            })
            .onStop(npc -> {
                npc.getNavigation().resetMaxVisitedNodesMultiplier();
                npc.freeHands();
                npc.setCrossingArms(true);
                npc.stopBreakingBlock();
                if (npc.getAssignedProject().isCompleted()) {
                    npc.playSound(SoundEvents.VILLAGER_CELEBRATE, 2.0F, 0.9F);
                }
            })
            .startIf(npc -> {
                BuildProject project = null;
                if (npc.getTown() != null) {
                    project = npc.getTown().getPendingProject();
                }
                if (project != null) {
                    npc.setAssignedProject(project);
                }
                return npc.getAssignedProject() != null && npc.getAssignedProject().isAvailable();
            })
            .stopIf(npc -> npc.getAssignedProject() == null || !npc.getAssignedProject().isAvailable())
            .forActivity(Activity.WORK)
            .assemble();

        return root;
    }

    private boolean canReachStep(BuildProject project, Npc npc) {
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
}
