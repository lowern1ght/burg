package org.dawnoftime.onceuponatown.client.screen.culturecreator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.screen.widgets.EditBoxIconButton;
import org.dawnoftime.onceuponatown.network.C2SSelectTradePacket;
import org.dawnoftime.onceuponatown.network.culturecreator.C2SRequestCultureCCPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.S2COpenCulturesCCScreenPacket;

import java.util.List;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;

public class CulturesCCScreen extends BaseCCScreen {

    private final List<String> cultures;

    public CulturesCCScreen(S2COpenCulturesCCScreenPacket packet) {
        super(Component.literal("Select the culture to modify"), new String[]{MOD_ID});
        cultures = packet.getCultureIds();
    }

    @Override
    public void initWidgets() {
        for (String culture : cultures) {
            this.createButton(Component.literal(culture), btn -> {
                Ouat.CLIENT.sendToServer(new C2SRequestCultureCCPacket(culture));
            });
        }
        // TODO Put a max number of characters based on the packet size "String with a maximum length of Short.MAX_VALUE."
        this.createEditBoxAndConfirm(Component.literal("Create a new culture..."), btn -> Ouat.CLIENT.sendToServer(new C2SRequestCultureCCPacket(((EditBoxIconButton) btn).getContent())));
    }
}
