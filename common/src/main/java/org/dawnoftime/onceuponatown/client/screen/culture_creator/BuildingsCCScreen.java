package org.dawnoftime.onceuponatown.client.screen.culture_creator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.screen.culture_creator.widgets_cc.ButtonWidgetCC;
import org.dawnoftime.onceuponatown.client.screen.culture_creator.widgets_cc.EditBoxAndConfirmWidgetCC;
import org.dawnoftime.onceuponatown.network.culturecreator.C2SRequestBuildingCCPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.C2SRequestCultureCCPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.C2SRequestCulturesCCPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.S2COpenBuildingsCCScreenPacket;

import java.util.List;

import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class BuildingsCCScreen extends BaseCCScreen {

    private final String cultureId;
    private final List<String> buildings;

    public BuildingsCCScreen(S2COpenBuildingsCCScreenPacket packet) {
        super(Ouat.translatable("cc", "buildings_nav"));
        cultureId = packet.getCultureId();
        buildings = packet.getBuildingIds();
    }

    @Override
    public List<NavigationTab> createNavigationMap() {
        return List.of(
                new NavigationTab(CULTURES_FOLDER_NAME, Ouat.translatable("cc", "cultures_nav"), C2SRequestCulturesCCPacket::new),
                new NavigationTab(cultureId, Component.literal(cultureId), () -> new C2SRequestCultureCCPacket(cultureId)),
                new NavigationTab("buildings", title, () -> null)
        );
    }

    @Override
    public void initWidgets() {
        for (String building : buildings) {
            this.addWidget(building, new ButtonWidgetCC(posX, Component.literal(building),
                    wdg -> Ouat.CLIENT.sendToServer(new C2SRequestBuildingCCPacket(cultureId, building))));
        }
        this.addWidget("new_culture", new EditBoxAndConfirmWidgetCC(posX, Ouat.translatable("cc", "buildings_hint_building"), font, false,
                wdg -> Ouat.CLIENT.sendToServer(new C2SRequestBuildingCCPacket(cultureId, wdg.get()))));
    }
}
