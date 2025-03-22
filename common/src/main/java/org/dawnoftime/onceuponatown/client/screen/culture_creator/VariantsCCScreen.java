package org.dawnoftime.onceuponatown.client.screen.culture_creator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.screen.culture_creator.widgets_cc.ButtonWidgetCC;
import org.dawnoftime.onceuponatown.client.screen.culture_creator.widgets_cc.EditBoxAndConfirmWidgetCC;
import org.dawnoftime.onceuponatown.network.culturecreator.*;

import java.util.List;

import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class VariantsCCScreen extends BaseCCScreen {

    private final String cultureId;
    private final String buildingId;
    private final List<String> variants;

    public VariantsCCScreen(S2COpenVariantsCCScreenPacket packet) {
        super(Ouat.translatable("cc", "variants_nav"));
        cultureId = packet.getCultureId();
        buildingId = packet.getBuildingId();
        variants = packet.getVariantIds();
    }

    @Override
    public List<NavigationTab> createNavigationMap() {
        return List.of(
                new NavigationTab(CULTURES_FOLDER_NAME, Ouat.translatable("cc", "cultures_nav"), C2SRequestCulturesCCPacket::new),
                new NavigationTab(cultureId, Component.literal(cultureId), () -> new C2SRequestCultureCCPacket(cultureId)),
                new NavigationTab(BUILDINGS_FOLDER_NAME, Ouat.translatable("cc", "buildings_nav"), () -> new C2SRequestBuildingsCCPacket(cultureId)),
                new NavigationTab(null, Component.literal(cultureId), () -> new C2SRequestBuildingCCPacket(cultureId, buildingId)),
                new NavigationTab(null, title, () -> null)
        );
    }

    @Override
    public void initWidgets() {
        for (String variant : variants) {
            this.addWidget(variant, new ButtonWidgetCC(posX, Component.literal(variant), wdg -> {}));
/*
                    wdg -> Ouat.CLIENT.sendToServer(new C2SRequestBuildingCCPacket(cultureId, variant))));
*/
        }
        this.addWidget("new_culture", new EditBoxAndConfirmWidgetCC(posX, Ouat.translatable("cc", "buildings_hint_building"), font, false, wdg -> {}));
/*
                wdg -> Ouat.CLIENT.sendToServer(new C2SRequestBuildingCCPacket(cultureId, wdg.get()))));
*/
    }
}
