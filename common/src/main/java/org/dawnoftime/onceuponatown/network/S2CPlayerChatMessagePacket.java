package org.dawnoftime.onceuponatown.network;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import static org.dawnoftime.onceuponatown.Ouat.modResource;

public class S2CPlayerChatMessagePacket implements IOuatPacket {
    private static final ResourceLocation ID = modResource("s2c_player_chat_message");
    private final Component message;

    private S2CPlayerChatMessagePacket(Component message) {
        this.message = message;
    }

    public static S2CPlayerChatMessagePacket create(Component message){
        return new S2CPlayerChatMessagePacket(message);
    }

    public static S2CPlayerChatMessagePacket decode(FriendlyByteBuf buf) {
        return new S2CPlayerChatMessagePacket(buf.readComponent());
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeComponent(message);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public static class Handler {
        public static void handle(S2CPlayerChatMessagePacket packet) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(packet.message);
            }
        }
    }
}