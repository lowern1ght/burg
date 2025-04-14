package org.dawnoftime.onceuponatown;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
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

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

public interface CommonAbstractions {
    /**
     * Checks if a mod with the given id is loaded.
     *
     * @param modId The mod to check if it is loaded.
     * @return True if the mod is loaded, false otherwise.
     */
    boolean isModLoaded(String modId);

    boolean isClientSide();

    /**
     * Opens the given GUI for the player passed in parameter
     *
     * @param player The player to open the GUI for
     * @param menu   A supplier of container properties including the registry name of the container
     * @param buf    Consumer to write any additional data the GUI needs
     */
    void openMenu(ServerPlayer player, MenuProvider menu, Consumer<FriendlyByteBuf> buf);

    void sendToClient(Player player, OuatPacket packet);

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
    void renderTooltip(GuiGraphics graphics, Font font, List<Component> textComponents, @Nullable TooltipComponent tooltipComponent, ItemStack stack, int mouseX, int mouseY);

    boolean canLivingConvert(LivingEntity entity, EntityType<? extends LivingEntity> outcome);

    void onLivingConvert(LivingEntity entity, LivingEntity outcome);

    ItemStack getProjectile(LivingEntity entity, ItemStack weaponStack, ItemStack projectileStack);

    AbstractArrow getArrow(Level level, LivingEntity entity, ItemStack itemStackInHand);

    Item getItem(ResourceLocation resourceLocation);

    ResourceLocation getResourceLocation(Item item);

    /**
     * Function that returns an ArmPose if the item in hand has a custom animation. Returns null otherwise.
     *
     * @param entity LivingEntity that is holding the ItemStack.
     * @param hand   Hand in which the ItemStack is hold.
     * @param stack  ItemStack of the Item being used by the entity.
     * @return An ArmPose if a custom animation is defined for this item (modded items).
     */
    @Nullable
    HumanoidModel.ArmPose getItemCustomArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack);

    /**
     * Function used to check whether an item can be used as a fishing rod, for example to render the position of the arms correctly.
     *
     * @param itemStack to be checked.
     * @return True if this item can be used to fish.
     */
    boolean canBeUsedAsFishingRod(ItemStack itemStack);

    /**
     * Function used by the culture creator to get the path of the export folders.
     *
     * @return The File corresponding to the folder .minecraft/config.
     */
    File getConfigFolder();
}