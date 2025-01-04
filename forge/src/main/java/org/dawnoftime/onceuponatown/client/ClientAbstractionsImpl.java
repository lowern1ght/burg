package org.dawnoftime.onceuponatown.client;

import org.dawnoftime.onceuponatown.network.IOuatPacket;
import org.dawnoftime.onceuponatown.network.PacketHandler;

public class ClientAbstractionsImpl implements ClientAbstractions {
    @Override
    public void sendToServer(IOuatPacket packet) {
        PacketHandler.CHANNEL.sendToServer(packet);
    }
}