package org.lowern1ght.burg.domain.settlement;

/**
 * The structural act-4 trigger for the town hub: a closed triple of
 * {@code boolean} conditions that the {@code Town} facade derives per call
 * and folds into the {@link HubMode} predicate. The three flags are the ones
 * {@code openspec/changes/hub-becomes-window/specs/construction-mode-supply-mode}
 * names as the act-4 trigger:
 *
 * <ul>
 *   <li>{@link #corePopulated} — every footprint inside the core radius is occupied.</li>
 *   <li>{@link #industryZoned} — the zoning layer has at least one industry cell outside the core radius.</li>
 *   <li>{@link #roadLaid} — the road planner has at least one path from the core to the industry zone.</li>
 * </ul>
 *
 * <p>The flag-set is the second leg of the act-4 gate. The first leg is the
 * standing threshold + acquisition state; the third is the act itself.
 * Together the legs make the SUPPLY-mode flip a real predicate, not a
 * shadow of the construction queue. See
 * {@code openspec/changes/hub-becomes-window/specs/construction-mode-supply-mode
 * §"Requirement: structural predicate is three conditions AND-ed"} for the
 * subset rule that datapacks will eventually configure; today the rule
 * collapses to {@link #isAnySet()} (the permissive form documented below),
 * and tightens to {@link #isComplete()} once the underlying fields (roads,
 * zoning layers) land on {@code Town}.
 *
 * <p><b>Permissive default.</b> The flag-set is value-only — it does not
 * know what the underlying fields look like. {@link Town#structuralFlags()}
 * is the read-side adapter: today it returns
 * {@code StructuralFlags.of(true, true, true)} because the road / zoning
 * state is not yet modeled on {@code Town}, and the structural predicate
 * must stay non-zero for the existing SUPPLY transition to keep firing
 * (the act-4 hand-off is not the moment to break the wire). When the
 * underlying fields land, {@code Town#structuralFlags()} flips to
 * {@code StructuralFlags.of(corePopulated, industryZoned, roadLaid)} and
 * the act-4 gate gets its teeth.
 *
 * <p>No Minecraft imports. The record is a domain value object in the same
 * Minecraft-free lineage {@link Acquisition}, {@link HubMode}, and
 * {@link HubView} established; the engine-edge consumer (the
 * {@code TownAnchorBlock} log line) reads the derived value through
 * {@code Town#hubMode()}, never through this record directly.
 */
public record StructuralFlags(boolean corePopulated, boolean industryZoned, boolean roadLaid) {

    /**
     * Empty flag-set — the additive default for any town whose structural
     * conditions are all unmet. {@code StructuralFlags.NONE} is the canonical
     * equality case for {@code new StructuralFlags(false, false, false)};
     * the {@link #of(boolean, boolean, boolean)} factory collapses to this
     * sentinel so callers never produce a fresh record for the default
     * (same referential-stability recipe {@link HubView#EMPTY} uses).
     */
    public static final StructuralFlags NONE = new StructuralFlags(false, false, false);

    /**
     * Builds a flag-set. The all-false input collapses to the
     * {@link #NONE} sentinel so the factory is a referentially-stable
     * identity for the empty case; non-empty inputs return fresh records.
     */
    public static StructuralFlags of(boolean corePopulated, boolean industryZoned, boolean roadLaid) {
        return (!corePopulated && !industryZoned && !roadLaid)
            ? NONE
            : new StructuralFlags(corePopulated, industryZoned, roadLaid);
    }

    /**
     * True iff no condition is met. The structural predicate's
     * "no progress" floor — a town in this state reads as
     * {@code HubMode.CONSTRUCTION} regardless of acquisition.
     */
    public boolean isEmpty() {
        return !corePopulated && !industryZoned && !roadLaid;
    }

    /**
     * True iff at least one condition is met. This is the act-4
     * gate's permissive form today: any partial progress on the
     * structural triple qualifies a town for SUPPLY mode once
     * acquisition is right. The strict form is {@link #isComplete()},
     * which becomes the gate once the underlying fields (roads,
     * zoning layers) land on {@code Town} and {@link Town#structuralFlags()}
     * is no longer hard-coded to all-true.
     */
    public boolean isAnySet() {
        return corePopulated || industryZoned || roadLaid;
    }

    /**
     * True iff every condition is met. This is the future act-4
     * gate's strict form: every flag the subset rule names must be
     * true at the moment of query. Today no {@code Town} reaches
     * {@code isComplete() == false} on the SUPPLY leg because the
     * helper is hard-coded to all-true; once {@code Town#structuralFlags()}
     * derives the flags from real state, this method becomes the
     * "town has earned SUPPLY" check.
     */
    public boolean isComplete() {
        return corePopulated && industryZoned && roadLaid;
    }
}
