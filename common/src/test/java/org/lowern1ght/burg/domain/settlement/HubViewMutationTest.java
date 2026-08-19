package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation-style invariants for {@link HubView}: the record's equals /
 * hashCode is value-based, the {@link HubView#EMPTY} sentinel is
 * referentially stable, {@code of(null)} collapses to {@code EMPTY}
 * rather than producing a null-mode record, and the boolean mode
 * accessors are mutually exclusive. Each assertion is written to kill a
 * specific mutant (a swapped predicate, a widened bound, a relaxed
 * constructor) — not to mirror the Javadoc.
 */
class HubViewMutationTest {

    @Test
    @DisplayName("EMPTY is referentially stable — many accesses return the same instance")
    void emptyIsReferentiallyStable() {
        HubView a = HubView.EMPTY;
        HubView b = HubView.EMPTY;
        HubView c = HubView.of(HubMode.CONSTRUCTION);
        assertSame(a, b, "EMPTY == EMPTY across reads");
        assertSame(a, c,
            "of(CONSTRUCTION) returns EMPTY (the additive default), not a fresh record");
    }

    @Test
    @DisplayName("record equality is value-based — same mode, same equals")
    void recordEqualityIsValueBased() {
        HubView left = new HubView(HubMode.SUPPLY);
        HubView right = new HubView(HubMode.SUPPLY);
        assertAll(
            () -> assertNotSame(left, right,
                "two `new HubView(...)` calls produce different instances (kills a static-cache mutant)"),
            () -> assertEquals(left, right,
                "value equality holds across two distinct instances"),
            () -> assertEquals(left.hashCode(), right.hashCode(),
                "equal records share a hash code (kills an asymmetric-hashCode mutant)"),
            () -> assertNotEquals(left, new HubView(HubMode.CONSTRUCTION),
                "different modes are unequal")
        );
    }

    @Test
    @DisplayName("isConstruction and isSupply are mutually exclusive and exhaustive")
    void modeAccessorsAreMutuallyExclusive() {
        for (HubMode mode : HubMode.values()) {
            HubView view = new HubView(mode);
            assertNotEquals(view.isConstruction(), view.isSupply(),
                mode + " view: isConstruction and isSupply must disagree");
            assertEquals(mode == HubMode.CONSTRUCTION, view.isConstruction(),
                mode + " view: isConstruction matches the mode");
            assertEquals(mode == HubMode.SUPPLY, view.isSupply(),
                mode + " view: isSupply matches the mode");
        }
    }

    @Test
    @DisplayName("of(null) collapses to EMPTY — no null-mode record leaks out")
    void ofNullCollapsesToEmpty() {
        HubView view = HubView.of(null);
        assertSame(HubView.EMPTY, view,
            "of(null) must return EMPTY (kills a pass-through-null mutant)");
        assertEquals(HubMode.CONSTRUCTION, view.mode(),
            "EMPTY.mode() is CONSTRUCTION — the wire-side reader never sees null");
    }

    @Test
    @DisplayName("record toString includes the mode — never \"null\"")
    void toStringIncludesMode() {
        HubView view = new HubView(HubMode.SUPPLY);
        String text = view.toString();
        assertTrue(text != null && text.contains("SUPPLY"),
            "toString of a SUPPLY view mentions SUPPLY (got: " + text + ")");
        assertFalse(text.equals("null"),
            "toString never renders the literal string \"null\"");
    }

    @Test
    @DisplayName("HubView is constructible for every HubMode")
    void constructibleForEveryMode() {
        for (HubMode mode : HubMode.values()) {
            HubView view = new HubView(mode);
            assertSame(mode, view.mode(),
                mode + " round-trips through the record ctor");
        }
    }
}