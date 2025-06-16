package org.dawnoftime.onceuponatown.client.gui.culture_creator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc.ButtonWidgetCC;
import org.dawnoftime.onceuponatown.network.culturecreator.*;

import java.util.*;

import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class VariantLevelsCCScreen extends BaseCCScreen {

    private final String cultureId;
    private final String buildingId;
    private final String variantId;
    private final int numberOfLevels;

    public VariantLevelsCCScreen(S2COpenVariantLevelsCCScreenPacket packet) {
        super(Component.literal(packet.getVariantId()));
        cultureId = packet.getCultureId();
        buildingId = packet.getBuildingId();
        variantId = packet.getVariantId();
        numberOfLevels = packet.getNumberOfLevels();
    }

    @Override
    public List<NavigationTab> createNavigationMap() {
        return Arrays.asList(
                new NavigationTab(CULTURES_FOLDER_NAME, Ouat.translatable("cc", "cultures_nav"), C2SRequestCulturesCCPacket::new),
                new NavigationTab(cultureId, Component.literal(cultureId), () -> new C2SRequestCultureCCPacket(cultureId)),
                new NavigationTab(BUILDINGS_FOLDER_NAME, Ouat.translatable("cc", "buildings_nav"), () -> new C2SRequestBuildingsCCPacket(cultureId)),
                new NavigationTab(null, Component.literal(buildingId), () -> new C2SRequestBuildingCCPacket(cultureId, buildingId)),
                new NavigationTab(null, Ouat.translatable("cc", "variants_nav"), () -> new C2SRequestVariantsCCPacket(cultureId, buildingId)),
                new NavigationTab(null, title, () -> null)
        );
    }

    @Override
    public void initWidgets() {
        for (int level = 0; level < numberOfLevels; level++) {
            String buttonLevel = String.valueOf(level);
            int levelFinal = level;
            this.addWidget(buttonLevel, new ButtonWidgetCC(posX, Ouat.translatable("cc", "level_nav", levelFinal + 1),
                    wdg -> Ouat.CLIENT.sendToServer(new C2SRequestVariantLevelCCPacket(cultureId, buildingId, variantId, levelFinal))));
        }
    }
}
