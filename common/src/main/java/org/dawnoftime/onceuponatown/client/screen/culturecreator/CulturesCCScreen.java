package org.dawnoftime.onceuponatown.client.screen.culturecreator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.network.culturecreator.S2COpenCulturesCCScreenPacket;

import java.util.List;

public class CulturesCCScreen extends BaseCCScreen {

    private final List<String> cultures;

    public CulturesCCScreen(S2COpenCulturesCCScreenPacket packet) {
        super(Component.literal("Select the culture to modify"));
        cultures = packet.getCultureIds();
    }

    @Override
    public void initWidgets() {
        for (String culture : cultures) {
            this.createButton(Component.literal(culture), btn -> {

            });
        }
        // TODO Put a max number of characters based on the packet size "String with a maximum length of Short.MAX_VALUE."
        this.createEditBoxAndConfirm(Component.literal("Create a new culture..."), btn -> {

        });
    }
}
