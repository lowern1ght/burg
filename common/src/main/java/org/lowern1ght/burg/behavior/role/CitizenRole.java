package org.lowern1ght.burg.behavior.role;

/**
 * What a citizen does for the town.
 *
 * <p>Roles are assigned by {@link RoleAssigner} based on town needs and the citizen's existing
 * assignment. A citizen whose town has no work for them gets {@link #IDLE}, which is the default
 * for citizens the engine has not yet classified.
 *
 * <p>This enum is intentionally distinct from the entity's own {@link
 * org.lowern1ght.burg.entity.Npc.Role} (BUILDER/SETTLER). That field describes the
 * NPC's body, persisted with the entity; this one describes the citizen's work assignment in the
 * behaviour engine, recomputed every {@link RoleAssigner#update(org.lowern1ght.burg.town.Town,
 * java.util.List, RoleAssignerConfig)} cycle. The two will converge over time — a body that
 * takes the BUILDER role in the engine also belongs to the builder roster — but the slice
 * today treats them as separate so the engine's bookkeeping can evolve without touching save
 * data.
 *
 * <p>The {@link #CHIEF} value is a stub for the earned-crown trajectory (Act 5) and is never
 * assigned automatically yet; it exists in the enum so per-town role tables can be planned
 * against the final set.
 */
public enum CitizenRole {
    /** Not assigned to any task. The default state for an unclassified citizen. */
    IDLE,
    /** Places buildings (the existing builder role, scoped per town). */
    BUILDER,
    /** Lays road segments. */
    ROAD_BUILDER,
    /** Tends crops. Future behaviour; the assigner does not pick it yet. */
    FARMER,
    /** Patrols and defends. Act 4-5. */
    GUARD,
    /** Trades. Act 2. */
    MERCHANT,
    /** Speaks for the village. Act 5. */
    CHIEF
}
