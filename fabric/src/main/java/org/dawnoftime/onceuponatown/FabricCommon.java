package org.dawnoftime.onceuponatown;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.dawnoftime.onceuponatown.network.IOuatPacket;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public class FabricCommon extends Common {

    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {

        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider menu, Consumer<FriendlyByteBuf> buf) {

    }

    @Override
    public void sendToServer(IOuatPacket packet) {

    }

    @Override
    public void sendToClient(ServerPlayer player, IOuatPacket packet) {

    }

    @Override
    public void renderTooltip(GuiGraphics graphics, Font font, List<Component> textComponents, @Nullable TooltipComponent tooltipComponent, ItemStack stack, int mouseX, int mouseY) {

    }

    @Override
    public boolean canLivingConvert(LivingEntity entity, EntityType<? extends LivingEntity> outcome) {
        return false;
    }

    @Override
    public void onLivingConvert(LivingEntity entity, LivingEntity outcome) {

    }

    @Override
    public ItemStack getProjectile(LivingEntity entity, ItemStack weaponStack, ItemStack projectileStack) {
        return null;
    }

    @Override
    public AbstractArrow getArrow(Level level, LivingEntity entity, ItemStack itemStackInHand) {
        return null;
    }

    @Override
    public @Nullable HumanoidModel.ArmPose getItemCustomArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return null;
    }

    @Override
    public boolean canBeUsedAsFishingRod(ItemStack itemStack) {
        return false;
    }
    
    public boolean isDevelopmentEnvironment() {

        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
