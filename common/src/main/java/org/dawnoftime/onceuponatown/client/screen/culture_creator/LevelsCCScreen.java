package org.dawnoftime.onceuponatown.client.screen.culture_creator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.screen.culture_creator.widgets_cc.ButtonWidgetCC;
import org.dawnoftime.onceuponatown.network.culturecreator.*;

import java.util.List;

import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class LevelsCCScreen extends BaseCCScreen {

    private final String cultureId;
    private final String buildingId;
    private int levelNumber;

    public LevelsCCScreen(S2COpenLevelsCCScreenPacket packet) {
        super(Ouat.translatable("cc", "levels_nav"));
        cultureId = packet.getCultureId();
        buildingId = packet.getBuildingId();
        levelNumber = packet.getLevelNumber();
    }

    @Override
    public List<NavigationTab> createNavigationMap() {
        return List.of(
                new NavigationTab(CULTURES_FOLDER_NAME, Ouat.translatable("cc", "cultures_nav"), C2SRequestCulturesCCPacket::new),
                new NavigationTab(cultureId, Component.literal(cultureId), () -> new C2SRequestCultureCCPacket(cultureId)),
                new NavigationTab(BUILDINGS_FOLDER_NAME, Ouat.translatable("cc", "buildings_nav"), () -> new C2SRequestBuildingsCCPacket(cultureId)),
                new NavigationTab(null, Component.literal(cultureId), () -> new C2SRequestBuildingCCPacket(cultureId, buildingId)),
                new NavigationTab(null, title, () -> null)
        );
    }

    @Override
    public void initWidgets() {
        for (int level = 1; level <= levelNumber; level++) {
            this.addWidget(String.valueOf(level), new ButtonWidgetCC(posX, Ouat.translatable("cc", "level_nav", level), wdg -> {}));
/*
                    wdg -> Ouat.CLIENT.sendToServer(new C2SRequestBuildingCCPacket(cultureId, variant))));
*/
        }
    }
}
