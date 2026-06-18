package org.dawnoftime.onceuponatown.client;

import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

public class TownHubClientState {
    // Full hub snapshot -- sent once when player opens the hub (initial load only).
    public static @Nullable CompoundTag pendingHubData;

    // Targeted delta packets -- received while hub is open.
    public static @Nullable CompoundTag pendingStockUpdate;
    public static @Nullable CompoundTag pendingBuildingList;
    public static @Nullable CompoundTag pendingQuestUpdate;
    public static @Nullable CompoundTag pendingEraUpdate;
    public static @Nullable CompoundTag pendingCitizenUpdate;
    public static @Nullable CompoundTag pendingLogEntry;
}
