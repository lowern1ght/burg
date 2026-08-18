package org.lowern1ght.burg.domain.settlement;

import java.util.Objects;

/**
 * A single player-construction intent — either a new building to be placed
 * or an upgrade task for a building already in the world. The fourth
 * carved value object in the Settlement bounded context (after
 * {@code Standing} / {@code Acquisition} / {@code StandingBook} in ADR-0009
 * and {@code ItemId} / {@code StockLedger} in ADR-0010), and the
 * strangler-side analogue of {@code town.QueueEntry} (the
 * {@code NewBuild} / {@code Upgrade} union on the {@code Town}
 * aggregate).
 *
 * <p>Sealed to keep the two-case union exhaustive: adding a third intent
 * shape is a deliberate domain decision (an ADR, a new test, a new
 * scenario) rather than an accidental spread across call sites. The two
 * permitted cases mirror {@code QueueEntry} one-for-one — same
 * {@code entryId} + {@code buildingDefId} fields, same monotonic-id
 * order preserved by the queue that owns them. What differs is the
 * Minecraft surface: {@code Upgrade} carries its world coordinate as the
 * stringified form of {@code BlockPos.asLong()} (a primitive), not as a
 * {@code BlockPos} reference. The reverse — {@code BlockPos.of(Long.parseLong(worldPosKey))} —
 * is the boundary conversion the {@code Town} facade performs when an
 * intent needs to resolve back to a world coordinate.
 *
 * <p>No Minecraft imports. {@code ItemId} is the canonical identity for
 * items (ADR-0010); {@code ConstructionIntent} is the canonical identity
 * for queued construction work (ADR-0011). Both share the same
 * Minecraft-free discipline; both are testable on a bare JVM.
 */
public sealed interface ConstructionIntent
    permits ConstructionIntent.NewBuild, ConstructionIntent.Upgrade {

    /** Stable identifier minted at enqueue time; survives a world reload. */
    long entryId();

    /** Canonical {@code building_def_id} the queue entry resolves to. */
    String buildingDefId();

    /**
     * A new building to construct from a free connection point. No world
     * coordinate — the builder NPC picks the slot when the entry reaches
     * the head of the queue.
     */
    record NewBuild(long entryId, String buildingDefId) implements ConstructionIntent {
        public NewBuild {
            Objects.requireNonNull(buildingDefId, "buildingDefId");
        }
    }

    /**
     * An upgrade task for a building already placed in the world.
     * {@code worldPosKey} is the stringified form of {@code BlockPos.asLong()}:
     * a primitive handle that round-trips through {@code Long.parseLong}
     * and {@code BlockPos.of} at the {@code Town} facade edge. We never
     * accept a {@code BlockPos} in domain signatures.
     * {@code fromLevel} is the building's upgrade level when this task was
     * enqueued.
     */
    record Upgrade(
        long entryId,
        String buildingDefId,
        String worldPosKey,
        int fromLevel
    ) implements ConstructionIntent {
        public Upgrade {
            Objects.requireNonNull(buildingDefId, "buildingDefId");
            Objects.requireNonNull(worldPosKey, "worldPosKey");
        }
    }
}
