package org.lowern1ght.burg.town;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.lowern1ght.burg.domain.settlement.ConstructionIntent;

/**
 * A single entry in the player construction queue.
 * Either a new building placement or a visual+stat upgrade of a placed building.
 */
public sealed interface QueueEntry permits QueueEntry.NewBuild, QueueEntry.Upgrade {

    long entryId();
    String defId();

    /** A new building to construct from a connection point. */
    record NewBuild(long entryId, String defId) implements QueueEntry {}

    /**
     * An upgrade task for a building already placed in the world.
     * fromLevel is the building's upgrade level when this task was enqueued.
     */
    record Upgrade(long entryId, String defId, BlockPos buildingWorldPos, int fromLevel) implements QueueEntry {}

    static CompoundTag serialize(QueueEntry entry) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("EntryId", entry.entryId());
        if (entry instanceof Upgrade u) {
            tag.putString("Type", "upgrade");
            tag.putString("DefId", u.defId());
            tag.putLong("BuildingWorldPos", u.buildingWorldPos().asLong());
            tag.putInt("FromLevel", u.fromLevel());
        } else if (entry instanceof NewBuild nb) {
            tag.putString("Type", "new_build");
            tag.putString("DefId", nb.defId());
        }
        return tag;
    }

    static QueueEntry deserialize(CompoundTag tag) {
        long entryId = tag.contains("EntryId") ? tag.getLong("EntryId") : 0L;
        String defId = tag.getString("DefId");
        if ("upgrade".equals(tag.getString("Type"))) {
            return new Upgrade(entryId, defId, BlockPos.of(tag.getLong("BuildingWorldPos")), tag.getInt("FromLevel"));
        }
        return new NewBuild(entryId, defId);
    }

    /**
     * MC-typed → domain boundary. Used by the {@code Town} facade when a
     * legacy queue entry needs to live in the {@link
     * org.lowern1ght.burg.domain.settlement.ConstructionQueue} value
     * object (e.g. {@code fromNbt} load, refund cost lookup). The
     * {@code BlockPos} is reduced to its long handle and stringified —
     * the same discipline {@code ConstructionIntent.Upgrade} uses
     * internally to stay Minecraft-free.
     */
    static ConstructionIntent toIntent(QueueEntry entry) {
        if (entry instanceof Upgrade u) {
            return new ConstructionIntent.Upgrade(
                u.entryId(),
                u.defId(),
                Long.toString(u.buildingWorldPos().asLong()),
                u.fromLevel());
        }
        return new ConstructionIntent.NewBuild(entry.entryId(), entry.defId());
    }

    /**
     * Domain → MC-typed boundary. The inverse of {@link #toIntent}.
     * Used wherever a Minecraft-aware consumer (NBT serialize, the
     * {@code TownHubDataBuilder} S2C packet, the {@code SimpleStateMachine}
     * builder NPC) needs a {@link QueueEntry} for a {@link
     * ConstructionIntent}. Stringified {@code worldPosKey} decodes back
     * to a {@code BlockPos} via {@code BlockPos.of(Long.parseLong(...))}.
     */
    static QueueEntry fromIntent(ConstructionIntent intent) {
        if (intent instanceof ConstructionIntent.Upgrade u) {
            return new Upgrade(
                u.entryId(),
                u.buildingDefId(),
                BlockPos.of(Long.parseLong(u.worldPosKey())),
                u.fromLevel());
        }
        return new NewBuild(intent.entryId(), intent.buildingDefId());
    }
}
