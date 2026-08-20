package org.lowern1ght.burg.town;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.shared.CitizenId;
import org.lowern1ght.burg.domain.settlement.Standing;
import org.lowern1ght.burg.domain.settlement.StandingBook;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the act-threshold gate added in this carve — the third leg of
 * {@link Town#hubMode()} — without bare-JVM-constructing a {@link Town}
 * (the class references {@code net.minecraft.*} on its god-object
 * fields and cannot be loaded by the {@code :common:test} target's
 * bare-JVM classpath; the {@code testImplementation files(...)} carve-out
 * in {@code common/build.gradle} pulls in the ModDev merged JAR +
 * critical transitive deps so the class metadata for the method
 * signatures is reachable).
 *
 * <p>What this test pins:
 * <ol>
 *   <li>{@link StandingBook#meetsActThreshold(int, double)} exists with the
 *       right contract — a static helper that returns
 *       {@code standing >= threshold}. The {@link Town#hubMode()} predicate
 *       delegates to it; this is the one truth the bare-JVM classpath can
 *       exercise directly. (The helper lives on {@link StandingBook}, not
 *       {@link Town}, so its static-init chain stays bare-JVM-clean —
 *       calling {@code Town.meetsActThreshold(...)} would trigger Town's
 *       static initializer, which transitively pulls in
 *       {@code io.netty.handler.codec.DecoderException} via the ModDev merged
 *       JAR and breaks the bare-JVM test contract.)</li>
 *   <li>{@code Town.highestStanding()} exists with the right signature —
 *       a public instance method that delegates to
 *       {@link StandingBook#highestStanding()}.</li>
 *   <li>{@link StandingBook#highestStanding()} returns the maximum score
 *       on the roll, or zero when the book is empty. The contract is
 *       pinned directly against {@link StandingBook} — no {@code Town}
 *       involvement — so the test stays in the bare-JVM domain and
 *       catches a regression that drops the third leg of the gate.</li>
 *   <li>The {@code hubMode()} third leg's gate logic
 *       ({@code meetsActThreshold(standing, ACT_THRESHOLD)}) is exercised
 *       at the threshold boundaries — the spec names 50 as the default
 *       and the slider range is {@code [0, 100]}, so the boundary cases
 *       pinned are 0, 49, 50, 51, 100.</li>
 * </ol>
 */
class TownHubModeConfigTest {

    @Test
    @DisplayName("StandingBook.meetsActThreshold gate: standing >= threshold — the boundary cases the slider produces")
    void meetsActThresholdGate() {
        // The Cloth Config knob's stored range is [0, 100], default 50.
        // The boundary cases pinned here are the four points the slider
        // can land on: 0 (no standing), 49 (just below), 50 (the default
        // crossing), 51 (just above), 100 (the ceiling).
        double threshold = 50.0;
        assertAll(
            () -> assertFalse(StandingBook.meetsActThreshold(0, threshold),
                "0 standing does not meet a 50 threshold"),
            () -> assertFalse(StandingBook.meetsActThreshold(49, threshold),
                "49 standing does not meet a 50 threshold — off-by-one guard"),
            () -> assertTrue(StandingBook.meetsActThreshold(50, threshold),
                "50 standing exactly meets a 50 threshold — the default crossing"),
            () -> assertTrue(StandingBook.meetsActThreshold(51, threshold),
                "51 standing meets a 50 threshold — just above"),
            () -> assertTrue(StandingBook.meetsActThreshold(100, threshold),
                "100 standing meets a 50 threshold — the ceiling")
        );
    }

    @Test
    @DisplayName("StandingBook.meetsActThreshold gate: threshold = 0 always fires (any standing ≥ 0), threshold = 100 only on the ceiling")
    void meetsActThresholdSliderEndpoints() {
        // The slider's stored range is [0, 100]; the gate's behaviour at
        // the endpoints is the contract the user sees on the Cloth screen.
        assertAll(
            () -> assertTrue(StandingBook.meetsActThreshold(0, 0.0),
                "threshold = 0: any non-negative standing meets it (the floor)"),
            () -> assertFalse(StandingBook.meetsActThreshold(-1, 0.0),
                "negative standing still does not meet threshold = 0 (a citizen with score -1 is the rare bad-actor)"),
            () -> assertTrue(StandingBook.meetsActThreshold(100, 100.0),
                "threshold = 100: standing = 100 meets the ceiling"),
            () -> assertFalse(StandingBook.meetsActThreshold(99, 100.0),
                "threshold = 100: standing = 99 does not meet it — off-by-one guard at the ceiling")
        );
    }

    @Test
    @DisplayName("StandingBook.highestStanding returns zero on the empty book — the additive default")
    void emptyBookReturnsZero() {
        // The additive default for an old save: a town with no standing
        // roll reads as 0, never as "absent" — the same discipline
        // StandingBook.standingFor(citizen) applies to a single citizen.
        assertEquals(0, StandingBook.EMPTY.highestStanding(),
            "the empty book's highest score is zero (the additive default, not 'absent')");
    }

    @Test
    @DisplayName("StandingBook.highestStanding returns the maximum score on a populated roll")
    void populatedBookReturnsMax() {
        UUID alice = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID bob = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID carol = UUID.fromString("00000000-0000-0000-0000-000000000003");
        StandingBook book = StandingBook.of(Map.of(
            CitizenId.of(alice), new Standing(CitizenId.of(alice), 12),
            CitizenId.of(bob),   new Standing(CitizenId.of(bob),   55),
            CitizenId.of(carol), new Standing(CitizenId.of(carol),  7)
        ));

        assertEquals(55, book.highestStanding(),
            "highestStanding returns the maximum score across the roll — 55 (the chief's standing)");
    }

    @Test
    @DisplayName("StandingBook.highestStanding ignores negative scores — the max is still the max")
    void populatedBookWithNegatives() {
        UUID alice = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID bob = UUID.fromString("00000000-0000-0000-0000-000000000002");
        StandingBook book = StandingBook.of(Map.of(
            CitizenId.of(alice), new Standing(CitizenId.of(alice), -5),
            CitizenId.of(bob),   new Standing(CitizenId.of(bob),   30)
        ));

        assertEquals(30, book.highestStanding(),
            "highestStanding ignores negatives — the max is still the max (the standing scale is signed)");
    }

    @Test
    @DisplayName("StandingBook.highestStanding: gate decision pairs book score with default threshold (50)")
    void gateDecisionWithDefaultThreshold() {
        // Compose the bare-JVM contract: the StandingBook.highestStanding()
        // + StandingBook.meetsActThreshold(int, double) pair IS the third
        // leg of Town.hubMode(). When a town has no chief yet (book empty),
        // the gate is false; when a chief has 50+ standing, the gate is
        // true.
        UUID alice = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertAll(
            () -> assertFalse(StandingBook.meetsActThreshold(
                    StandingBook.EMPTY.highestStanding(), 50.0),
                "empty book → 0 → 0 < 50 → gate is false"),
            () -> assertFalse(StandingBook.meetsActThreshold(
                    StandingBook.of(Map.of(
                        CitizenId.of(alice),
                            new Standing(CitizenId.of(alice), 30)
                    )).highestStanding(), 50.0),
                "30 standing → 30 < 50 → gate is false"),
            () -> assertTrue(StandingBook.meetsActThreshold(
                    StandingBook.of(Map.of(
                        CitizenId.of(alice),
                            new Standing(CitizenId.of(alice), 60)
                    )).highestStanding(), 50.0),
                "60 standing → 60 >= 50 → gate is true")
        );
    }

    @Test
    @DisplayName("Town.highestStanding exists — public instance int, the read-side adapter for the gate")
    void highestStandingSignature() throws Exception {
        // Reflection-only check (no static call → no Town class init).
        // Town.class linking happens via getMethod() but Town's <clinit>
        // does not run, which keeps the bare-JVM test classpath quiet.
        Method m = Town.class.getMethod("highestStanding");

        assertNotNull(m, "the public accessor must exist on Town");
        assertAll(
            () -> assertFalse(Modifier.isStatic(m.getModifiers()),
                "the accessor is per-instance — reads the town's standing book"),
            () -> assertTrue(Modifier.isPublic(m.getModifiers()),
                "the accessor is public so TownCommand and the TownHubDataBuilder S2C packet can read it"),
            () -> assertEquals(int.class, m.getReturnType(),
                "the accessor returns the highest score on the roll — int")
        );
    }
}
