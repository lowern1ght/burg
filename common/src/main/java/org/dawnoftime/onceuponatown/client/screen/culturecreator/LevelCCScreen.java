package org.dawnoftime.onceuponatown.client.screen.culturecreator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.network.culturecreator.C2SRequestCulturesCCPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.S2COpenCultureCCScreenPacket;

import java.util.Arrays;
import java.util.List;

import static org.dawnoftime.onceuponatown.datapack.DataHandler.CULTURE_FOLDER_NAME;

public class LevelCCScreen extends BaseCCScreen {

    private final String cultureId;

    public LevelCCScreen(S2COpenCultureCCScreenPacket packet) {
        super(Component.literal(packet.getCultureId()));
        cultureId = packet.getCultureId();
    }

    @Override
    public List<NavigationTab> createNavigationMap() {
        return Arrays.asList(
                new NavigationTab(CULTURE_FOLDER_NAME, Ouat.translatable("cc", "cultures_nav"), C2SRequestCulturesCCPacket::new),
                new NavigationTab(cultureId, title, () -> null)
        );
    }

    @Override
    public void initWidgets() {
        this.createButton(Ouat.translatable("cc", "buildings_nav"), btn -> {

        });
    }
}
