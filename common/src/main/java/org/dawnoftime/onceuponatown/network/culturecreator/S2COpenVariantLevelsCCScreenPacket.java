package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.client.ClientUtils;

import static org.dawnoftime.onceuponatown.Ouat.*;

public class S2COpenVariantLevelsCCScreenPacket extends OpenScreenPacket {
    private static final ResourceLocation ID = modResource("s2c_open_variant_levels_screen_cc");

    private final String cultureId;
    private final String buildingId;
    private final String variantId;
    private final int numberOfLevels;

    private S2COpenVariantLevelsCCScreenPacket(String cultureId, String buildingId, String variantId, int numberOfLevels){
        super(ID.getPath());
        this.cultureId = cultureId;
        this.buildingId = buildingId;
        this.variantId = variantId;
        this.numberOfLevels = numberOfLevels;
    }

    public static S2COpenVariantLevelsCCScreenPacket create(Player player, String cultureId, String buildingId, String variantId, int numberOfLevels){
        S2COpenVariantLevelsCCScreenPacket packet = new S2COpenVariantLevelsCCScreenPacket(cultureId, buildingId, variantId, numberOfLevels);
        packet.saveTag(player);
        return packet;
    }

    public static S2COpenVariantLevelsCCScreenPacket decode(FriendlyByteBuf buf) {
        String cultureId = buf.readUtf();
        String buildingId = buf.readUtf();
        String variantId = buf.readUtf();
        byte numberOfLevels = buf.readByte();
        return new S2COpenVariantLevelsCCScreenPacket(cultureId, buildingId, variantId, numberOfLevels);
    }

    public static S2COpenVariantLevelsCCScreenPacket decode(CompoundTag tag) {
        String cultureId = tag.getString("culture_id");
        String buildingId = tag.getString("building_id");
        String level = tag.getString("variant_id");
        byte numberOfLevels = tag.getByte("number_of_levels");
        return S2COpenVariantLevelsCCScreenPacket.create(null, cultureId, buildingId, level, numberOfLevels);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
        buf.writeUtf(buildingId);
        buf.writeUtf(variantId);
        buf.writeByte(numberOfLevels);
    }

    public void encode(CompoundTag tag) {
        tag.putString("culture_id", cultureId);
        tag.putString("building_id", buildingId);
        tag.putString("variant_id", variantId);
        tag.putInt("number_of_levels", numberOfLevels);
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

    public int getNumberOfLevels() {
        return numberOfLevels;
    }

    public static class Handler {
        public static void handle(S2COpenVariantLevelsCCScreenPacket packet) {
            if (COMMON.isClientSide()) {
                ClientUtils.openVariantLevelsCCScreen(packet);
            }
        }
    }
}