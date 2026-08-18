package org.lowern1ght.burg.gametest;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.integration.ExternalModPresence;
import org.lowern1ght.burg.integration.xaero.XaeroIntegration;
import org.lowern1ght.burg.town.Town;

/**
 * GameTest coverage for {@link XaeroIntegration}'s soft-dep fallback path.
 *
 * <p>Tests deliberately avoid touching the Xaero waypoint API directly: adding the
 * mod to the test classpath is a separate phase, and the API is also physical-client
 * only (waypoints are stored in per-player JSON files on the player's machine, with
 * no server-to-client waypoint push path). The tests in this class verify what
 * actually matters for the soft-dep integration:
 *
 * <ol>
 *   <li>{@link #isAvailable_returnsFalse_whenXaeroNotLoaded} — the default state on
 *       a test JVM that does not have Xaero's Minimap installed.</li>
 *   <li>{@link #onTownRegistered_doesNothing_whenXaeroNotLoaded} and the
 *       symmetric {@code Removed}, {@code WarStarted}, {@code WarEnded} cases —
 *       every public entry point must no-op cleanly (no exception, no state
 *       change) when the mod is absent. Reflection is never invoked on this path.</li>
 * </ol>
 *
 * <p>Run with {@code ./gradlew :neoforge:runGameTestServer}. The dedicated
 * game-test server does not have Xaero installed, so every test here exercises
 * the no-Xaero branch.
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class XaeroIntegrationGameTest {

    @GameTest(template = "empty5x5", timeoutTicks = 100, batch = "integration")
    public static void isAvailable_returnsFalse_whenXaeroNotLoaded(GameTestHelper helper) {
        // Force a refresh so the test does not depend on whatever the rest of the
        // test server happened to load.
        ExternalModPresence.refresh();

        helper.assertFalse(XaeroIntegration.isAvailable(),
            "xaerominimap is not a hard dependency and must not be in the loaded set");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 100, batch = "integration")
    public static void onTownRegistered_doesNothing_whenXaeroNotLoaded(GameTestHelper helper) {
        ExternalModPresence.refresh();
        XaeroIntegration.resetReflectionCacheForTests();

        Town town = new Town();
        town.setName("TestTown");
        // No exception means the no-op path is clean. Reflection is never
        // attempted because isAvailable() returns false at the very top.
        XaeroIntegration.onTownRegistered(town);

        helper.assertFalse(XaeroIntegration.isReflectionResolved(),
            "reflection must not resolve on a server without Xaero");
        helper.assertFalse(XaeroIntegration.isReflectionFailed(),
            "no reflection means no failure to record");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 100, batch = "integration")
    public static void onTownRemoved_doesNothing_whenXaeroNotLoaded(GameTestHelper helper) {
        ExternalModPresence.refresh();
        XaeroIntegration.resetReflectionCacheForTests();

        Town town = new Town();
        town.setName("TestTown");
        XaeroIntegration.onTownRemoved(town);

        helper.assertFalse(XaeroIntegration.isReflectionResolved(),
            "onTownRemoved must not attempt reflection when Xaero is absent");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 100, batch = "integration")
    public static void onWarStarted_doesNothing_whenXaeroNotLoaded(GameTestHelper helper) {
        ExternalModPresence.refresh();
        XaeroIntegration.resetReflectionCacheForTests();

        Town attacker = new Town();
        attacker.setName("A");
        Town defender = new Town();
        defender.setName("B");

        // Anchor ZERO -> guard skips even if the soft-dep gate weren't there.
        attacker.registerBuilding(new BlockPos(0, 64, 0), "test:zero", List.of(),
            new net.minecraft.world.level.levelgen.structure.BoundingBox(0, 64, 0, 1, 65, 1),
            net.minecraft.world.level.block.Rotation.NONE);
        defender.registerBuilding(new BlockPos(0, 64, 0), "test:zero", List.of(),
            new net.minecraft.world.level.levelgen.structure.BoundingBox(0, 64, 0, 1, 65, 1),
            net.minecraft.world.level.block.Rotation.NONE);

        XaeroIntegration.onWarStarted(attacker, defender);

        helper.assertFalse(XaeroIntegration.isReflectionResolved(),
            "onWarStarted must not attempt reflection when Xaero is absent");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 100, batch = "integration")
    public static void onWarEnded_doesNothing_whenXaeroNotLoaded(GameTestHelper helper) {
        ExternalModPresence.refresh();
        XaeroIntegration.resetReflectionCacheForTests();

        Town attacker = new Town();
        attacker.setName("A");
        Town defender = new Town();
        defender.setName("B");

        XaeroIntegration.onWarEnded(attacker, defender);

        helper.assertFalse(XaeroIntegration.isReflectionResolved(),
            "onWarEnded must not attempt reflection when Xaero is absent");
        helper.succeed();
    }
}
