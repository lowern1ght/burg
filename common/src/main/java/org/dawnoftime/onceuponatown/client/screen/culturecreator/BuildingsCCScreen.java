package org.dawnoftime.onceuponatown.client.screen.culturecreator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.screen.widgets.EditBoxIconButton;
import org.dawnoftime.onceuponatown.network.culturecreator.C2SRequestCultureCCPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.C2SRequestCulturesCCPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.S2COpenCulturesCCScreenPacket;

import java.util.List;

import static org.dawnoftime.onceuponatown.culture.ServerCultures.CULTURE_FOLDER_NAME;

public class BuildingsCCScreen extends BaseCCScreen {

    private final String cultureId;
    private final List<String> buildings;

    public BuildingsCCScreen(S2COpenCulturesCCScreenPacket packet) {
        super(Ouat.translatable("cc", "buildings_nav"));
    }

    @Override
    public List<NavigationTab> createNavigationMap() {
        return List.of(
                new NavigationTab(CULTURE_FOLDER_NAME, Ouat.translatable("cc", "cultures_nav"), C2SRequestCulturesCCPacket::new),
                new NavigationTab(cultureId, Component.literal(cultureId), () -> new C2SRequestCultureCCPacket(cultureId)),
                new NavigationTab("buildings", title, () -> null)
        );
    }

    @Override
    public void initWidgets() {
        for (String culture : buildings) {
            this.createButton(Component.literal(culture), btn -> Ouat.CLIENT.sendToServer(new C2SRequestCultureCCPacket(culture)));
        }
        this.createEditBoxAndConfirm(Ouat.translatable("cc", "buildings_hint_building"), btn -> Ouat.CLIENT.sendToServer(new C2SRequestCultureCCPacket(((EditBoxIconButton) btn).getContent())));
    }
}
