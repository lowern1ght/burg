package org.dawnoftime.onceuponatown.client.screen.culturecreator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.network.culturecreator.C2SRequestCultureCCPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.C2SRequestCulturesCCPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.S2COpenCulturesCCScreenPacket;

import java.util.List;

import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class LevelsCCScreen extends BaseCCScreen {

    public LevelsCCScreen(S2COpenCulturesCCScreenPacket packet) {
        super(Ouat.translatable("cc", "levels_nav"));
    }

    @Override
    public List<NavigationTab> createNavigationMap() {
        return List.of(
/*                new NavigationTab(CULTURES_FOLDER_NAME, Ouat.translatable("cc", "cultures_nav"), C2SRequestCulturesCCPacket::new),
                new NavigationTab(cultureId, Component.literal(cultureId), () -> new C2SRequestCultureCCPacket(cultureId)),
                new NavigationTab("buildings", Ouat.translatable("cc", "buildings_nav"), () -> new C2SRequestCultureCCPacket(cultureId)),
                new NavigationTab(null, Component.literal(buildingId), () -> new C2SRequestCultureCCPacket(cultureId)),
                new NavigationTab(null, title, () -> null)*/
        );
    }

    @Override
    public void initWidgets() {
/*        for (String culture : cultures) {
            this.createButton(Component.literal(culture), btn -> Ouat.CLIENT.sendToServer(new C2SRequestCultureCCPacket(culture)));
        }*/
    }
}
