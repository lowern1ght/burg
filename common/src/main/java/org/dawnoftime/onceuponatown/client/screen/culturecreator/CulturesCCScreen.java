package org.dawnoftime.onceuponatown.client.screen.culturecreator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.screen.widgets.EditBoxIconButton;
import org.dawnoftime.onceuponatown.network.C2SSelectTradePacket;
import org.dawnoftime.onceuponatown.network.OuatPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.C2SRequestCultureCCPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.S2COpenCulturesCCScreenPacket;
import oshi.util.tuples.Pair;

import java.util.List;
import java.util.function.Supplier;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;
import static org.dawnoftime.onceuponatown.culture.ServerCultures.CULTURE_FOLDER_NAME;

public class CulturesCCScreen extends BaseCCScreen {

    private final List<String> cultures;

    public CulturesCCScreen(S2COpenCulturesCCScreenPacket packet) {
        super(Ouat.translatable("cc", "cultures_title"));
        cultures = packet.getCultureIds();
    }

    @Override
    public List<NavigationTab> createNavigationMap() {
        return List.of(
                new NavigationTab(CULTURE_FOLDER_NAME, Ouat.translatable("cc", "cultures_nav"), () -> null)
        );
    }

    @Override
    public void initWidgets() {
        for (String culture : cultures) {
            this.createButton(Component.literal(culture), btn -> {
                Ouat.CLIENT.sendToServer(new C2SRequestCultureCCPacket(culture));
            });
        }
        this.createEditBoxAndConfirm(Component.literal("Create a new culture..."), btn -> Ouat.CLIENT.sendToServer(new C2SRequestCultureCCPacket(((EditBoxIconButton) btn).getContent())));
    }
}
