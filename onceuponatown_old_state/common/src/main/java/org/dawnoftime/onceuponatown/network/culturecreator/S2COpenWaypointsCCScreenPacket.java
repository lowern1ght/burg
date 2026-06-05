package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.client.ClientUtils;

import static org.dawnoftime.onceuponatown.Ouat.*;

public class S2COpenWaypointsCCScreenPacket extends OpenScreenPacket {
    private static final ResourceLocation ID = modResource("s2c_open_variant_level_screen_cc");

    private final String cultureId;
    private final String buildingId;
    private final String variantId;
    private final int level;

    private S2COpenWaypointsCCScreenPacket(String cultureId, String buildingId, String variantId, int level) {
        super(ID.getPath());
        this.cultureId = cultureId;
        this.buildingId = buildingId;
        this.variantId = variantId;
        this.level = level;
    }

    public static S2COpenWaypointsCCScreenPacket create(String cultureId, String buildingId, String variantId, int level) {
        return new S2COpenWaypointsCCScreenPacket(cultureId, buildingId, variantId, level);
    }

    public static S2COpenWaypointsCCScreenPacket decode(FriendlyByteBuf buf) {
        String cultureId = buf.readUtf();
        String buildingId = buf.readUtf();
        String variantId = buf.readUtf();
        byte level = buf.readByte();
        return new S2COpenWaypointsCCScreenPacket(cultureId, buildingId, variantId, level);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
        buf.writeUtf(buildingId);
        buf.writeUtf(variantId);
        buf.writeByte(level);
    }

    public static S2COpenWaypointsCCScreenPacket decode(CompoundTag tag) {
        String cultureId = tag.getString("culture_id");
        String buildingId = tag.getString("building_id");
        String level = tag.getString("variant_id");
        byte numberOfLevels = tag.getByte("level");
        return S2COpenWaypointsCCScreenPacket.create(cultureId, buildingId, level, numberOfLevels);
    }

    public void encode(CompoundTag tag) {
        tag.putString("culture_id", cultureId);
        tag.putString("building_id", buildingId);
        tag.putString("variant_id", variantId);
        tag.putInt("level", level);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public String getCultureId() {
        return cultureId;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public String getVariantId() {
        return variantId;
    }

    public int getLevel() {
        return level;
    }

    public static class Handler {
        public static void handle(S2COpenWaypointsCCScreenPacket packet) {
            if (COMMON.isClientSide()) {
                ClientUtils.openWaypointsCCScreen(packet);
            }
        }
    }
}