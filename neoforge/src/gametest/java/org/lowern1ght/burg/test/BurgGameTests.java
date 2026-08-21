package org.lowern1ght.burg.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.lowern1ght.burg.town.Town;

/**
 * Burg's first live-Minecraft behavior test. Targets the structural-flags
 * seam — {@link Town#addZoning(Town.Zone, int)} followed by
 * {@link Town#structuralFlags()} — that the cheap {@code :neoforge:test}
 * JUnit target already pins statically
 * ({@code TownStructuralFlagsRealDerivationsTest}, 7 cases).
 *
 * <p>This {@code @GameTestHolder} lives in the dedicated {@code gametest}
 * source set so it is only discovered by the {@code runGameTestServer}
 * Minecraft run, never by the plain JUnit target. The holder's value
 * ({@code "burg"}) becomes the default {@link GameTest#templateNamespace()}
 * for any {@code @GameTest} method on this class that does not specify
 * its own namespace.
 *
 * <p><b>What this is — and what it is not.</b>
 *
 * <ul>
 *   <li><b>Is</b>: a real instance-method {@code @GameTest} that takes
 *       a {@link GameTestHelper}, mutates a fresh {@link Town}, and reads
 *       the resulting {@code structuralFlags}. The helper gives us a
 *       running {@code ServerLevel} we don't actually need for this
 *       claim — the structural-flags derivation is a pure in-memory
 *       read — but having the helper is what makes the test a
 *       {@code @GameTest} (the framework scans for the helper parameter
 *       to decide which methods to register).</li>
 *   <li><b>Is not</b>: a vanilla {@code @Test} on a bare JUnit classpath.
 *       The {@code gametest} source set is excluded from
 *       {@code sourceSets.test} on purpose, and the {@code compileJava}
 *       toolchain never picks these classes up.</li>
 * </ul>
 *
 * <p><b>Residual — what this PR does NOT do.</b>
 *
 * <ol>
 *   <li><b>{@code data/burg/structure/empty5x5.nbt} fixture ships
 *       with the mod.</b> The {@code @GameTest(template = "empty5x5")}
 *       reference is resolved by
 *       {@code new FileToIdConverter("structure", ".nbt").idToFile(...)}
 *       — {@code data/burg/structure/empty5x5.nbt}, gzip-compressed
 *       NBT. The empty-fixture PR (commit 55b3ee5) shipped an early
 *       {@code .snbt} variant (raw deflate + int palette IDs); the
 *       generator at {@code tools/generate_empty5x5.py} now produces
 *       the canonical {@code .nbt} shape ({@code gzip} compression,
 *       {@code BlockState} NBT palette entries, {@code state} as a
 *       palette index). See {@code EmptyFixtureTest} for the byte
 *       count and round-trip pins.</li>
 *   <li><b>CI invocation of {@code :neoforge:runGameTestServer} is
 *       out of scope for the {@code @GameTestHolder} wiring PR</b>
 *       (the residual here is the same one as the first point — the
 *       fixture). The {@code gameTestServer} run configuration boots a
 *       real Minecraft dedicated server via ModLauncher and is wired
 *       up by the {@code gametest-run} PR alongside the
 *       {@code .github/workflows/} job that runs it on PRs.</li>
 * </ol>
 *
 * <p>What this PR DOES do: it adds the {@code gametest} source set to
 * {@code :neoforge}, wires its classpath (ModDev merged JAR + SLF4J +
 * the four MC transitives {@code Town}'s static init pulls in —
 * brigadier, datafixerupper, authlib), registers the source set on the
 * mod so {@code runGameTestServer} can pick the classes up at server
 * runtime, and ships a compiling {@code BurgGameTests} that proves the
 * source-set wiring. The two residuals above are the next iteration's
 * work; both are documented here so the next reader does not have to
 * discover them by running the build.
 */
@GameTestHolder("burg")
public final class BurgGameTests {

    /**
     * Act-5 mutator seam under a live GameTestHelper: the same {@code Town}
     * the static unit target exercises, but now wrapped in the
     * {@code @GameTest} machinery so a real MC server sees it. Asserts
     * that {@code town.addZoning(CORE, 5)} flips the
     * {@code industryZoned} leg of {@code town.structuralFlags()}.
     *
     * <p>The pin mirrors the {@code addZoningFlipsIndustryZoned} case
     * of {@code TownStructuralFlagsRealDerivationsTest} — same act, same
     * claim, different JVM. The static target proves the seam on a
     * bare-JUnit classpath (cheap, runs in &lt;1s); this {@code @GameTest}
     * proves the seam survives the {@code runGameTestServer} bootstrap
     * (the real residual test — gating on that is the next iteration).
     */
    @GameTest(template = "empty5x5")
    public void addZoningFlipsIndustryZonedOnLiveServer(GameTestHelper helper) {
        Town town = new Town();

        town.addZoning(Town.Zone.CORE, 5);

        helper.assertTrue(
            town.structuralFlags().industryZoned(),
            "expected industryZoned=true after Town.addZoning(CORE, 5); "
                + "the structural gate's industry_zoned leg should open"
        );
        helper.assertTrue(
            town.getZoningCount().containsKey(Town.Zone.CORE),
            "expected CORE to be observed in zoningCount after "
                + "Town.addZoning(CORE, 5)"
        );

        helper.succeed();
    }
}
