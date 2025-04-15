package org.dawnoftime.onceuponatown.client.gui.culture_creator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc.EditDigitWidgetCC;
import org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc.ItemEditBoxWidgetCC;
import org.dawnoftime.onceuponatown.network.culturecreator.*;

import java.util.Arrays;
import java.util.List;

import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class LevelCCScreen extends BaseCCScreen {

    private final String cultureId;
    private final String buildingId;
    private final String level;
    private final String initRequiredEra;
    private final String initDwellingSlots;

    public LevelCCScreen(S2COpenLevelCCScreenPacket packet) {
        super(Ouat.translatable("cc", "level_nav", Integer.parseInt(packet.getLevel()) + 1));
        cultureId = packet.getCultureId();
        buildingId = packet.getBuildingId();
        level = packet.getLevel();
        initRequiredEra = packet.getRequiredEra();
        initDwellingSlots = packet.getDwellingSlots();
    }

    @Override
    public List<NavigationTab> createNavigationMap() {
        return Arrays.asList(
                new NavigationTab(CULTURES_FOLDER_NAME, Ouat.translatable("cc", "cultures_nav"), C2SRequestCulturesCCPacket::new),
                new NavigationTab(cultureId, Component.literal(cultureId), () -> new C2SRequestCultureCCPacket(cultureId)),
                new NavigationTab(BUILDINGS_FOLDER_NAME, Ouat.translatable("cc", "buildings_nav"), () -> new C2SRequestBuildingsCCPacket(cultureId)),
                new NavigationTab(null, Component.literal(buildingId), () -> new C2SRequestBuildingCCPacket(cultureId, buildingId)),
                new NavigationTab(null, Ouat.translatable("cc", "levels_nav"), () -> new C2SRequestLevelsCCPacket(cultureId, buildingId)),
                new NavigationTab(null, title, () -> null)
        );
    }

    @Override
    public void initWidgets() {
        this.addWidget("required_era", new EditDigitWidgetCC(posX, Ouat.translatable("cc", "building_level_required_era"), font, true))
                .set(initRequiredEra);
        this.addWidget("dwelling_slots", new EditDigitWidgetCC(posX, Ouat.translatable("cc", "building_level_dwelling_slots"), font, true))
                .set(initDwellingSlots);
    }

    @Override
    public void removed() {
        Ouat.CLIENT.sendToServer(new C2SSaveLevelCCPacket(cultureId, buildingId, level, this.widgets.get("required_era").get(), this.widgets.get("dwelling_slots").get()));
        super.removed();
    }
}
