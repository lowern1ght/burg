package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.item.CultureCreatorItem;
import org.dawnoftime.onceuponatown.network.OuatPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class OpenScreenPacket implements OuatPacket {
    private final String id;

    public OpenScreenPacket(String id) {
        this.id = id;
    }

    protected void saveTag(@Nullable Player player) {
        if (player != null) {
            ItemStack stack = player.getItemInHand(player.getUsedItemHand());
            if (!stack.isEmpty() && stack.getItem() instanceof CultureCreatorItem) {
                CompoundTag tag = stack.getOrCreateTag();
                CompoundTag packetTag = new CompoundTag();
                packetTag.putString("id", id);
                this.encode(packetTag);
                tag.put("ouat_packet", packetTag);
            }
        }
    }

    public abstract void encode(CompoundTag tag);

    public static @Nullable CompoundTag getPacketTag(@NotNull CompoundTag tag) {
        try {
            if (tag.contains("ouat_packet")) {
                return tag.getCompound("ouat_packet");
            }
        } catch (Exception e) {
            Ouat.LOG.debug("Trying to load the screen to open, but the packet tag is malformed : {}", e.toString());
        }
        return null;
    }

    public static @Nullable String decodePacketId(@NotNull CompoundTag tag) {
        try {
            CompoundTag ouatTag =  getPacketTag(tag);
            if (ouatTag != null) {
                return ouatTag.getString("id");
            }
        } catch (Exception e) {
            Ouat.LOG.debug("Trying to load the screen to open, but the packet tag is missing information : {}", e.toString());
        }
        return null;
    }
}
