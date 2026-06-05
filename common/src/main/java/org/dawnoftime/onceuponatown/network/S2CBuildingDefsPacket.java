package org.dawnoftime.onceuponatown.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.ClientBuildingDefsRegistry;

public record S2CBuildingDefsPacket(CompoundTag data) {
    public static final ResourceLocation ID = Ouat.modResource("s2c_building_defs");

    public static S2CBuildingDefsPacket decode(FriendlyByteBuf buf) {
        return new S2CBuildingDefsPacket(buf.readNbt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    public static class Handler {
        public static void handle(S2CBuildingDefsPacket packet) {
            net.minecraft.client.Minecraft.getInstance().execute(() ->
                ClientBuildingDefsRegistry.setFromNbt(packet.data()));
        }
    }
}
