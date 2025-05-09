package org.dawnoftime.onceuponatown;

import net.fabricmc.loader.api.FabricLoader;
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
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.dawnoftime.onceuponatown.network.OuatPacket;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public class CommonAbstractionsImpl implements CommonAbstractions {

    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isClientSide() {
        return false;
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider menu, Consumer<FriendlyByteBuf> buf) {

    }

    @Override
    public void sendToClient(Player player, OuatPacket packet) {

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
    public Item getItem(ResourceLocation resourceLocation) {
        return BuiltInRegistries.ITEM.get(resourceLocation);
    }

    @Override
    public ResourceLocation getResourceLocation(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    @Override
    public @Nullable HumanoidModel.ArmPose getItemCustomArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return null;
    }

    @Override
    public boolean canBeUsedAsFishingRod(ItemStack itemStack) {
        return false;
    }

    @Override
    public File getConfigFolder() {
        return FabricLoader.getInstance().getConfigDir().toFile();
    }

    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
