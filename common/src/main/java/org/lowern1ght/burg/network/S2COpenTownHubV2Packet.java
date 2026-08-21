package org.lowern1ght.burg.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.client.TownHubClientState;

/**
 * The act-4 SUPPLY-mode open-gateway. The server sends this so the
 * client opens {@code org.lowern1ght.burg.client.gui.TownHubScreenV2}
 * directly, bypassing the legacy {@code TownHubMenu}/TownHubScreen
 * flow that ships with the CONSTRUCTION-mode CONSTRUCTION lump.
 *
 * @param anchorPos world position of the town anchor the client should centre on; never null
 *
 * <p>Carries only the anchor position for now — the wire-format
 * intent list is the act-4 follow-up PR. The client opens the V2
 * screen with an empty {@link org.lowern1ght.burg.settlement.ui.SupplyIntentList}
 * ({@code org.lowern1ght.burg.client.gui.TownHubScreenV2#withEmptyIntent}),
 * which renders the
 * {@link org.lowern1ght.burg.settlement.ui.SupplyIntentListWidget#NO_INTENT_KEY}
 * placeholder.
 *
 * <p>The class lives in {@code common} so the server-side
 * {@link org.lowern1ght.burg.block.TownAnchorBlock#useWithoutItem}
 * can send it. The handle sets a static field on
 * {@link TownHubClientState}; the actual {@code setScreen(...)} call
 * lives on the client side (it needs {@code Minecraft.getInstance()},
 * which is not on the bare-JVM classpath).
 */
public record S2COpenTownHubV2Packet(BlockPos anchorPos) implements CustomPacketPayload {

    public static final Type<S2COpenTownHubV2Packet> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "s2c_open_town_hub_v2"));

    public static final StreamCodec<FriendlyByteBuf, S2COpenTownHubV2Packet> STREAM_CODEC =
        StreamCodec.of(S2COpenTownHubV2Packet::write, S2COpenTownHubV2Packet::read);

    private static S2COpenTownHubV2Packet read(FriendlyByteBuf buf) {
        return new S2COpenTownHubV2Packet(buf.readBlockPos());
    }

    private static void write(FriendlyByteBuf buf, S2COpenTownHubV2Packet packet) {
        buf.writeBlockPos(packet.anchorPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2COpenTownHubV2Packet packet, IPayloadContext context) {
        context.enqueueWork(() ->
            TownHubClientState.openTownHubV2 = packet.anchorPos());
    }
}
