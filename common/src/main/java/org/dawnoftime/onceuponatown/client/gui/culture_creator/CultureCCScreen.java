package org.dawnoftime.onceuponatown.client.gui.culture_creator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc.ButtonWidgetCC;
import org.dawnoftime.onceuponatown.network.culturecreator.C2SRequestBuildingsCCPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.C2SRequestCulturesCCPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.S2COpenCultureCCScreenPacket;

import java.util.Arrays;
import java.util.List;

import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class CultureCCScreen extends BaseCCScreen {

    private final String cultureId;

    public CultureCCScreen(S2COpenCultureCCScreenPacket packet) {
        super(Component.literal(packet.getCultureId()));
        cultureId = packet.getCultureId();
    }

    @Override
    public List<NavigationTab> createNavigationMap() {
        return Arrays.asList(
                new NavigationTab(CULTURES_FOLDER_NAME, Ouat.translatable("cc", "cultures_nav"), C2SRequestCulturesCCPacket::new),
                new NavigationTab(cultureId, title, () -> null)
        );
    }

    @Override
    public void initWidgets() {
        this.addWidget("buildings", new ButtonWidgetCC(posX, Ouat.translatable("cc", "buildings_nav"),
                wdg -> Ouat.CLIENT.sendToServer(new C2SRequestBuildingsCCPacket(cultureId))));
    }
}
