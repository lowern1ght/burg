package org.dawnoftime.onceuponatown.client.gui.culture_creator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc.AddWidgetCC;
import org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc.ButtonWidgetCC;
import org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc.DropAndEditBoxWidgetCC;
import org.dawnoftime.onceuponatown.network.culturecreator.*;
import oshi.util.tuples.Pair;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class LevelsCCScreen extends BaseCCScreen {

    private final String cultureId;
    private final String buildingId;
    private byte levelNumber;

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
                new NavigationTab(null, Component.literal(buildingId), () -> new C2SRequestBuildingCCPacket(cultureId, buildingId)),
                new NavigationTab(null, title, () -> null)
        );
    }

    @Override
    public void initWidgets() {
        for (int level = 0; level < levelNumber; level++) {
            String buttonLevel = String.valueOf(level);
            this.addWidget(buttonLevel, new ButtonWidgetCC(posX, Ouat.translatable("cc", "level_nav", level + 1),
                    wdg -> Ouat.CLIENT.sendToServer(new C2SRequestLevelCCPacket(cultureId, buildingId, buttonLevel))));
        }
        this.addWidget("add_level", new AddWidgetCC(posX, (widget) -> {
            if (levelNumber < 127) {
                String newButtonLevel = String.valueOf(levelNumber);
                this.insertBeforeLast(newButtonLevel, new ButtonWidgetCC(
                        posX,
                        Ouat.translatable("cc", "level_nav", levelNumber + 1),
                        wdg -> Ouat.CLIENT.sendToServer(new C2SRequestLevelCCPacket(cultureId, buildingId, newButtonLevel))));
                this.updateWidgetPositions();
                this.updateMaxScrollOffset();
                levelNumber++;
            }
        }));
    }

    @Override
    public void removed() {
        Ouat.CLIENT.sendToServer(new C2SSaveLevelsCCPacket(cultureId, buildingId, levelNumber));
        super.removed();
    }
}
