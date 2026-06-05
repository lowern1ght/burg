package org.dawnoftime.onceuponatown.client.gui.culture_creator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc.ButtonWidgetCC;
import org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc.EditDigitWidgetCC;
import org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc.ItemEditBoxWidgetCC;
import org.dawnoftime.onceuponatown.network.culturecreator.*;

import java.util.Arrays;
import java.util.List;

import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class BuildingCCScreen extends BaseCCScreen {

    private final String cultureId;
    private final String buildingId;
    private final String initWeight;
    private final String initItem;

    public BuildingCCScreen(S2COpenBuildingCCScreenPacket packet) {
        super(Component.literal(packet.getBuildingId()));
        cultureId = packet.getCultureId();
        buildingId = packet.getBuildingId();
        initItem = packet.getItem();
        initWeight = packet.getWeight();
    }

    @Override
    public List<NavigationTab> createNavigationMap() {
        return Arrays.asList(
                new NavigationTab(CULTURES_FOLDER_NAME, Ouat.translatable("cc", "cultures_nav"), C2SRequestCulturesCCPacket::new),
                new NavigationTab(cultureId, Component.literal(cultureId), () -> new C2SRequestCultureCCPacket(cultureId)),
                new NavigationTab(BUILDINGS_FOLDER_NAME, Ouat.translatable("cc", "buildings_nav"), () -> new C2SRequestBuildingsCCPacket(cultureId)),
                new NavigationTab(null, title, () -> null)
        );
    }

    @Override
    public void initWidgets() {
        this.addWidget("item", new ItemEditBoxWidgetCC(posX, Ouat.translatable("cc", "building_item_id"), font))
                .set(null, initItem);
        this.addWidget("weight", new EditDigitWidgetCC(posX, Ouat.translatable("cc", "building_weight"), font, true))
                .set(null, initWeight);
        this.addWidget("levels", new ButtonWidgetCC(posX, Ouat.translatable("cc", "levels_nav"),
                wdg -> Ouat.CLIENT.sendToServer(new C2SRequestLevelsCCPacket(cultureId, buildingId))));
        this.addWidget("variants", new ButtonWidgetCC(posX, Ouat.translatable("cc", "variants_nav"),
                wdg -> Ouat.CLIENT.sendToServer(new C2SRequestVariantsCCPacket(cultureId, buildingId))));
    }

    @Override
    public void removed() {
        Ouat.CLIENT.sendToServer(new C2SSaveBuildingCCPacket(cultureId, buildingId, this.widgets.get("item").get(null), this.widgets.get("weight").get()));
        super.removed();
    }
}
