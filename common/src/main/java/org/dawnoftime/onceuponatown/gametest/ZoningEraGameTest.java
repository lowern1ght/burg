package org.dawnoftime.onceuponatown.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.behavior.intent.BuildIntent;
import org.dawnoftime.onceuponatown.behavior.intent.IntentCost;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.UUID;

/**
 * DIST-1 zoning + DIST-2 era gate GameTests.
 *
 * <p>Three concerns under test:
 * <ul>
 *   <li>{@link Town#zoneOf(BlockPos)} returns the expected zone (CORE / INDUSTRY
 *       / MILITARY) at the right distance from the anchor.</li>
 *   <li>{@link Town#getAnchorPos()} returns the centroid of placed buildings
 *       (or BlockPos.ZERO when the town is empty).</li>
 *   <li>{@link BuildIntent#canResolve(Town)} respects the era gate — currently
 *       a no-op because {@code eraFor} returns 0 for every defId, but the gate
 *       is wired and the path through the check is exercised.</li>
 * </ul>
 *
 * <p>The {@link BuildIntent} record gains a {@code requiredZone} field on this
 * slice. A null zone is treated as malformed and the intent cannot resolve.
 * The actual position-picking enforcement inside
 * {@link Town#tryAddToConstructionQueue(String)} is deferred to a follow-up
 * commit; this test only exercises the intent-level gates.
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class ZoningEraGameTest {

    private static final ResourceLocation SETTLEMENT =
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "settlement");

    // -----------------------------------------------------------------------------------
    // Town.Zone + Town.zoneOf
    // -----------------------------------------------------------------------------------

    /**
     * An empty town's anchor is {@link BlockPos#ZERO}; zoneOf returns CORE for
     * positions within 32 blocks of origin, INDUSTRY for 32-64, MILITARY for
     * beyond. The ROAD zone is reserved for future road-segment detection.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 30, batch = "zoning")
    public static void zoneOf_emptyTown_usesZeroAnchor(GameTestHelper helper) {
        Town town = new Town();
        town.setName("ZoneTestEmpty");

        helper.assertTrue(town.getAnchorPos().equals(BlockPos.ZERO),
            "empty town's anchor is BlockPos.ZERO (was " + town.getAnchorPos() + ")");

        // Within 32 blocks of origin → CORE.
        helper.assertTrue(town.zoneOf(new BlockPos(10, 0, 10)) == Town.Zone.CORE,
            "position (10,0,10) is CORE (was " + town.zoneOf(new BlockPos(10, 0, 10)) + ")");
        helper.assertTrue(town.zoneOf(new BlockPos(31, 0, 0)) == Town.Zone.CORE,
            "position (31,0,0) is CORE (boundary)");

        // 32-64 blocks → INDUSTRY.
        helper.assertTrue(town.zoneOf(new BlockPos(40, 0, 0)) == Town.Zone.INDUSTRY,
            "position (40,0,0) is INDUSTRY (was " + town.zoneOf(new BlockPos(40, 0, 0)) + ")");
        helper.assertTrue(town.zoneOf(new BlockPos(64, 0, 0)) == Town.Zone.INDUSTRY,
            "position (64,0,0) is INDUSTRY (boundary)");

        // Beyond 64 blocks → MILITARY.
        helper.assertTrue(town.zoneOf(new BlockPos(100, 0, 0)) == Town.Zone.MILITARY,
            "position (100,0,0) is MILITARY (was " + town.zoneOf(new BlockPos(100, 0, 0)) + ")");

        helper.succeed();
    }

    /**
     * Once a building is registered, the anchor shifts to its centroid. zoneOf
     * is computed relative to the centroid, not relative to BlockPos.ZERO.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 30, batch = "zoning")
    public static void zoneOf_singleBuilding_anchorsAtBuilding(GameTestHelper helper) {
        Town town = new Town();
        town.setName("ZoneTestAnchored");

        ServerLevel level = helper.getLevel();
        BlockPos buildingPos = helper.absolutePos(new BlockPos(50, 1, 50));
        town.registerBuilding(buildingPos, "settlement",
            java.util.List.of(),
            new BoundingBox(50, 0, 50, 51, 1, 51),
            Rotation.NONE);

        helper.assertTrue(town.getAnchorPos().equals(buildingPos),
            "single-building town anchors at the building (was " + town.getAnchorPos() + ")");

        // Right next to the building → CORE.
        helper.assertTrue(town.zoneOf(buildingPos) == Town.Zone.CORE,
            "the building position itself is CORE");
        // 40 blocks away → INDUSTRY.
        helper.assertTrue(town.zoneOf(buildingPos.offset(40, 0, 0)) == Town.Zone.INDUSTRY,
            "40 blocks from anchor is INDUSTRY");
        // 100 blocks away → MILITARY.
        helper.assertTrue(town.zoneOf(buildingPos.offset(100, 0, 0)) == Town.Zone.MILITARY,
            "100 blocks from anchor is MILITARY");

        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // BuildIntent.canResolve — DIST-1 zone + DIST-2 era wiring
    // -----------------------------------------------------------------------------------

    /**
     * BuildIntent.canResolve must pass when the era gate is satisfied (it
     * always is, since {@code eraFor} returns 0 for the first slice). The
     * sanity check: canResolve stays true across several town eras, proving
     * the era gate is wired but inert — once BuildingDataHandler exposes era,
     * this test will need a sharper assertion.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "zoning")
    public static void buildIntent_canResolve_eraGate_isWiredButInert(GameTestHelper helper) {
        Town town = new Town();
        town.setName("EraTest");
        town.setBuilderNpcIdAtSlot(0, UUID.randomUUID());

        // Town at era 0 → BUILD can resolve (eraFor=0, gate passes).
        BuildIntent intent0 = new BuildIntent(SETTLEMENT, town, 5,
            IntentCost.empty(), Town.Zone.CORE);
        helper.assertTrue(intent0.canResolve(town),
            "era=0 with requiredZone=CORE passes canResolve (was false)");

        // Town at era 5 → still passes (eraFor still returns 0, gate inert).
        setTownEraViaReflection(town, 5);
        BuildIntent intent5 = new BuildIntent(SETTLEMENT, town, 5,
            IntentCost.empty(), Town.Zone.CORE);
        helper.assertTrue(intent5.canResolve(town),
            "era=5 still passes canResolve (eraFor returns 0, gate inert)");

        // Town at era 100 → still passes.
        setTownEraViaReflection(town, 100);
        BuildIntent intent100 = new BuildIntent(SETTLEMENT, town, 5,
            IntentCost.empty(), Town.Zone.CORE);
        helper.assertTrue(intent100.canResolve(town),
            "era=100 still passes canResolve (eraFor returns 0, gate inert)");

        helper.succeed();
    }

    /**
     * BuildIntent.canResolve must reject a null requiredZone (the field is
     * mandatory for the first slice — full zone enforcement is deferred).
     * Sanity check: the gate fires when the field is missing.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 30, batch = "zoning")
    public static void buildIntent_canResolve_nullZone_isRejected(GameTestHelper helper) {
        Town town = new Town();
        town.setName("NullZoneTest");
        town.setBuilderNpcIdAtSlot(0, UUID.randomUUID());

        BuildIntent nullZone = new BuildIntent(SETTLEMENT, town, 5,
            IntentCost.empty(), null);
        helper.assertTrue(!nullZone.canResolve(town),
            "canResolve rejects null requiredZone (was true)");

        helper.succeed();
    }

    // -----------------------------------------------------------------------------------
    // helpers
    // -----------------------------------------------------------------------------------

    /**
     * Town.currentEra is a private field with no setter (it is persisted from
     * NBT and only mutated via {@code advanceEra}). For this test we need to
     * set arbitrary era values, so reflection is the cleanest seam — keeps
     * production code unchanged.
     */
    private static void setTownEraViaReflection(Town town, int era) {
        try {
            java.lang.reflect.Field f = Town.class.getDeclaredField("currentEra");
            f.setAccessible(true);
            f.setInt(town, era);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("failed to set Town.currentEra via reflection", e);
        }
    }
}
