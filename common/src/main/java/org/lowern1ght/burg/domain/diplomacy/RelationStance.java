package org.lowern1ght.burg.domain.diplomacy;

/**
 * The stance of one realm toward another — the relation set from VISION
 * §"The immediate architecture consequence" ({@code relations: to other
 * realms (war / truce / alliance / tribute)}) plus {@link #NEUTRAL} as
 * the never-interacted default (diplomacy README §"Decisions" 2).
 *
 * <p>Name mapping to the existing town-scale engine
 * ({@code behavior.diplomacy.DiplomaticStatus}, which this seed does
 * <em>not</em> replace — the adapter comes later, at the behavior edge):
 *
 * <table border="1">
 *   <caption>Legacy town-scale status to realm-scale stance</caption>
 *   <tr><th>DiplomaticStatus (behavior)</th><th>RelationStance (domain)</th></tr>
 *   <tr><td>{@code AT_WAR}</td><td>{@link #WAR}</td></tr>
 *   <tr><td>{@code TRUCE}</td><td>{@link #TRUCE}</td></tr>
 *   <tr><td>{@code ALLY}</td><td>{@link #ALLIANCE}</td></tr>
 *   <tr><td>{@code NEUTRAL}</td><td>{@link #NEUTRAL}</td></tr>
 *   <tr><td>— (no legacy value; tribute is a ledger, not a status)</td><td>{@link #TRIBUTE}</td></tr>
 * </table>
 *
 * <p>{@code TRIBUTE} is new at the stance level: in the town-scale
 * engine {@code TributeAction} deliberately leaves the status column at
 * {@code NEUTRAL} because tribute is an asymmetric arrangement that can
 * ride on top of any posture. At realm scale the design names it as one
 * of the five legible relation verbs; whether real instances compose
 * (an alliance carrying a tribute obligation — diplomacy README
 * decision 2 suggests orthogonal flags) is an open implementation
 * question this seed does not answer. The enum names the verbs; it does
 * not model the composition.
 */
public enum RelationStance {
    /** The two realms' production goes into armies, not trade. */
    WAR,
    /** War paused; can expire back into war. */
    TRUCE,
    /** Mutual bond; both sides must have wanted it. */
    ALLIANCE,
    /** One side pays; asymmetric by construction. */
    TRIBUTE,
    /** The default — realms that have never interacted read as NEUTRAL. */
    NEUTRAL;

    /** True iff this is the never-interacted default. */
    public boolean isDefault() {
        return this == NEUTRAL;
    }
}
