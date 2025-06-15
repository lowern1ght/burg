package org.dawnoftime.onceuponatown.client;

import org.dawnoftime.onceuponatown.network.OuatPacket;
import org.dawnoftime.onceuponatown.network.PacketHandler;

public class ForgePlatformClientHelper implements IPlatformClientHelper {
    @Override
    public void sendToServer(OuatPacket packet) {
        PacketHandler.CHANNEL.sendToServer(packet);
    }
}