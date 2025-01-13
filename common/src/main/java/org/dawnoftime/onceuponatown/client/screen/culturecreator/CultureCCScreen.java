package org.dawnoftime.onceuponatown.client.screen.culturecreator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.network.culturecreator.C2SRequestCulturesCCPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.S2COpenCultureCCScreenPacket;

import java.util.Arrays;
import java.util.List;

import static org.dawnoftime.onceuponatown.culture.ServerCultures.CULTURE_FOLDER_NAME;

public class CultureCCScreen extends BaseCCScreen {

    private final String cultureId;

    public CultureCCScreen(S2COpenCultureCCScreenPacket packet) {
        super(Ouat.translatable("cc", "culture_title", packet.getCultureId()));
        cultureId = packet.getCultureId();
    }

    @Override
    public List<NavigationTab> createNavigationMap() {
        return Arrays.asList(
                new NavigationTab(CULTURE_FOLDER_NAME, Ouat.translatable("cc", "cultures_nav"), C2SRequestCulturesCCPacket::new),
                new NavigationTab(cultureId, Component.literal(cultureId), () -> null)
        );
    }

    @Override
    public void initWidgets() {
        this.createButton(Component.literal("COUCOU"), btn -> {

        });
    }
}
