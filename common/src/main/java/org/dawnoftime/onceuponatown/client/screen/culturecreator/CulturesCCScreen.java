package org.dawnoftime.onceuponatown.client.screen.culturecreator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.network.culturecreator.S2COpenCulturesCCScreenPacket;

public class CulturesCCScreen extends BaseCCScreen {
    public CulturesCCScreen(S2COpenCulturesCCScreenPacket packet) {
        super(Component.literal("Select the culture to modify"));
    }
}
