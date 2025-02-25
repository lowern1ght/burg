package org.dawnoftime.onceuponatown.client.screen.culturecreator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.network.culturecreator.C2SRequestCultureCCPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.C2SRequestCulturesCCPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.S2COpenCultureCCScreenPacket;

import java.util.Arrays;
import java.util.List;

import static org.dawnoftime.onceuponatown.datapack.DataHandler.CULTURE_FOLDER_NAME;

public class BuildingCCScreen extends BaseCCScreen {

    private final String cultureId;
    private final String buildingId;

    public BuildingCCScreen(S2COpenCultureCCScreenPacket packet) {
        super(Component.literal(packet.getBuildingId()));
        cultureId = packet.getBuildingId();
    }

    @Override
    public List<NavigationTab> createNavigationMap() {
        return Arrays.asList(
                new NavigationTab(CULTURE_FOLDER_NAME, Ouat.translatable("cc", "cultures_nav"), C2SRequestCulturesCCPacket::new),
                new NavigationTab(cultureId, Component.literal(cultureId), () -> new C2SRequestCultureCCPacket(cultureId)),
                new NavigationTab("buildings", Ouat.translatable("cc", "buildings_nav"), () -> new C2SRequestCultureCCPacket(cultureId)),
                new NavigationTab(null, title, () -> null)
        );
    }

    @Override
    public void initWidgets() {
        this.createButton(Ouat.translatable("cc", "buildings_nav"), btn -> {

        });
        this.createButton(Ouat.translatable("cc", "levels_nav"), btn -> {});
    }
}
