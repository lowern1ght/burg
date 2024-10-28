package org.dawnoftime.onceuponatown.platform;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.network.NetworkHooks;
import org.dawnoftime.onceuponatown.client.screen.BuyScreen;
import org.dawnoftime.onceuponatown.client.screen.tooltip.TradeItemTooltip;
import org.dawnoftime.onceuponatown.network.ForgeNetwork;
import org.dawnoftime.onceuponatown.network.IOuatPacket;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class ForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Forge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public void openMenu(ServerPlayer player, MenuProvider menu, Consumer<FriendlyByteBuf> buf) {
        NetworkHooks.openScreen(player, menu, buf);
    }

    @Override
    public void sendToServer(IOuatPacket packet) {
        ForgeNetwork.CHANNEL.sendToServer(packet);
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
}