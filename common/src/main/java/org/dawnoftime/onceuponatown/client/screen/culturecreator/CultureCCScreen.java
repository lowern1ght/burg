package org.dawnoftime.onceuponatown.client.screen.culturecreator;

import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.network.culturecreator.S2COpenCultureCCScreenPacket;
import org.dawnoftime.onceuponatown.network.culturecreator.S2COpenCulturesCCScreenPacket;

import java.util.List;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;

public class CultureCCScreen extends BaseCCScreen {

    public CultureCCScreen(S2COpenCultureCCScreenPacket packet) {
        super(Component.literal(packet.getCultureId()), new String[]{MOD_ID, packet.getCultureId()});
    }

    @Override
    public void initWidgets() {
        this.createButton(Component.literal("COUCOU"), btn -> {

        });
    }
}
