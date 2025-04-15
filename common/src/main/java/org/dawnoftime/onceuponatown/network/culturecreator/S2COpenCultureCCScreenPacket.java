package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.client.ClientUtils;
import org.jetbrains.annotations.Nullable;

import static org.dawnoftime.onceuponatown.Ouat.COMMON;
import static org.dawnoftime.onceuponatown.Ouat.modResource;

public class S2COpenCultureCCScreenPacket extends OpenScreenPacket {
    private static final ResourceLocation ID = modResource("s2c_open_culture_screen_cc");

    private final String cultureId;

    private S2COpenCultureCCScreenPacket(String cultureId){
        super(ID.getPath());
        this.cultureId = cultureId;
    }

    public static S2COpenCultureCCScreenPacket create(@Nullable Player player, String cultureId){
        S2COpenCultureCCScreenPacket packet = new S2COpenCultureCCScreenPacket(cultureId);
        packet.saveTag(player);
        return packet;
}

    public static S2COpenCultureCCScreenPacket decode(FriendlyByteBuf buf) {
        String cultureId = buf.readUtf();
        return S2COpenCultureCCScreenPacket.create(null, cultureId);
    }

    public static S2COpenCultureCCScreenPacket decode(CompoundTag tag) {
        String cultureId = tag.getString("culture_id");
        return S2COpenCultureCCScreenPacket.create(null, cultureId);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
    }

    public void encode(CompoundTag tag) {
        tag.putString("culture_id", cultureId);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public static class Handler {
        public static void handle(S2COpenCultureCCScreenPacket packet) {
            if (COMMON.isClientSide()) {
                ClientUtils.openCultureCCScreen(packet);
            }
        }
    }

    public String getCultureId(){
        return cultureId;
    }
}