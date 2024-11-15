package org.dawnoftime.onceuponatown.client;

import org.dawnoftime.onceuponatown.network.IOuatPacket;

public interface ClientAbstractions {
    /**
     * Sends the given packet to the Server.
     *
     * @param packet C2S packet to be sent.
     */
    void sendToServer(IOuatPacket packet);

    //TODO Import all the function from forge here somehow ?!
}