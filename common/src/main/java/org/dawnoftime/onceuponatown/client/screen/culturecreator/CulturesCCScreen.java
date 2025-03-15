package org.dawnoftime.onceuponatown.client.screen.culturecreator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.screen.culturecreator.widgets.WidgetCC;
import org.dawnoftime.onceuponatown.network.culturecreator.C2SRequestCultureCCPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.S2COpenCulturesCCScreenPacket;

import java.util.List;

import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class CulturesCCScreen extends BaseCCScreen {

    private final List<String> cultures;

    public CulturesCCScreen(S2COpenCulturesCCScreenPacket packet) {
        super(Ouat.translatable("cc", "cultures_nav"));
        cultures = packet.getCultureIds();
    }

    @Override
    public List<NavigationTab> createNavigationMap() {
        return List.of(
                new NavigationTab(CULTURES_FOLDER_NAME, title, () -> null)
        );
    }

    @Override
    public void initWidgets() {
        for (String culture : cultures) {
            this.addWidget(culture, new WidgetCC.ButtonCC(posX, Component.literal(culture),
                    wdg -> Ouat.CLIENT.sendToServer(new C2SRequestCultureCCPacket(culture))));
        }
        this.addWidget("new_culture", new WidgetCC.EditBoxAndConfirm(posX, Ouat.translatable("cc", "cultures_hint_culture"), font, false,
                wdg -> Ouat.CLIENT.sendToServer(new C2SRequestCultureCCPacket(wdg.get()))));
    }
}
