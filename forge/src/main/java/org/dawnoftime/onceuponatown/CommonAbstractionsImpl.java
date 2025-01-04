package org.dawnoftime.onceuponatown;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.dawnoftime.onceuponatown.network.IOuatPacket;
import org.dawnoftime.onceuponatown.network.PacketHandler;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class CommonAbstractionsImpl implements CommonAbstractions {
    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public void sendToClient(Player player, IOuatPacket packet) {
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            PacketHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer), packet);
        }
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
    public boolean canLivingConvert(LivingEntity entity, EntityType<? extends LivingEntity> outcome) {
        return ForgeEventFactory.canLivingConvert(entity, outcome, (timer) -> {
        });
    }

    @Override
    public void onLivingConvert(LivingEntity entity, LivingEntity outcome) {
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
    public AbstractArrow getArrow(Level level, LivingEntity entity, ItemStack itemStackInHand) {
        AbstractArrow arrow = new Arrow(level, entity);
        if (itemStackInHand.getItem() instanceof BowItem bowItem) {
            return bowItem.customArrow(arrow);
        }
        return arrow;
    }

    @Override
    public Item getItem(ResourceLocation resourceLocation) {
        Item item = ForgeRegistries.ITEMS.getValue(resourceLocation);
        return item == null ? Items.AIR : item;
    }

    @Override
    public ResourceLocation getResourceLocation(Item item) {
        ResourceLocation resourceLocation = ForgeRegistries.ITEMS.getKey(item);
        return resourceLocation == null ? BuiltInRegistries.ITEM.getKey(Items.AIR) : resourceLocation;
    }

    @Override
    public @Nullable HumanoidModel.ArmPose getItemCustomArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return IClientItemExtensions.of(stack).getArmPose(entity, hand, stack);
    }

    @Override
    public File getConfigFolder() {
        return FMLPaths.CONFIGDIR.get().toFile();
    }
}