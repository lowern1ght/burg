package org.dawnoftime.onceuponatown.client.gui.culture_creator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.schematic.Waypoint;
import org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc.ButtonWidgetCC;
import org.dawnoftime.onceuponatown.item.CultureCreatorItem;
import org.dawnoftime.onceuponatown.network.culturecreator.*;

import java.util.Arrays;
import java.util.List;

import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class WaypointsCCScreen extends BaseCCScreen {

    private final String cultureId;
    private final String buildingId;
    private final String variantId;
    private final int level;

    public WaypointsCCScreen(S2COpenWaypointsCCScreenPacket packet) {
        super(Ouat.translatable("cc", "waypoints", packet.getLevel() + 1));
        cultureId = packet.getCultureId();
        buildingId = packet.getBuildingId();
        variantId = packet.getVariantId();
        level = packet.getLevel();
    }

    @Override
    public List<NavigationTab> createNavigationMap() {
        return Arrays.asList(
                new NavigationTab(CULTURES_FOLDER_NAME, Ouat.translatable("cc", "cultures_nav"), C2SRequestCulturesCCPacket::new),
                new NavigationTab(cultureId, Component.literal(cultureId), () -> new C2SRequestCultureCCPacket(cultureId)),
                new NavigationTab(BUILDINGS_FOLDER_NAME, Ouat.translatable("cc", "buildings_nav"), () -> new C2SRequestBuildingsCCPacket(cultureId)),
                new NavigationTab(null, Component.literal(buildingId), () -> new C2SRequestBuildingCCPacket(cultureId, buildingId)),
                new NavigationTab(null, Ouat.translatable("cc", "variants_nav"), () -> new C2SRequestVariantsCCPacket(cultureId, buildingId)),
                new NavigationTab(null, Component.literal(variantId), () -> new C2SRequestVariantLevelsCCPacket(cultureId, buildingId, variantId)),
                new NavigationTab(null, Ouat.translatable("cc", "level_nav", level + 1), () -> new C2SRequestVariantLevelCCPacket(cultureId, buildingId, variantId, level)),
                new NavigationTab(null, title, () -> null)
        );
    }

    @Override
    public void initWidgets() {
        for (Waypoint wp : Waypoint.values()) {
            this.addWidget(wp.name(), new ButtonWidgetCC(posX, Ouat.translatable("cc", wp.name()), (btn) -> {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    ItemStack stack = player.getItemInHand(player.getUsedItemHand());
                    if (!stack.isEmpty() && stack.getItem() instanceof CultureCreatorItem) {
                        stack.getOrCreateTag().putString("ouat_culture_creator_waypoint", wp.name());
                    }
                }
                this.onClose();
            }));
        }
    }
}
