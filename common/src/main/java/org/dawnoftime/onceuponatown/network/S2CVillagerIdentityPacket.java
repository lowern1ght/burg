package org.dawnoftime.onceuponatown.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.client.CitizenMembershipClientState;

import java.util.UUID;

/**
 * Tells the client that one villager is (or is no longer) a town's.
 *
 * <p>The whole payload is one UUID and one bit, and that is the point. Everything else the
 * client needs about a citizen — its name, its face, its clothing tint — is a pure function
 * of that UUID, so publishing membership publishes the citizen. The alternative was syncing
 * the attachment itself, which NeoForge 21.1.77 cannot do: there is no {@code sync()} on
 * {@code AttachmentType.Builder}, checked against the jar.
 *
 * <p>It does not matter whether this arrives before or after the entity's own spawn packet.
 * The handler only records a UUID in a set; the renderer reads that set on whatever frame it
 * gets to. Ordering only becomes a problem for a design that has to attach the fact to a live
 * entity, which is one more reason not to key this by entity id.
 */
public record S2CVillagerIdentityPacket(UUID villager, boolean member)
        implements CustomPacketPayload {

    public static final Type<S2CVillagerIdentityPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "s2c_villager_identity"));

    public static final StreamCodec<FriendlyByteBuf, S2CVillagerIdentityPacket> STREAM_CODEC =
        StreamCodec.of(S2CVillagerIdentityPacket::write, S2CVillagerIdentityPacket::read);

    private static S2CVillagerIdentityPacket read(FriendlyByteBuf buf) {
        return new S2CVillagerIdentityPacket(buf.readUUID(), buf.readBoolean());
    }

    private static void write(FriendlyByteBuf buf, S2CVillagerIdentityPacket packet) {
        buf.writeUUID(packet.villager());
        buf.writeBoolean(packet.member());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CVillagerIdentityPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
            CitizenMembershipClientState.set(packet.villager(), packet.member()));
    }
}
