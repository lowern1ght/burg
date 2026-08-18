package org.lowern1ght.burg.behavior.morale;

import org.lowern1ght.burg.entity.Npc;
import org.lowern1ght.burg.town.Town;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The per-citizen morale store, kept in the engine scope.
 *
 * <p>A citizen without a recorded entry reads as the {@code DEFAULT} (a 50,
 * i.e. {@link MoraleLevel#NEUTRAL}). {@link #adjust(UUID, int)} and
 * {@link #set(UUID, int)} clamp to the 0..100 scale, so a hot chain of small
 * adjustments cannot push a citizen off either end of the scale.
 *
 * <p>Town-level morale is not stored; it is computed on demand from the
 * citizen average via {@link #averageForTown(List)}. The engine passes in the
 * current free-citizen list (resolved from {@code NpcSupplier.freeCitizens});
 * Town does not expose a {@code getCitizens()} method and the foundation
 * phase is not adding one.
 */
public final class MoraleState {

    /** The neutral starting value for a citizen the engine has not yet classified. */
    public static final int DEFAULT = 50;

    private final Map<UUID, Integer> morale = new HashMap<>();

    /** The citizen's morale, or {@link #DEFAULT} if the engine has not seen this UUID yet. */
    public int valueFor(UUID citizenId) {
        return morale.getOrDefault(citizenId, DEFAULT);
    }

    /** The bucket the citizen's morale currently sits in. */
    public MoraleLevel levelFor(UUID citizenId) {
        return MoraleLevel.fromValue(valueFor(citizenId));
    }

    /**
     * Add {@code delta} to a citizen's morale, clamping into 0..100. A negative
     * delta can take a citizen down to 0; a positive one can take them up to 100.
     * Deltas past either bound are absorbed at the bound.
     */
    public void adjust(UUID citizenId, int delta) {
        int current = valueFor(citizenId);
        int newValue = Math.max(0, Math.min(100, current + delta));
        morale.put(citizenId, newValue);
    }

    /** Overwrite the citizen's morale, clamped into 0..100. */
    public void set(UUID citizenId, int value) {
        morale.put(citizenId, Math.max(0, Math.min(100, value)));
    }

    /**
     * Mean morale across the supplied citizens. Returns the {@link #DEFAULT} (50)
     * when the list is empty, so a freshly-spawned town is NEUTRAL until the
     * engine has something to average over.
     *
     * <p>The engine resolves the citizen list via {@code NpcSupplier.freeCitizens(town)};
     * this method intentionally takes the resolved list rather than {@link Town},
     * because Town does not expose a {@code getCitizens()} method and this slice
     * does not add one.
     */
    public float averageForTown(List<Npc> citizens) {
        if (citizens.isEmpty()) return 50.0f;
        int sum = 0;
        for (Npc n : citizens) {
            sum += valueFor(n.getUUID());
        }
        return sum / (float) citizens.size();
    }
}
