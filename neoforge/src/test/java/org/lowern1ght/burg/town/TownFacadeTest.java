package org.lowern1ght.burg.town;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.settlement.Acquisition;
import org.lowern1ght.burg.domain.settlement.HubMode;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MC-aware behavior tests for the {@link Town} facade. The {@code :common:test}
 * target deliberately stays Minecraft-free, so the only way to exercise
 * {@code new Town()} + {@code Town.hubMode()} end-to-end is here, where the
 * ModDev merged JAR is on the test classpath. Each case is the smallest
 * construct that pins one leg of the {@link Town#hubMode()} predicate; the
 * third leg (the standing-threshold {@code BurgConfig} reader) is intentionally
 * not exercised here — its unit pinning lives in {@code :common:test}'s
 * {@code TownHubModeConfigTest}, which exercises {@code StandingBook.meetsActThreshold}
 * directly without loading {@code BurgConfig}.
 *
 * <p><b>What the predicate does today.</b>
 * {@code Town.hubMode()} returns {@link HubMode#SUPPLY} iff all three legs pass:
 * acquisition != {@link Acquisition#FREE}, structural flags is non-empty, and
 * the highest standing meets the act threshold. The carve documented in
 * {@code openspec/changes/hub-becomes-window} specifies the strict form;
 * {@code Town#structuralFlags()} is the read-side adapter and the strict
 * derivation collapses to {@code StructuralFlags.NONE} for every fresh save
 * because the underlying fields (zoning count, planned roads) start empty.
 * That is what these tests pin — the gate's collapse behaviour on a fresh
 * town is part of the contract, not a coincidence of the test.
 */
class TownFacadeTest {

    @Test
    @DisplayName("new Town() constructs and hubMode() returns CONSTRUCTION for a fresh town")
    void freshTownLandsOnConstructionMode() {
        // The default ctor sets acquisition=FREE, structural fields all empty,
        // standing book empty. The predicate short-circuits at the first leg
        // (acquisition == FREE) and returns CONSTRUCTION — the additive default
        // for any world that hasn't earned SUPPLY yet.
        Town town = new Town();

        assertNotNull(town, "the default ctor must produce a non-null Town");
        assertAll(
            () -> assertEquals(Acquisition.FREE, town.getAcquisition(),
                "fresh town's acquisition is FREE — the additive default for a new save"),
            () -> assertEquals(HubMode.CONSTRUCTION, town.hubMode(),
                "fresh town's hub mode is CONSTRUCTION — first leg (acquisition) fails the gate"),
            () -> assertEquals(0, town.highestStanding(),
                "fresh town's standing book is empty — highest score reads as zero, never 'absent'")
        );
    }

    @Test
    @DisplayName("setAcquisition(ELEVATED) does not flip hubMode() to SUPPLY — the structural gate still blocks")
    void elevatedAcquisitionWithoutStructuralStillGates() {
        // Setting acquisition to ELEVATED satisfies the FIRST leg of the
        // predicate. The SECOND leg — structuralFlags().isAnySet() — is the
        // gate on a fresh town because zoningCount and plannedRoads are both
        // empty by default, so isAnySet() returns false. The hub stays on
        // CONSTRUCTION until the act-5 zoning / road-planner carves land
        // and populate the underlying fields.
        //
        // This case pins the strict-form collapse: a chief (ELEVATED) is not
        // enough on its own. The third leg (standing ≥ ACT_THRESHOLD) is never
        // reached because the second leg short-circuits, so BurgConfig's
        // static init is not required for this test.
        Town town = new Town();
        town.setAcquisition(Acquisition.ELEVATED);

        assertAll(
            () -> assertEquals(Acquisition.ELEVATED, town.getAcquisition(),
                "setAcquisition(ELEVATED) is observed by getAcquisition() — the facade is a direct write"),
            () -> assertEquals(HubMode.CONSTRUCTION, town.hubMode(),
                "hub mode stays CONSTRUCTION — the structural leg is the gate on a fresh town, "
                    + "not the acquisition leg"),
            () -> assertEquals(0, town.highestStanding(),
                "standing book still empty — setAcquisition does not seed standing")
        );
    }

    @Test
    @DisplayName("StructuralFlags.NONE is the strict-collapse result on a fresh town")
    void structuralFlagsCollapseToNone() {
        // Spec concrete shape: "Town.setAcquisition(ELEVATED) flips hubMode()
        // to... what? Inspect the predicate; document the actual behavior."
        // The inspection is: structuralFlags() returns StructuralFlags.NONE
        // for a fresh town (no core populated, no industry zoned, no road
        // laid). This is the empty-flag-set sentinel documented in
        // StructuralFlags.of — all three legs are false on a fresh town, so
        // the predicate collapses to NONE regardless of acquisition.
        Town town = new Town();

        assertEquals(org.lowern1ght.burg.domain.settlement.StructuralFlags.NONE,
            town.structuralFlags(),
            "fresh town's structural flags collapse to NONE — the strict form holds "
                + "because zoningCount and plannedRoads both start empty");
    }
}