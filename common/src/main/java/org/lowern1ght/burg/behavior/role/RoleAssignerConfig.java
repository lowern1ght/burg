package org.lowern1ght.burg.behavior.role;

/**
 * Quotas the {@link RoleAssigner} uses when distributing roles across a town's citizens.
 *
 * <p>Each field is the maximum number of citizens that may hold that role at once; the assigner
 * stops handing out a role once its quota is full and gives unassigned citizens the next
 * priority role, falling back to {@link CitizenRole#IDLE}.
 *
 * <p>{@link #defaults()} matches the current town setup — one or two builders depending on era,
 * one road builder when roads are being planned, none of the late-game roles until the acts
 * they belong to are implemented. Future phases will derive this from the town's era, town
 * level and other inputs.
 */
public record RoleAssignerConfig(
    int maxBuilders,
    int maxRoadBuilders,
    int maxFarmers,
    int maxGuards,
    int maxMerchants
) {
    /**
     * The role quotas used when no town-specific configuration is supplied.
     *
     * <p>Two builders match a town that has finished its first era transition; one road builder
     * is enough to lay down a road at a time. The other roles stay at zero until the acts that
     * need them land.
     */
    public static RoleAssignerConfig defaults() {
        return new RoleAssignerConfig(2, 1, 0, 0, 0);
    }
}
