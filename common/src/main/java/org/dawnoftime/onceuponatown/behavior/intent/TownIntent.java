package org.dawnoftime.onceuponatown.behavior.intent;

import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.behavior.role.CitizenRole;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.Set;

/**
 * A declaration of what a town wants its citizens to do.
 *
 * <p>Intents are the <i>what</i> of the behaviour engine. They are queued by the town itself
 * (or, in later phases, by external triggers — quests, defence events, player commands) and
 * the scheduler sorts them, prunes the ones that no longer apply, and hands the survivors to
 * the citizens who can carry them out.
 *
 * <p>An intent is not a plan of execution. It carries only enough state to decide whether it
 * still describes something the town wants, what it would cost, and how aggressively it
 * should be picked over its siblings. The mapping to concrete per-citizen work is the
 * {@link org.dawnoftime.onceuponatown.behavior.task.CitizenTask} layer.
 *
 * <p>The sealed {@code permits} list is the full set of intent kinds the engine knows about.
 * Adding a new intent kind is a deliberate two-step: declare the record, then extend the
 * permits list. In-package, the switch in {@link IntentScheduler} resolves kind-specific
 * behaviour without runtime type checks.
 */
public sealed interface TownIntent
        permits BuildIntent, UpgradeIntent, ExpandIntent,
                TradeIntent, DefendIntent, RecallIntent {

    /**
     * A stable identifier for this intent instance. The scheduler uses this to look the
     * intent up in its index. Two intents with the same id can coexist (the scheduler keeps
     * the active list ordered and the index is a shortcut), but cancellation by id targets
     * the first match.
     */
    ResourceLocation id();

    /** The town the intent belongs to. Never null. */
    Town town();

    /** Higher numbers are picked first. Ties are broken by insertion order. */
    int basePriority();

    /**
     * True when the intent can be executed right now — there are free citizens, the cost is
     * payable, the prerequisites are met. The scheduler queries this each tick and assigns
     * the intent to a citizen when it returns true.
     */
    boolean canResolve(Town town);

    /**
     * True when the intent still describes something the town wants. False means the intent
     * should be dropped on the next tick: the building is already up, the war is over, the
     * settler is home, whatever the per-kind rule says.
     */
    boolean isStillValid(Town town);

    /** What executing this intent would cost the town. {@link IntentCost#empty()} for free. */
    IntentCost cost();

    /**
     * Citizens with these roles can fulfill this intent. Empty set means any role.
     * Specific intents override to declare role requirements.
     */
    default Set<CitizenRole> requiredRoles() {
        return Set.of();
    }
}
