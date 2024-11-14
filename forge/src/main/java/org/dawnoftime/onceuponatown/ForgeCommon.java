package org.dawnoftime.onceuponatown;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import org.dawnoftime.onceuponatown.Common;
import org.dawnoftime.onceuponatown.culture.CultureManager;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.network.ForgeNetwork;
import org.dawnoftime.onceuponatown.network.IOuatPacket;
import org.dawnoftime.onceuponatown.registry.OuatCommands;
import org.dawnoftime.onceuponatown.registry.OuatEntitiesRegistry;
import org.dawnoftime.onceuponatown.town.TownManager;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class ForgeCommon extends Common {
    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public void sendToClient(ServerPlayer player, IOuatPacket packet) {
        ForgeNetwork.CHANNEL.sendTo(packet, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    @Override
    public void sendToServer(IOuatPacket packet) {
        ForgeNetwork.CHANNEL.sendToServer(packet);
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider menu, Consumer<FriendlyByteBuf> buf) {
        NetworkHooks.openScreen(player, menu, buf);
    }

    @Override
    public void renderTooltip(GuiGraphics graphics, Font font, List<Component> textComponents, @Nullable TooltipComponent tooltipComponent, ItemStack stack, int mouseX, int mouseY) {
        graphics.renderTooltip(font, textComponents, Optional.ofNullable(tooltipComponent), stack, mouseX, mouseY);
        stack.getItem().isDamageable(stack);
    }

    @Override
    public boolean canLivingConvert(LivingEntity entity, EntityType<? extends LivingEntity> outcome){
        return ForgeEventFactory.canLivingConvert(entity, outcome, (timer) -> {});
    }

    @Override
    public void onLivingConvert(LivingEntity entity, LivingEntity outcome){
        ForgeEventFactory.onLivingConvert(entity, outcome);
    }

    @Override
    public boolean canBeUsedAsFishingRod(ItemStack itemStack) {
        return itemStack.canPerformAction(ToolActions.FISHING_ROD_CAST);
    }

    @Override
    public ItemStack getProjectile(LivingEntity entity, ItemStack weaponStack, ItemStack projectileStack) {
        return ForgeHooks.getProjectile(entity, weaponStack, projectileStack);
    }

    @Override
    public AbstractArrow getArrow(Level level, LivingEntity entity, ItemStack itemStackInHand){
        AbstractArrow arrow = new Arrow(level, entity);
        if (itemStackInHand.getItem() instanceof BowItem bowItem) {
            return bowItem.customArrow(arrow);
        }
        return arrow;
    }

    @Override
    public @Nullable HumanoidModel.ArmPose getItemCustomArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return IClientItemExtensions.of(stack).getArmPose(entity, hand, stack);
    }

    @Mod.EventBusSubscriber(modid = Ouat.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModBusCommonEvents {
        @SubscribeEvent
        public static void commonSetup(FMLCommonSetupEvent event) {
            event.enqueueWork(ForgeNetwork::init);
        }
    }

    @Mod.EventBusSubscriber(modid = Ouat.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeBusCommonEvents {
        @SubscribeEvent
        public static void createEntityAttributes(EntityAttributeCreationEvent event) {
            event.put(OuatEntitiesRegistry.ENTITY_REGISTRY.NPC.get(), Npc.createAttributes().build());
        }
        @SubscribeEvent
        public static void registerCommands(RegisterCommandsEvent event) {
            OuatCommands.register(event.getDispatcher());
        }

        @SubscribeEvent
        public static void onServerStarting(ServerStartingEvent event) {
            CultureManager.loadCultures(event.getServer().getResourceManager());
        }

        @SubscribeEvent
        public static void onLevelTick(TickEvent.LevelTickEvent event) {
            if (event.level instanceof ServerLevel level && event.phase.equals(TickEvent.Phase.END)) {
                //TownManager.tickTowns(level);
            }
        }

        @SubscribeEvent
        public static void addReloadListener(AddReloadListenerEvent event) {
            //event.addListener(CultureManager.instance());
        }
        @SubscribeEvent
        public static void finalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
            if (event.getEntity() instanceof Npc npc) {
                npc.onFinalizeSpawnEvent();
            }
        }
    }
}