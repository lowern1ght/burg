package org.lowern1ght.burg.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Canary for the {@code :neoforge:test} target — proves that the ModDev
 * merged JAR is on the classpath and that a representative slice of
 * {@code net.minecraft.*} types resolves at test time. If this canary
 * fails, every other MC-aware test in this target fails to compile or
 * class-link, so the failure mode is loud and points at the carve-out in
 * {@code neoforge/build.gradle}.
 *
 * <p><b>What this is not.</b> Not a Gametest, not a server harness. No
 * {@code MinecraftServer} bootstrap, no world load, no chunk placement.
 * The {@code testImplementation files(...)} block in
 * {@code neoforge/build.gradle} wires the merged JAR + the ModDev-plugin
 * legacy classpath into the test classpath; this canary exercises the
 * canary surface and stops there. Real Gametest coverage lands in a
 * future {@code gametest} source set, not here.
 *
 * <p><b>Cheap by design.</b> Three class loads (BlockPos, ResourceLocation,
 * BuiltInRegistries-class-only), zero registry reads, zero MC bootstrap.
 * No fixtures, no resources, no SLF4J configuration — whatever SLF4J
 * binding the test classpath carries is what we use.
 *
 * <p><b>Why no {@link Item} static field read.</b> Reading any MC registry
 * static field (e.g. {@code Items.AIR}, {@code Blocks.STONE}) throws
 * {@code "Not bootstrapped"} via {@code net.minecraft.server.Bootstrap}
 * unless MC has run its full bootstrap chain (which happens in the
 * {@code runClient} / {@code runServer} / {@code runGameTestServer}
 * tasks, not in a plain JUnit run). The static init of {@code Items}
 * itself registers its constants through the registry, and the registry
 * calls back into GameEvent dispatch, which is where the bootstrap
 * guard fires. We avoid the trap entirely by reading only type
 * identities (no static fields) and verifying class equality on the
 * spots that prove the merged JAR is on the classpath.
 */
final class NeoforgeTestHarness {

    @Test
    @DisplayName("MC types resolve and the merged JAR is on the classpath")
    void verifyMergedJarOnClasspath() {
        // The block-position type is MC's most basic immutable value
        // object — a class load with no static init that touches a
        // registry. A NoClassDefFoundError here means the ModDev merged
        // JAR is missing or the version pin drifted.
        BlockPos origin = new BlockPos(0, 0, 0);
        assertNotNull(origin, "BlockPos(0,0,0) constructs from the merged JAR");
        assertNotNull(BlockPos.ZERO, "BlockPos.ZERO is non-null");
        assertSame(origin.getX(), BlockPos.ZERO.getX(), "BlockPos equality holds on coords");
        assertSame(origin.getY(), BlockPos.ZERO.getY(), "BlockPos equality holds on coords");
        assertSame(origin.getZ(), BlockPos.ZERO.getZ(), "BlockPos equality holds on coords");

        // ResourceLocation is the namespace path/separator parser — used
        // by every MC resource id. Resolving the .class proves it loaded.
        assertNotNull(ResourceLocation.class, "ResourceLocation.class resolves");

        // BuiltInRegistries loads as a type only — we do NOT read .ITEM or
        // .size() because those require Bootstrap.run(). The class-load is
        // enough proof for the canary; registry behaviour is verified in
        // the wire site (Town.applyStockToReserve reads through it via a
        // path that catches the bootstrap guard).
        assertNotNull(BuiltInRegistries.class, "BuiltInRegistries.class resolves");

        // Item and Level are the parent classes Town and TickScheduler
        // import. Resolving them proves the merged JAR covers the full
        // class hierarchy Town pulls in.
        assertNotNull(Item.class, "Item.class resolves");
        assertNotNull(Level.class, "Level.class resolves");
    }
}