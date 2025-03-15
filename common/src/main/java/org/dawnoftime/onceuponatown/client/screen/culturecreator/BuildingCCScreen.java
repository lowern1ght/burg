package org.dawnoftime.onceuponatown.client.screen.culturecreator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.screen.culturecreator.widgets.WidgetCC;
import org.dawnoftime.onceuponatown.network.culturecreator.*;

import java.util.Arrays;
import java.util.List;

import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class BuildingCCScreen extends BaseCCScreen {

    private final String cultureId;
    private final String buildingId;

    public BuildingCCScreen(S2COpenBuildingCCScreenPacket packet) {
        super(Component.literal(packet.getBuildingId()));
        cultureId = packet.getCultureId();
        buildingId = packet.getBuildingId();
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
        this.addWidget("weight", new WidgetCC.EditDigit(posX, Ouat.translatable("cc", "weight"), font, true));
        this.addWidget("levels", new WidgetCC.ButtonCC(posX, Ouat.translatable("cc", "levels_nav"),
                wdg -> {}));
        this.addWidget("variants", new WidgetCC.ButtonCC(posX, Ouat.translatable("cc", "variants_nav"),
                wdg -> {}));
    }

    @Override
    public void removed() {
        super.removed();
        Ouat.CLIENT.sendToServer(new C2SSaveBuildingCCPacket(cultureId, buildingId, "", this.widgets.get("weight").get()));
    }
}
