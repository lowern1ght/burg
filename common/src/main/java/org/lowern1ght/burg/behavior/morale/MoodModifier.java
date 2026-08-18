package org.lowern1ght.burg.behavior.morale;

import net.minecraft.server.level.ServerLevel;
import org.lowern1ght.burg.town.Town;

import java.util.UUID;

/**
 * A single term in a citizen's morale equation.
 *
 * <p>A modifier reads whatever it needs (the citizen's home, the town's food
 * stock, the season) and returns the modified morale value. Modifiers run in
 * registration order inside {@link MoraleCalculator#compute}; the calculator
 * itself clamps the final result into 0..100 but does not clamp between
 * modifiers, so a modifier that wants to overflow the scale can do so and
 * the next modifier will start from the overflowed value.
 *
 * <p>The {@link ServerLevel} parameter is part of the contract even though no
 * current modifier reads it: Town-level data lookups (food, beds, housing)
 * live on Town itself, not the level, but a future modifier that wants the
 * calendar or the weather can take the level without breaking the signature.
 */
public interface MoodModifier {

    /**
     * Apply this modifier to the current morale value and return the result.
     * Positive numbers raise morale; negative numbers lower it. The caller is
     * responsible for clamping the final result; modifiers themselves do not.
     */
    int modify(UUID citizenId, Town town, int currentMorale, ServerLevel level);

    /** Stable name used in logs and tests. */
    String name();
}
