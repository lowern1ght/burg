package org.dawnoftime.onceuponatown.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.integration.ExternalModPresence;
import org.dawnoftime.onceuponatown.integration.ItemResolver;

/**
 * GameTest coverage for {@link ItemResolver} and {@link ExternalModPresence}.
 *
 * <p>Lives here (not under {@code common/src/test/}) because the {@code common} test
 * source set has JUnit + findbugs on its test classpath but NOT Minecraft — the
 * {@code neoForge {} } block in {@code common/build.gradle} adds the game jar as
 * {@code compileOnly} on the main source set, which does not propagate to the test
 * classpath. {@code @GameTest} tests run in a real Minecraft-jvm, so they can touch
 * {@code BuiltInRegistries} and {@code ResourceLocation} without a classpath trick.
 *
 * <p>Run with {@code ./gradlew :neoforge:runGameTestServer} (the {@code runGameTestServer}
 * task is what the {@code gameTestServer} run configuration points at). The JUnit
 * {@code ./gradlew test} task is the wrong entry point for tests in this package —
 * the existing rule in {@code common/build.gradle} ("Plain JVM tests, no Minecraft")
 * is the same constraint that puts these tests here.
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class IntegrationPackagesGameTest {

    private static final String FARMERS_DELIGHT = "farmersdelight";

    @GameTest(template = "empty5x5", timeoutTicks = 100, batch = "integration")
    public static void resolve_knownVanillaItem_returnsStack(GameTestHelper helper) {
        ItemStack stack = ItemResolver.resolve(ResourceLocation.parse("minecraft:dirt"));

        helper.assertFalse(stack.isEmpty(), "minecraft:dirt must resolve to a non-empty stack");
        helper.assertTrue(stack.getItem() == Items.DIRT, "the resolved item must be Items.DIRT");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 100, batch = "integration")
    public static void resolve_missingItem_returnsEmptyStack(GameTestHelper helper) {
        ItemStack stack = ItemResolver.resolve(ResourceLocation.parse("test:nonexistent"));

        helper.assertTrue(stack.isEmpty(), "missing id must yield ItemStack.EMPTY");
        helper.assertTrue(stack == ItemStack.EMPTY, "the EMPTY constant is the expected return");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 100, batch = "integration")
    public static void resolve_missingItem_warnsOnce(GameTestHelper helper) {
        int before = ExternalModPresence.warnedCount();

        // First call: a freshly missing item. warn-dedupe set grows by one.
        ItemResolver.resolve(ResourceLocation.parse("farmersdelight:tomato"));
        int afterFirst = ExternalModPresence.warnedCount();
        helper.assertTrue(afterFirst == before + 1,
            "first call must register the warning (before=" + before + ", afterFirst=" + afterFirst + ")");

        // Second call: same id. Same dedupe bucket. No new warning.
        ItemResolver.resolve(ResourceLocation.parse("farmersdelight:tomato"));
        int afterSecond = ExternalModPresence.warnedCount();
        helper.assertTrue(afterSecond == afterFirst,
            "second call must not re-warn (afterFirst=" + afterFirst + ", afterSecond=" + afterSecond + ")");

        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 100, batch = "integration")
    public static void isPresent_knownItem_true(GameTestHelper helper) {
        helper.assertTrue(ItemResolver.isPresent(ResourceLocation.parse("minecraft:dirt")),
            "minecraft:dirt is present");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 100, batch = "integration")
    public static void isPresent_missingItem_false(GameTestHelper helper) {
        helper.assertFalse(ItemResolver.isPresent(ResourceLocation.parse("test:nonexistent")),
            "test:nonexistent is not present");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 100, batch = "integration")
    public static void isLoaded_minecraft_true(GameTestHelper helper) {
        ExternalModPresence.refresh();

        helper.assertTrue(ExternalModPresence.isLoaded("minecraft"),
            "vanilla is always present on a real game-test JVM");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 100, batch = "integration")
    public static void isLoaded_farmersdelight_false(GameTestHelper helper) {
        ExternalModPresence.refresh();

        helper.assertFalse(ExternalModPresence.isLoaded(FARMERS_DELIGHT),
            "farmersdelight is not a mod dependency and must not be in the loaded set");
        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 100, batch = "integration")
    public static void loadedMods_isUnmodifiableSnapshot(GameTestHelper helper) {
        ExternalModPresence.refresh();

        var snapshot = ExternalModPresence.loadedMods();
        helper.assertTrue(snapshot.contains("minecraft"), "snapshot must contain the vanilla modid");

        // Set.copyOf produces an immutable snapshot. Both .add and .remove must throw.
        try {
            snapshot.add("burg.test.mutation");
            helper.fail("snapshot.add must throw UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
        try {
            snapshot.remove("minecraft");
            helper.fail("snapshot.remove must throw UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
        helper.succeed();
    }
}
