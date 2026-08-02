package org.dawnoftime.onceuponatown.behavior.morale;

import net.minecraft.server.level.ServerLevel;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Combines a list of {@link MoodModifier}s into a single computed morale value.
 *
 * <p>The default construction wires up {@link HasFoodModifier} and
 * {@link HasBedModifier} — the two modifiers the engine cares about today.
 * Future slices will register seasonal modifiers, threat modifiers (raid
 * nearby), and housing-quality modifiers; they go here, in the registration
 * order they should compose in.
 *
 * <p>{@link #compute} clamps the final result into 0..100. A modifier is free
 * to push the running value past either bound — the next modifier then sees
 * that overflowed value — but the value stored back into {@link MoraleState}
 * is always clamped.
 */
public final class MoraleCalculator {

    private final List<MoodModifier> modifiers = new ArrayList<>();

    public MoraleCalculator() {
        modifiers.add(new HasFoodModifier());
        modifiers.add(new HasBedModifier());
    }

    /**
     * Apply every registered modifier in order, starting from {@code baseMorale},
     * and return the result clamped into 0..100. {@code level} is passed through
     * to modifiers that want it; none of the current defaults read it.
     */
    public int compute(UUID citizenId, Town town, int baseMorale, ServerLevel level) {
        int result = baseMorale;
        for (MoodModifier mod : modifiers) {
            result = mod.modify(citizenId, town, result, level);
        }
        return Math.max(0, Math.min(100, result));
    }

    /** Add another modifier at the end of the chain. Tests use this to inject fakes. */
    public void register(MoodModifier modifier) {
        modifiers.add(modifier);
    }

    /** The currently registered modifiers, in composition order. */
    public List<MoodModifier> modifiers() {
        return List.copyOf(modifiers);
    }
}
