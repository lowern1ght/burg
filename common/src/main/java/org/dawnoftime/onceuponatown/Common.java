package org.dawnoftime.onceuponatown;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.dawnoftime.onceuponatown.network.IOuatPacket;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public abstract class Common {

    public void init(){

    }

    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return True if the mod is loaded, false otherwise.
     */
    public abstract boolean isModLoaded(String modId);

    /**
     * Opens the given GUI for the player passed in parameter
     *
     * @param player The player to open the GUI for
     * @param menu   A supplier of container properties including the registry name of the container
     * @param buf    Consumer to write any additional data the GUI needs
     */
    public abstract void openMenu(ServerPlayer player, MenuProvider menu, Consumer<FriendlyByteBuf> buf);

    /**
     * Sends the given packet to the Server.
     *
     * @param packet C2S packet to be sent.
     */
    public abstract void sendToServer(IOuatPacket packet);

    /**
     * Function called to render tooltips on a GUI. Forge requires an ItemStack while Vanilla does not.
     *
     * @param graphics         GuiGraphics that will render this tooltip.
     * @param font             Font of the tooltip.
     * @param textComponents   Text of the tooltip.
     * @param tooltipComponent
     * @param stack            ItemStack source of the tooltip.
     * @param mouseX           Mouse X coordinate.
     * @param mouseY           Mouse Y coordinate.
     */
    public abstract void renderTooltip(GuiGraphics graphics, Font font, List<Component> textComponents, @Nullable TooltipComponent tooltipComponent, ItemStack stack, int mouseX, int mouseY);

    public abstract boolean canLivingConvert(LivingEntity entity, EntityType<? extends LivingEntity> outcome);

    public abstract void onLivingConvert(LivingEntity entity, LivingEntity outcome);

    // TODO Maybe we could find a way to check directly in the class NpcFishingHookRenderer without using this ?
    public abstract boolean canUseFishingRod(ItemStack stack);

    public abstract ItemStack getProjectile(LivingEntity entity, ItemStack weaponStack, ItemStack projectileStack);
}