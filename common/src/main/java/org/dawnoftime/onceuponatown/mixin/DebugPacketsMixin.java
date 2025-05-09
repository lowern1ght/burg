package org.dawnoftime.onceuponatown.mixin;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import org.dawnoftime.onceuponatown.Config;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(DebugPackets.class)
public class DebugPacketsMixin {
    @Inject(method = "sendPathFindingPacket", at = @At("HEAD"))
    private static void implementSendPathFindingPacket(Level level, Mob mob, @Nullable Path path, float maxDistanceToWaypoint, CallbackInfo ci) {
        if (Config.DEBUG_PATHFINDING && !level.isClientSide() && path != null && mob instanceof Npc) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            buffer.writeInt(mob.getId());
            buffer.writeFloat(maxDistanceToWaypoint);
            path.writeToStream(buffer);
            sendPacketToAllPlayers((ServerLevel) level, buffer, ClientboundCustomPayloadPacket.DEBUG_PATHFINDING_PACKET);
        }
    }

    @Inject(method = "sendGoalSelector", at = @At("HEAD"))
    private static void implementSendGoalSelector(Level level, Mob mob, GoalSelector goalSelector, CallbackInfo ci) {
        if (Config.DEBUG_GOALS && !level.isClientSide() && mob instanceof Npc) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            buffer.writeBlockPos(mob.blockPosition());
            buffer.writeInt(mob.getId());
            Set<WrappedGoal> goals = goalSelector.getAvailableGoals();
            buffer.writeInt(goals.size());
            for (WrappedGoal goal : goals) {
                buffer.writeInt(goal.getPriority());
                buffer.writeBoolean(goal.isRunning());
                buffer.writeUtf(goal.getGoal().toString(), 255);
            }
            sendPacketToAllPlayers((ServerLevel) level, buffer, ClientboundCustomPayloadPacket.DEBUG_GOAL_SELECTOR);
        }
    }

    @Inject(method = "sendEntityBrain", at = @At("HEAD"))
    private static void implementSendEntityBrain(LivingEntity livingEntity, CallbackInfo ci) {
        if (Config.DEBUG_BRAINS && !livingEntity.level().isClientSide() && livingEntity instanceof Npc) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            buffer.writeDouble(livingEntity.getX());
            buffer.writeDouble(livingEntity.getY());
            buffer.writeDouble(livingEntity.getZ());
            buffer.writeUUID(livingEntity.getUUID());
            buffer.writeInt(livingEntity.getId());
            buffer.writeUtf(livingEntity.getDisplayName().getString());
            if (livingEntity instanceof Villager villager) {
                buffer.writeUtf(villager.getVillagerData().getProfession().toString());
                buffer.writeInt(villager.getVillagerXp());
            } else {
                buffer.writeUtf("");
                buffer.writeInt(0);
            }
            buffer.writeFloat(livingEntity.getHealth());
            buffer.writeFloat(livingEntity.getMaxHealth());
            writeBrain(livingEntity, buffer);
            sendPacketToAllPlayers((ServerLevel) livingEntity.level(), buffer, ClientboundCustomPayloadPacket.DEBUG_BRAIN);
        }
    }

    @Shadow
    private static void writeBrain(LivingEntity livingEntity, FriendlyByteBuf buffer) {
    }

    @Shadow
    private static void sendPacketToAllPlayers(ServerLevel level, FriendlyByteBuf buffer, ResourceLocation identifier) {
    }
}
