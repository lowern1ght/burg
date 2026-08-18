package org.lowern1ght.burg.behavior.intent;

import net.minecraft.resources.ResourceLocation;
import org.lowern1ght.burg.behavior.role.CitizenRole;
import org.lowern1ght.burg.behavior.role.RoleAssigner;
import org.lowern1ght.burg.entity.Npc;
import org.lowern1ght.burg.town.Town;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Holds the live intents for every town, prunes the ones that are no longer wanted, and
 * hands the survivors to the citizens who can carry them out.
 *
 * <p>The scheduler is deliberately small. It does not know how to execute any intent — that
 * is the task layer's job — and it does not know how to find citizens — that is the
 * supplied {@link NpcSupplier}'s job. What it does is:
 *
 * <ol>
 *   <li>Keep an {@code active} list per town, plus a secondary index keyed by id.</li>
 *   <li>Each tick, drop intents whose {@link TownIntent#isStillValid} is false.</li>
 *   <li>Sort the survivors by descending priority.</li>
 *   <li>For each free citizen, pick the highest-priority intent that resolves and bind the
 *       two together. The actual binding step is delegated to the {@link NpcSupplier} —
 *       this class only logs the pairing so tests can inspect it.</li>
 * </ol>
 *
 * <p>Binding to a {@link org.lowern1ght.burg.behavior.task.CitizenTask} happens at a
 * higher layer (see {@code BehaviorEngine}). The scheduler stops at "these two belong
 * together now".
 */
public final class IntentScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IntentScheduler.class);

    private final NpcSupplier npcSupplier;
    private final RoleAssigner roleAssigner;
    private final boolean legacyRoleFallback;

    /** All intents currently considered by the scheduler, grouped by town. Insertion order kept. */
    private final Map<Town, List<TownIntent>> active = new LinkedHashMap<>();

    /** Shortcut from {@link TownIntent#id()} to the intent. Last-written-wins on collisions. */
    private final Map<ResourceLocation, TownIntent> index = new HashMap<>();

    /** Pairings produced by the latest tick: intent id -> citizen uuid. The engine feeds assignments off this. */
    private final Map<ResourceLocation, java.util.UUID> pendingAssignments = new HashMap<>();

    public IntentScheduler(NpcSupplier npcSupplier) {
        this(npcSupplier, new RoleAssigner(), true);
    }

    public IntentScheduler(NpcSupplier npcSupplier, RoleAssigner roleAssigner) {
        this(npcSupplier, roleAssigner, false);
    }

    private IntentScheduler(NpcSupplier npcSupplier, RoleAssigner roleAssigner,
                            boolean legacyRoleFallback) {
        this.npcSupplier = npcSupplier;
        this.roleAssigner = roleAssigner;
        this.legacyRoleFallback = legacyRoleFallback;
    }

    public RoleAssigner roleAssigner() {
        return roleAssigner;
    }

    // --- enqueue / cancel ---------------------------------------------------------------

    public void enqueue(TownIntent intent) {
        active.computeIfAbsent(intent.town(), t -> new ArrayList<>()).add(intent);
        index.put(intent.id(), intent);
    }

    /** Removes the first intent with this id from its town. No-op if not found. */
    public void cancel(ResourceLocation intentId) {
        TownIntent intent = index.remove(intentId);
        if (intent == null) return;
        List<TownIntent> townList = active.get(intent.town());
        if (townList != null) {
            townList.remove(intent);
            if (townList.isEmpty()) active.remove(intent.town());
        }
        pendingAssignments.remove(intentId);
    }

    /** A read-only view of the intents currently active for this town, in insertion order. */
    public List<TownIntent> activeIntents(Town town) {
        List<TownIntent> list = active.get(town);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    /** The pairings produced by the latest tick. Cleared by the next tick. Read-only. */
    public Map<ResourceLocation, java.util.UUID> peekPairings() {
        return Collections.unmodifiableMap(pendingAssignments);
    }

    /** The pairings produced by the latest tick. Cleared on drain. */
    public Map<ResourceLocation, java.util.UUID> drainPendingAssignments() {
        Map<ResourceLocation, java.util.UUID> copy = new HashMap<>(pendingAssignments);
        pendingAssignments.clear();
        return copy;
    }

    /** Returns the intent with this id, if it is currently active. */
    public Optional<TownIntent> find(ResourceLocation intentId) {
        return Optional.ofNullable(index.get(intentId));
    }

    // --- tick ---------------------------------------------------------------------------

    /**
     * Drives the scheduler one tick. Drops stale intents, sorts the rest by priority,
     * and pairs each free citizen with the highest-priority intent that resolves.
     *
     * <p>This method does not construct {@link org.lowern1ght.burg.behavior.task.CitizenTask}
     * instances — that is the engine's job. It just records the pairings in
     * {@link #pendingAssignments} for the engine to read.
     */
    public void onTick(TickContext ctx) {
        pendingAssignments.clear();

        // 1) Drop stale intents and sort the survivors by priority desc.
        for (List<TownIntent> townList : active.values()) {
            townList.removeIf(intent -> !intent.isStillValid(intent.town()));
            townList.sort(Comparator.comparingInt(TownIntent::basePriority).reversed());
        }

        // 2) For each town, pair free citizens with intents that can resolve.
        for (Map.Entry<Town, List<TownIntent>> entry : active.entrySet()) {
            Town town = entry.getKey();
            List<TownIntent> intents = entry.getValue();
            List<Npc> available = new ArrayList<>(npcSupplier.freeCitizens(town));
            if (available.isEmpty()) continue;

            for (TownIntent intent : intents) {
                if (available.isEmpty()) break;
                if (!intent.canResolve(town)) continue;

                Set<CitizenRole> requiredRoles = intent.requiredRoles();
                Npc matched = null;
                for (Npc npc : available) {
                    if (matchesRole(town, npc, requiredRoles)) {
                        matched = npc;
                        break;
                    }
                }
                if (matched == null) continue;

                available.remove(matched);
                pendingAssignments.put(intent.id(), matched.getUUID());
                LOGGER.debug("[BEHAVIOR] intent {} -> npc {}", intent.id(), matched.getUUID());
            }
        }

        // 3) Clean up towns whose intent lists emptied out.
        active.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    private boolean matchesRole(Town town, Npc npc, Set<CitizenRole> requiredRoles) {
        if (requiredRoles.isEmpty()) return true;
        CitizenRole role = roleAssigner.currentRole(npc.getUUID());
        if (legacyRoleFallback && role == CitizenRole.IDLE) role = town.roleOf(npc.getUUID());
        return requiredRoles.contains(role);
    }

    // --- test seams ---------------------------------------------------------------------

    /** The context the scheduler's tick needs. See TaskContext for the shape. */
    public interface TickContext {
        /** World time of the current tick. */
        long gameTick();
    }
}
