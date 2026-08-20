package org.lowern1ght.burg.town;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for {@code Town.applyStockToReserve(StockLedger wire, Map<Item, Integer> reserve)}
 * — the static, no-{@code this}-state version of the StockLedger ↔ {@code reserveStock}
 * wire-side apply path (ADR-0013 + ADR-0026). The instance method
 * {@link Town#applyStockLedger(org.lowern1ght.burg.domain.settlement.StockLedger)} stays
 * the wire-write of record; this helper exists for callers that want the same
 * clear-and-merge body without paying for the cache sync.
 *
 * <p>The return type is {@code int} — the count of dropped entries
 * (unparseable ItemIds + unregistered Items + zero/negative quantities), so a
 * caller that wires a brand-new reserve from a possibly-dirty wire can
 * surface the partial-apply count to a log line or chat warning. Same
 * contract as the instance method.
 *
 * <p><b>Why this test exercises the signature, not the body.</b> Loading
 * {@code Town.class} at test time transitively pulls in
 * {@code net.neoforged.neoforge}'s full runtime classpath (SLF4J, Brigadier,
 * DataFixerUpper, Netty, authlib, logging, etc.), which is not a test classpath
 * concern for the bare-JVM test convention
 * ({@code common/build.gradle §"Plain JVM tests, no Minecraft"}). The
 * {@code testImplementation files(...)} carve-out in {@code common/build.gradle}
 * pulls in the ModDev merged JAR + the four critical transitive deps
 * (SLF4J, Brigadier, DataFixerUpper, authlib) so the class metadata for the
 * method signature is reachable; loading the class body still demands more
 * than is reasonable for a single test.
 *
 * <p>The behavior cases that would normally live here (clear before merge,
 * zero/negative drop, unregistered-Item drop, fixpoint) are pinned by the
 * in-game wire path
 * ({@code C2SSupplyStockPacket.handle} → {@code town.getReserveStock().merge}
 * → {@code town.stockLedger()} cache sync). A future carve that adds an
 * MC-aware test target (a {@code :neoforge} test source set with its own
 * {@code gradle test} task) is the right place for the full behavior tests.
 *
 * <p>What this test does pin:
 * <ol>
 *   <li>the helper is {@code public static};</li>
 *   <li>the helper returns {@code int} (the dropped-entry count);</li>
 *   <li>the helper's two parameters are exactly
 *       {@link org.lowern1ght.burg.domain.settlement.StockLedger} and
 *       {@link Map};</li>
 *   <li>{@link Map} is the parameter's raw class — the generic
 *       {@code Map<Item, Integer>} compiles to {@code Map} at the JVM
 *       level (type erasure), so this assertion still holds.</li>
 * </ol>
 */
class TownApplyToReserveTest {

    @Test
    @DisplayName("Town.applyStockToReserve exists with the right signature: public static int (StockLedger, Map)")
    void helperSignatureIsRight() throws Exception {
        Class<?> townClass = Town.class;
        Method helper = townClass.getMethod("applyStockToReserve",
            org.lowern1ght.burg.domain.settlement.StockLedger.class,
            Map.class);

        assertNotNull(helper, "the helper method must exist");
        assertAll(
            () -> assertTrue(Modifier.isStatic(helper.getModifiers()),
                "applyStockToReserve must be static — no MC state, no instance method"),
            () -> assertTrue(Modifier.isPublic(helper.getModifiers()),
                "applyStockToReserve must be public so callers outside Town can reach it"),
            () -> assertEquals(int.class, helper.getReturnType(),
                "the helper returns the dropped-entry count — int, mirroring the instance method"),
            () -> assertEquals(2, helper.getParameterCount()),
            () -> assertEquals(
                org.lowern1ght.burg.domain.settlement.StockLedger.class,
                helper.getParameterTypes()[0],
                "first parameter is the StockLedger wire"),
            () -> assertEquals(Map.class, helper.getParameterTypes()[1],
                "second parameter is the Map<Item, Integer> reserve "
                    + "(type erasure → Map at the JVM level)")
        );
    }
}