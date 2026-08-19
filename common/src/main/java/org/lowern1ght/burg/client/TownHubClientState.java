package org.lowern1ght.burg.client;

import net.minecraft.core.BlockPos;
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

    // ADR-0022 wiring — acts 0–3 still use the legacy menu flow above;
    // act-4 SUPPLY-mode opens {@link org.lowern1ght.burg.client.gui.TownHubScreenV2}
    // directly. The server sets this when the town's hubMode() == SUPPLY,
    // the client reads it on the next tick and opens the V2 screen, then
    // nulls it (one-shot). The anchor position is informational for the
    // first wire — the engine reads it for nothing today.
    public static @Nullable BlockPos openTownHubV2;
}
