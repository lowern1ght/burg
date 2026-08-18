package org.lowern1ght.burg.behavior.role;

import org.lowern1ght.burg.entity.Npc;
import org.lowern1ght.burg.town.Town;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Decides what each citizen in a town should be doing.
 *
 * <p>The engine calls {@link #update} every so often with the current set of citizens and a
 * role quota. The assigner is idempotent — already-assigned citizens keep their roles until
 * something else reassigns them — so calling {@code update} on every tick is safe, only
 * wasteful; the engine throttles to roughly five seconds per pass.
 *
 * <p>Priority order today is the same as the prompt: {@link CitizenRole#BUILDER} first, then
 * {@link CitizenRole#ROAD_BUILDER}, then {@link CitizenRole#IDLE} for anyone left over. Future
 * phases will read the town's needs (a queued road needs a {@code ROAD_BUILDER}; a queued
 * build needs a {@code BUILDER}) and pick the role from there.
 *
 * <p>The role map is not persisted. It is recomputed every cycle from the live citizen set,
 * so a chunk unload that drops a citizen from the world is automatically reconciled on the next
 * pass.
 */
public final class RoleAssigner {

    private final Map<UUID, CitizenRole> roles = new HashMap<>();

    /**
     * Assign roles to the given citizens, leaving existing assignments in place and only
     * classifying citizens the assigner has not seen yet.
     *
     * <p>Two passes:
     * <ol>
     *   <li>Count how many citizens already hold each role. Existing assignments are sticky —
     *       this is how a manually-overridden role (see {@link #assignManually}) survives a
     *       subsequent {@code update} call.</li>
     *   <li>For every citizen without an assignment, walk the priority list and give them the
     *       first role whose quota has room. Citizens past every quota become {@link
     *       CitizenRole#IDLE}.</li>
     * </ol>
     *
     * <p>Demotion when a quota shrinks (a town loses a builder slot) is not implemented in
     * this slice — the next phase handles it once the engine starts honouring roles for intent
     * routing.
     *
     * @param town     the town whose citizens are being assigned. Currently unused at runtime
     *                 but kept on the signature for future per-town quota overrides.
     * @param citizens the citizens to consider. Citizens not in this list are forgotten on the
     *                 next call.
     * @param config   the role quotas for this pass.
     */
    public void update(Town town, List<Npc> citizens, RoleAssignerConfig config) {
        int builders = 0;
        int roadBuilders = 0;
        int farmers = 0;
        int guards = 0;
        int merchants = 0;

        // First pass: count current roles. Existing assignments stick so manual overrides and
        // sticky engine decisions survive the next update.
        for (Npc npc : citizens) {
            CitizenRole current = roles.getOrDefault(npc.getUUID(), CitizenRole.IDLE);
            switch (current) {
                case BUILDER -> builders++;
                case ROAD_BUILDER -> roadBuilders++;
                case FARMER -> farmers++;
                case GUARD -> guards++;
                case MERCHANT -> merchants++;
                case IDLE, CHIEF -> {}
            }
        }

        // Second pass: classify the unassigned, walking the priority list until every quota
        // either has its slot or has been exhausted. Citizens past every quota get IDLE.
        for (Npc npc : citizens) {
            UUID id = npc.getUUID();
            if (roles.containsKey(id)) continue;

            if (builders < config.maxBuilders()) {
                roles.put(id, CitizenRole.BUILDER);
                builders++;
            } else if (roadBuilders < config.maxRoadBuilders()) {
                roles.put(id, CitizenRole.ROAD_BUILDER);
                roadBuilders++;
            } else {
                roles.put(id, CitizenRole.IDLE);
            }
        }

        // TODO: demote excess if quota shrinks (next phase)
    }

    /**
     * The role currently assigned to a citizen, or {@link CitizenRole#IDLE} if the assigner has
     * never seen this UUID.
     */
    public CitizenRole currentRole(UUID citizenId) {
        return roles.getOrDefault(citizenId, CitizenRole.IDLE);
    }

    /**
     * Set a citizen's role directly, bypassing the assigner's quota logic. Used by future
     * code that promotes a citizen to {@link CitizenRole#CHIEF} or marks somebody as a
     * {@link CitizenRole#GUARD} regardless of quotas. The next {@link #update} call will keep
     * the assignment as long as the citizen is still in the town's citizen list.
     */
    public void assignManually(UUID citizenId, CitizenRole role) {
        roles.put(citizenId, role);
    }
}
