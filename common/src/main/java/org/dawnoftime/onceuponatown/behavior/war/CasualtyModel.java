package org.dawnoftime.onceuponatown.behavior.war;

import org.dawnoftime.onceuponatown.entity.Npc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-NPC death and injury accounting for a battle.
 *
 * <p>Casualty data is kept in memory only (wars are short-lived — see
 * {@link BattleStateMachine} for the persistence decision). Two views:
 * <ul>
 *   <li>{@link #isAlive(UUID)} — asked by the state machine when deciding
 *       whether a squad still has combatants.</li>
 *   <li>{@link #averageHealth(Squad)} — the 0–1 fraction the state machine
 *       actually reads. A dead NPC counts as 0; an NPC with injury
 *       severity ≥ 6 counts as 0; an NPC with severity 3–5 counts as 0.5
 *       (half-casualty); everyone else counts as 1.</li>
 * </ul>
 *
 * <p>Injury severity is intentionally an additive integer. A single hit
 * of severity 4 and a second hit of severity 2 both contribute, so a
 * squad that took two glancing hits reads as a single serious hit. The
 * math is what the state machine asks for; if a richer model lands
 * later (per-body-part injuries, morale effects, etc.) it will replace
 * this.
 */
public final class CasualtyModel {
    private final Set<UUID> dead = new HashSet<>();
    private final Map<UUID, Integer> injuries = new HashMap<>();

    public boolean isAlive(UUID npcId) {
        return npcId != null && !dead.contains(npcId);
    }

    public void markDead(UUID npcId) {
        if (npcId != null) dead.add(npcId);
    }

    public void markInjured(UUID npcId, int severity) {
        if (npcId == null || severity <= 0) return;
        injuries.merge(npcId, severity, Integer::sum);
    }

    public int injurySeverity(UUID npcId) {
        return injuries.getOrDefault(npcId, 0);
    }

    /**
     * Average health of the squad, weighted 0–1. Each member contributes
     * 1.0 if healthy, 0.5 if injured (severity 3–5), 0.0 if severely
     * injured (severity ≥ 6) or dead. An empty squad returns 0.0 — a
     * state machine reading from an empty squad should treat that as
     * routed.
     */
    public float averageHealth(Squad squad) {
        if (squad.members().isEmpty()) return 0f;
        float aliveCount = 0f;
        for (Npc n : squad.members()) {
            UUID id = n.getUUID();
            if (dead.contains(id)) continue;
            int sev = injuries.getOrDefault(id, 0);
            if (sev < 3) aliveCount += 1f;
            else if (sev < 6) aliveCount += 0.5f;
            // else severely injured counts as 0
        }
        return aliveCount / (float) squad.members().size();
    }

    /**
     * The set of NPCs marked dead during this battle. Returned as a
     * defensive copy so callers can't mutate the model's internal set.
     */
    public Set<UUID> deadMembers() {
        return Set.copyOf(dead);
    }
}
