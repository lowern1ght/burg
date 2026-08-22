package org.lowern1ght.burg.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import org.lowern1ght.burg.town.Quest;
import org.lowern1ght.burg.town.Town;

import java.util.Optional;

/**
 * Burg's live-Minecraft behavior tests. Two pins, both under the
 * {@code runGameTestServer} boot (a real MC dedicated server in
 * gametest mode via ModLauncher):
 *
 * <ol>
 *   <li>{@link #addZoningFlipsIndustryZonedOnLiveServer} — the
 *       structural-flags seam {@link Town#addZoning(Town.Zone, int)} →
 *       {@link Town#structuralFlags()}, mirrored from the cheap
 *       {@code :neoforge:test} JUnit target
 *       ({@code TownStructuralFlagsRealDerivationsTest}, 7 cases).</li>
 *   <li>{@link #findQuestDefReturnsAddedQuestAndEmptyForUnknownDefOnLiveServer} —
 *       the ADR-0029 defId-keyed engine port
 *       {@link Town#findQuestDef(String)} (after
 *       {@link Town#addQuest(org.lowern1ght.burg.town.Quest)}). The
 *       static signature lives in
 *       {@code :common:test}'s {@code TownQuestLogSotTest}; the MC-aware
 *       end-to-end pin lives in {@code :neoforge:test}'s
 *       {@code TickSchedulerQuestTickPortTest}; this {@code @GameTest}
 *       is the third leg — the live MC server bootstrap.</li>
 * </ol>
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

    /**
     * Live-Minecraft pin for the ADR-0029 {@link Town#findQuestDef(String)}
     * engine port. The {@code :neoforge:test} static target
     * ({@code TickSchedulerQuestTickPortTest}) drives the same seam
     * through {@code TickScheduler.tickQuests} on a bare JUnit classpath;
     * this {@code @GameTest} proves the port survives the
     * {@code runGameTestServer} bootstrap end-to-end.
     *
     * <p>The seam under test:
     * <ol>
     *   <li>{@code Town.addQuest(Quest)} populates both the SoT
     *       {@code questLog} (a STATUS_ACTIVE ref for the defId) and the
     *       derived {@code questDefIndex} (the rich {@link Quest} keyed
     *       by defId). The engine primary key is defId — a carve that
     *       re-introduced questId as the index key would re-introduce
     *       the per-spawn identity the carve retired.</li>
     *   <li>{@code Town.findQuestDef(defId)} is the O(1) read path the
     *       four engine consumers — {@code QuestManager.isAlreadyActive},
     *       {@code TickScheduler.tickQuests},
     *       {@code C2SContributeQuestPacket.handle},
     *       {@code TownHubDataBuilder} — read through. Empty when no
     *       quest with the defId is active, present carrying the same
     *       {@link Quest} instance {@code addQuest} stored.</li>
     * </ol>
     *
     * <p>Both assertions are pinned:
     * <ul>
     *   <li>The known-defId branch returns the same {@link Quest}
     *       instance {@code addQuest} inserted (identity, not equality —
     *       the contract is "no defensive copy", see
     *       {@code TickSchedulerQuestTickPortTest.tickQuestsIsIdempotentForActiveDefId}).</li>
     *   <li>The unknown-defId branch returns {@link Optional#empty()}
     *       so callers can chain {@code .orElse(null)} without a guard.
     *       This is the negative half of the seam — a carve that
     *       accidentally returned {@code null} would NPE the
     *       contribute-packet handle before the C2S codec surfaced it.</li>
     * </ul>
     *
     * <p>The static signature pin lives in
     * {@code :common:test}'s {@code TownQuestLogSotTest.findQuestDefPortSignature}
     * and the MC-aware end-to-end pin lives in
     * {@code :neoforge:test}'s {@code TickSchedulerQuestTickPortTest}.
     * This is the third leg — the live MC server bootstrap, which is
     * the only place the {@code gametest} source-set wiring
     * (PRs #64, #66, #67, #68, #69) is exercised end to end.
     */
    @GameTest(template = "empty5x5")
    public void findQuestDefReturnsAddedQuestAndEmptyForUnknownDefOnLiveServer(GameTestHelper helper) {
        Town town = new Town();

        // ADR-0029 — addQuest requires both questId (the per-spawn
        // identity the contribute packet's client render carries) AND
        // defId (the engine primary key the findQuestDef port reads
        // through). The defaults (null questId / null defId) make
        // addQuest a silent no-op; we set both explicitly so the test
        // is not a no-op on its own.
        Quest quest = new Quest();
        quest.questId = "q-smoke-001";
        quest.defId = "burg:test:smoke";
        quest.questType = "TASK";

        town.addQuest(quest);

        Optional<Quest> present = town.findQuestDef("burg:test:smoke");
        Optional<Quest> absent = town.findQuestDef("burg:does:not:exist");

        helper.assertTrue(
            present.isPresent(),
            "expected findQuestDef(\"burg:test:smoke\") to be present after "
                + "Town.addQuest(quest); the defId-keyed questDefIndex must "
                + "carry the quest we just added"
        );
        helper.assertTrue(
            present.orElseThrow() == quest,
            "expected findQuestDef to return the same Quest instance we "
                + "added — the port must not defensive-copy the entry"
        );
        helper.assertTrue(
            quest.defId.equals(present.orElseThrow().defId),
            "expected the surfaced Quest to carry the same defId we added "
                + "— the engine primary key survives the read port"
        );
        helper.assertFalse(
            absent.isPresent(),
            "expected findQuestDef(\"burg:does:not:exist\") to be empty — "
                + "an unknown defId must yield Optional.empty() so callers "
                + "can chain .orElse(null) without a guard"
        );

        helper.succeed();
    }
}
