package org.dawnoftime.onceuponatown.client.gui.culture_creator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.network.culturecreator.*;

import java.util.Arrays;
import java.util.List;

import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class VariantLevelCCScreen extends BaseCCScreen {

    private final String cultureId;
    private final String buildingId;
    private final String variantId;
    private final int level;

    public VariantLevelCCScreen(S2COpenVariantLevelCCScreenPacket packet) {
        super(Ouat.translatable("cc", "variant_level_nav", packet.getVariantId(), packet.getLevel() + 1));
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
                new NavigationTab(null, title, () -> null)
        );
    }

    @Override
    public void initWidgets() {
        // Title 2 buttons
        // SizeSelector (si souris dessus quand scroll, monte et descend la valeur)
        // Title 1 button
        // IconButtonsInventory
    }
}
