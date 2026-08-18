package org.lowern1ght.burg.behavior.morale;

import net.minecraft.server.level.ServerLevel;
import org.lowern1ght.burg.town.Town;

import java.util.UUID;

/**
 * A citizen whose town has food in stock is happier than one whose town does
 * not. The first-slice rule is a flat +5; the negative case ({@code -3} for
 * an empty larder) is reserved for a future slice once {@code Town} exposes a
 * food-stock accessor.
 *
 * <p>Town does not yet have {@code getFoodStock()}; this implementation
 * assumes the town has food (optimistic default) so the modifier contributes
 * +5 to every citizen. When the food-stock API lands, swap the body for a
 * read against it. The optimistic default is documented here so a future
 * test does not silently mask a starvation regression.
 */
public final class HasFoodModifier implements MoodModifier {

    /** Optimistic default: assume the town has food. */
    private static final int DELTA = 5;

    @Override
    public int modify(UUID citizenId, Town town, int currentMorale, ServerLevel level) {
        return currentMorale + DELTA;
    }

    @Override
    public String name() {
        return "HasFood";
    }
}
