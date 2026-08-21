package org.lowern1ght.burg.town;

import net.minecraft.world.item.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.settlement.StockLedger;
import org.lowern1ght.burg.domain.shared.ItemId;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MC-aware behavior test for the {@link Town} ↔ {@link StockLedger}
 * redirect — the {@code :neoforge:test} counterpart to
 * {@code :common:test}'s {@code TownApplyToReserveTest}, which only pins
 * the {@code public static int (StockLedger, Map)} signature via reflection
 * because loading {@code Town.class} at the bare-JVM classpath transitively
 * pulls in {@code net.neoforged.neoforge}'s full runtime (SLF4J, Brigadier,
 * DataFixerUpper, Netty, authlib). This file has the merged JAR + the
 * four MC transitive deps on the test classpath, so {@code new Town()}
 * runs without {@code NoClassDefFoundError}; it can exercise the instance
 * dispatch on the {@code :neoforge} target.
 *
 * <p><b>What this pins.</b> The redirect contract for the empty path and
 * the parse-stage skip path. The registry-resolved path (entries whose
 * {@link ItemId} parses as a {@code ResourceLocation} AND whose {@link Item}
 * is present in the live registry) lives on the in-game wire site and is
 * not exercised here — see the
 * <i>Why the registry-resolved path is deferred</i> note below.
 *
 * <p><b>Why the registry-resolved path is deferred.</b> The
 * {@code apply*ToReserve} body does
 * {@code Item item = BuiltInRegistries.ITEM.get(rl);} for every parseable
 * entry. Reading the {@code BuiltInRegistries.ITEM} static field triggers
 * its {@code <clinit>} on the merged JAR's classloader; the
 * {@code static {}} block evaluates the {@code ::Items.AIR} bootstrap
 * supplier registered against the {@code ITEM} registry, which goes
 * through {@code Registry.register} → {@code Bootstrap.checkBootstrapCalled}
 * → throws {@code "Not bootstrapped (called from registry …)"}. Without a
 * {@code Bootstrap.run()} in the JUnit JVM (the {@code :neoforge:test}
 * target is a plain JUnit run, not a ModLauncher / JUnitUserDev boot, per
 * {@code neoforge/build.gradle} §"The ModDev plugin's
 * {@code unitTest { enable() }} block" -> we do not want that here"),
 * no registry is writable and
 * {@code new Town().applyStockLedger(<wire with minecraft:stone>)} crashes
 * with the bootstrap exception. Only the empty-wire and parse-fail paths
 * avoid {@code BuiltInRegistries.ITEM}, because the for-loop body never
 * reaches the registry call. The cross-Item inventory tick (the only
 * path that exercises the resolved path in production) is MC-runtime-only
 * and lands in a future {@code gametest} source set.
 *
*     <p>Similarly, {@code new Item(...)} is not constructable on this
     * surface — the {@code Item} constructor reads
     * {@code BuiltInRegistries.ITEM.createIntrusiveHolder(this)} which routes
     * through the same bootstrap gate. Direct mutations of
     * {@link Town#getReserveStock()} therefore use {@code null} as the map
     * key: {@link java.util.HashMap} permits {@code null} keys, the
     * {@link Town} facade drops them at the edge in
     * {@link Town#syncStockLedgerFromReserve()}, and the assertion surface
     * here doesn't need a concrete {@link Item} to demonstrate the
     * redirect — a non-empty {@link Map}{@code <Item,Integer>} that is
     * identity-stable through {@code getReserveStock()} is what the redirect
     * depends on. The reference keys further down (cast to {@code Item} for
     * the map writes) are not real {@code Item}s — the cast is type-system
     * only, since the map's runtime type is {@link java.util.HashMap} which
     * accepts any {@link Object} value as a key and the {@code Item} type
     * parameter is erased at the call site.
 *
 * <p><b>What this pins, concretely:</b>
 * <ol>
 *   <li><b>Empty path on the instance method.</b> {@code applyStockLedger(EMPTY)}
 *       is observable on a real {@link Town} instance: it clears the
 *       reserve, leaves {@link StockLedger#EMPTY} unchanged (the cache
 *       consistency check passes because the reserve stays empty, no
 *       rebuild fires), and returns zero skipped entries. The
 *       {@link Town#getReserveStock()} map is the same reference as the
 *       field — proved by direct mutation being visible to subsequent
 *       reads.</li>
 *   <li><b>Parse-stage skip on the instance method.</b> A wire whose
 *       {@link ItemId}s all fail {@code ResourceLocation.tryParse} is
 *       fully skipped before the registry call; the instance method
 *       returns the entry count as the skipped total, and the reserve
 *       stays empty. The same wire applied via the {@code static}
 *       {@link Town#applyStockToReserve(StockLedger, Map)} helper on the
 *       town's live reserve produces the identical end state — pin for
 *       the redirect identity on the parse-fail path.</li>
 *   <li><b>Clear-before-write on the instance method.</b> A second apply
 *       with a different wire starts from the first apply's end state; a
 *       {@link Town#applyStockLedger(StockLedger)} call replaces, never
 *       accumulates at the {@code Item}-keyed reserve. Pin by populating
 *       the reserve directly via the returned map reference, then
 *       running a parse-fail apply, and observing that the pre-poisoned
 *       entries are cleared before the for-loop iterates.</li>
 * </ol>
 *
 * <p>What this test does <i>not</i> pin (and why a future carve does not
 * need to redo it):
 * <ul>
 *   <li>The clear-before-merge <i>wire semantics</i> (clear-then-sum-other
 *       at the {@link StockLedger} value-object level) are pinned in
 *       {@code :common:test}'s {@code StockLedgerApplyRoundTripTest} —
 *       five cases covering totals, rebuild-from-wire equality, EMPTY-
 *       merge identity, zero-qty source-map drop at construction, and
 *       first-supply-against-EMPTY symmetry.</li>
 *   <li>The full behavior of the registry-resolved edge — the
 *       {@code Item} object survives the lookup, the {@code reserve} entry
 *       carries it, {@code StockLedger.cacheIsConsistent()} rebuilds on
 *       the read — lives in the in-game wire path
 *       ({@code C2SSupplyStockPacket.handle} → {@code town.getReserveStock().merge}
 *       → {@code town.stockLedger()} cache sync) and is verified there.
 *       A {@code gametest} source set, when it lands, exercises this
 *       end to end with a real server bootstrap.</li>
 * </ul>
 */
class TownApplyStockLedgerRedirectTest {

    /**
     * The ItemId strings chosen here are deliberate: each one fails
     * {@code ResourceLocation.tryParse} on the very first character.
     * {@code validPathChar} accepts only {@code _ - a-z 0-9 / .}, so
     * {@code "!"} and {@code "???"} drop at the parse-stage short-circuit
     * and never reach {@code BuiltInRegistries.ITEM.get(rl)}. Two distinct
     * malformed strings exercise the "two-item wire" surface that PR #47's
     * wire helper was built for, without paying for the registry gate.
     *
     * <p>An empty-string {@link ItemId} value would <i>not</i> fail
     * {@code tryParse} — {@code isValidPath("")} returns {@code true}
     * (the length loop never iterates), so the path is the empty string
     * and the resolution proceeds to the registry lookup, which crashes
     * the bootstrap gate. The non-empty invalid characters chosen here
     * fail on the very first {@code validPathChar} check.
     */
    private static final ItemId UNPARSEABLE_A = new ItemId("!");
    private static final ItemId UNPARSEABLE_B = new ItemId("???");

    @Test
    @DisplayName("applyStockLedger(EMPTY) clears the reserve, leaves StockLedger EMPTY, returns 0 — instance dispatch observable")
    void emptyWireClearBranch() {
        Town town = new Town();
        assertSame(StockLedger.EMPTY, town.stockLedger(),
            "fresh town's StockLedger is EMPTY — the additive default for the cache");
        assertEquals(Map.of(), town.getReserveStock(),
            "fresh town's reserveStock is empty — the additive default for the reserve");

        int skipped = town.applyStockLedger(StockLedger.EMPTY);

        assertAll(
            () -> assertEquals(0, skipped,
                "EMPTY wire → 0 skipped entries (no entries to process, no registry touch)"),
            () -> assertSame(StockLedger.EMPTY, town.stockLedger(),
                "EMPTY apply leaves StockLedger EMPTY — cache consistency holds (reserve is empty), no rebuild fires"),
            () -> assertEquals(Map.of(), town.getReserveStock(),
                "EMPTY apply leaves reserveStock empty — the clear runs but has nothing to clear")
        );
    }

    @Test
    @DisplayName("applyStockLedger(wire with unparseable ItemIds) — parse-stage skip returns wire size, reserve untouched")
    void parseStageSkipOnInstanceMethod() {
        Town town = new Town();

        // Two unparseable entries — none reach the registry lookup.
        Map<ItemId, Integer> source = new LinkedHashMap<>();
        source.put(UNPARSEABLE_A, 5);
        source.put(UNPARSEABLE_B, 10);
        StockLedger wire = StockLedger.of(source);
        assertEquals(2, wire.size(),
            "wire has both unparseable entries at construction (of() only drops null/non-positive, not malformed strings)");

        int skipped = town.applyStockLedger(wire);

        assertAll(
            () -> assertEquals(2, skipped,
                "both entries fail tryParse → both increment skipped → return value == wire.size()"),
            () -> assertSame(StockLedger.EMPTY, town.stockLedger(),
                "parse-fail path skips before the registry call → StockLedger stays EMPTY"),
            () -> assertEquals(Map.of(), town.getReserveStock(),
                "parse-fail path never populates the reserve → reserveStock stays empty")
        );
    }

    @Test
    @DisplayName("applyStockLedger and applyStockToReserve produce the same end state on the parse-fail path — redirect identity")
    void instanceAndStaticPathsAgreeOnParseFail() {
        // Build two fresh towns and apply the same wire through each entry
        // point. Both must land on identical (skipped, reserveStock,
        // StockLedger) triples — this is the test for the redirect's body
        // parity, restricted to a wire whose entries all short-circuit at
        // the parse stage.
        Map<ItemId, Integer> source = new LinkedHashMap<>();
        source.put(UNPARSEABLE_A, 3);
        source.put(UNPARSEABLE_B, 7);
        StockLedger wire = StockLedger.of(source);

        Town viaInstance = new Town();
        int instanceSkipped = viaInstance.applyStockLedger(wire);

        Town viaStatic = new Town();
        Map<Item, Integer> staticReserve = viaStatic.getReserveStock();
        int staticSkipped = Town.applyStockToReserve(wire, staticReserve);

        assertAll(
            () -> assertEquals(instanceSkipped, staticSkipped,
                "instance and static return the same skipped count — body parity on the parse-fail path"),
            () -> assertEquals(viaInstance.getReserveStock(), viaStatic.getReserveStock(),
                "both call sites end with the same reserve contents — the redirect's static helper is what the instance delegates to"),
            () -> assertEquals(viaInstance.stockLedger(), viaStatic.stockLedger(),
                "both call sites see the same cached StockLedger — empty because nothing was written through the registry")
        );
    }

    @Test
    @DisplayName("applyStockLedger's clear-first discipline — a parse-failed wire clears prior reserve entries before iterating")
    void clearFirstThenIterateOnParseFail() {
        Town town = new Town();
        Map<Item, Integer> reserve = town.getReserveStock();

        // Step 1 — simulate a prior registry-resolved apply by populating
        // the reserve directly via the live map reference. (We can't go
        // through applyStockLedger itself because every parseable entry
        // would route through BuiltInRegistries.ITEM and crash the
        // bootstrap gate — see class javadoc.) null is a permitted
        // HashMap key and is dropped at the edge in
        // syncStockLedgerFromReserve(), so it stands in as "a prior entry
        // existed at the Item-keyed boundary" without going through Item.
        reserve.put(null, 42);
        assertEquals(1, reserve.size(),
            "step 1 — pre-poison via direct map mutation: reserve is non-empty");

        // Step 2 — applyStockLedger with a parse-failed wire. The body
        // begins with reserveStock.clear() (the clear-first line), so the
        // pre-poisoned entry vanishes before the for-loop iterates; the
        // for-loop adds nothing because every entry fails tryParse.
        Map<ItemId, Integer> source = new LinkedHashMap<>();
        source.put(UNPARSEABLE_A, 1);
        source.put(UNPARSEABLE_B, 2);
        StockLedger parseFailWire = StockLedger.of(source);

        town.applyStockLedger(parseFailWire);

        assertEquals(Map.of(), town.getReserveStock(),
            "step 2 — applyStockLedger clears the reserve first; the parse-fail for-loop adds nothing,"
                + " so the reserve ends empty (the clear-before-write discipline, on this no-registry surface)");
    }

    @Test
    @DisplayName("Town.getReserveStock returns the live reserveStock field — direct map mutations are visible to applyStockLedger's clear")
    void getReserveStockReturnsLiveMapReference() {
        Town town = new Town();

        Map<Item, Integer> firstReference = town.getReserveStock();
        Map<Item, Integer> secondReference = town.getReserveStock();

        // Live-reference identity is the discipline the cache-sync hooks
        // depend on — the apply path's syncStockLedgerFromReserve()
        // callback receives the same map every call, so a mutation
        // visible to the apply path's clear is also visible to the
        // post-apply cache rebuild (when the registry gate is not in
        // the way).
        assertSame(firstReference, secondReference,
            "every getReserveStock() call returns the same live field reference — the apply path's clear reaches the same map");

        // null is a permitted HashMap key; the reserve is recorded
        // identity-hashable per put. Two distinct puts produce two
        // visible entries through both references. (HashMap allows
        // multiple null puts that collapse to one entry; this test
        // uses the key-set-after-put semantics — one put, one entry.)
        firstReference.put(null, 11);
        assertTrue(secondReference.containsKey(null),
            "a put through the first reference is visible through the second reference — map identity holds");
        assertEquals(1, secondReference.size(),
            "the single pre-poisoned entry (null -> 11) is visible through the second reference");

        // The Town.applyStockLedger(EMPTY) call now clears the entry and
        // observes the cleared reserve — which exercises the
        // apply-path's first line (clear-before-write) without going
        // through the registry.
        town.applyStockLedger(StockLedger.EMPTY);

        assertEquals(Map.of(), town.getReserveStock(),
            "applyStockLedger(EMPTY) ran the clear-first line and emptied the reserve;"
                + " the single pre-poisoned entry (null -> 11) is gone");
    }
}
