package org.dawnoftime.onceuponatown;

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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.dawnoftime.onceuponatown.network.IOuatPacket;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

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

    public abstract ItemStack getProjectile(LivingEntity entity, ItemStack weaponStack, ItemStack projectileStack);

    public abstract AbstractArrow getArrow(Level level, LivingEntity entity, ItemStack itemStackInHand);

    /**
     * Function that returns an ArmPose if the item in hand has a custom animation. Returns null otherwise.
     * @param entity LivingEntity that is holding the ItemStack.
     * @param hand Hand in which the ItemStack is hold.
     * @param stack ItemStack of the Item being used by the entity.
     * @return An ArmPose if a custom animation is defined for this item (modded items).
     */
    public abstract @Nullable HumanoidModel.ArmPose getItemCustomArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack);

    /**
     * Function used to check whether an item can be used as a fishing rod, for example to render the position of the arms correctly.
     * @param itemStack to be checked.
     * @return True if this item can be used to fish.
     */
    public abstract boolean canBeUsedAsFishingRod(ItemStack itemStack);
}