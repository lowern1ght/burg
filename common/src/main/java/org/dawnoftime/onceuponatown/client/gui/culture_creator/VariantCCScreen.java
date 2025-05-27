package org.dawnoftime.onceuponatown.client.gui.culture_creator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.network.culturecreator.*;
import oshi.util.tuples.Pair;

import java.util.*;

import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class VariantCCScreen extends BaseCCScreen {

    private final String cultureId;
    private final String buildingId;
    private String initRequiredEra;
    private String initDwellingSlots;
    private Map<Integer, Pair<String, String>> initProfessionSlots;

    public VariantCCScreen(S2COpenVariantLevelsCCScreenPacket packet) {
        super(Ouat.translatable("cc", "level_nav", 1));
        cultureId = packet.getCultureId();
        buildingId = packet.getBuildingId();
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

    }

    @Override
    public void removed() {
        super.removed();
    }
}
