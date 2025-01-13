package org.dawnoftime.onceuponatown.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

public interface OuatPacket {
    default FriendlyByteBuf toBuf() {
        var ret = new FriendlyByteBuf(Unpooled.buffer());
        encode(ret);
        return ret;
    }

    void encode(FriendlyByteBuf buf);

    /**
     * Fabric requires a custom RL id to be sent every time the packet is sent.
     */
    ResourceLocation getFabricId();
}
