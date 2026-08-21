package org.lowern1ght.burg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.domain.shared.ItemId;
import org.lowern1ght.burg.infrastructure.config.BuildingOutputCap;
import org.lowern1ght.burg.town.PlacedBuilding;

/**
 * Live-MC GameTest for {@link PlacedBuilding#forceAdd} on the real Minecraft
 * server bootstrap (no {@code Bootstrap} reflection trick — this target IS the
 * real bootstrap). The two test cases cover the additive surface that
 * {@code PlacedBuildingForceAddOutputCapTest} pins on a bare-JUnit classpath:
 *
 * <ol>
 *   <li><b>{@link #forceAdd_accumulatesIntoOutputLedger}:</b> at the loaded
 *       spec's default cap (256), three {@code forceAdd} calls leave the
 *       three distinct Items on the per-instance {@link
 *       PlacedBuilding#outputLedger()}. The SoT ledger is what the cap reads
 *       — the bare-JVM test pins it; this live run pins that the
 *       real-Items path also accumulates (the bare-JVM {@code resolveItem}
 *       path skips Items that trip {@code Bootstrap.checkBootstrapCalled}).</li>
 *   <li><b>{@link #forceAdd_atCap_dropsOldestViaFifo}:</b> with the cap
 *       overridden to 2 via {@link BuildingOutputCap#setCurrent}, three
 *       {@code forceAdd} calls leave only the two newest Items on the
 *       ledger. The cap's FIFO drain is observable on the live server
 *       — same discipline the {@code BUILDING_OUTPUT_CAP_PER_INSTANCE}
 *       Cloth knob (ADR-0027) enforces.</li>
 * </ol>
 *
 * <p>The cap override test uses {@link BuildingOutputCap#resetCurrent()} in a
 * finally block so the static slot does not leak into the next test in the
 * batch (the additive default of 256 is what {@code FMLCommonSetupEvent}
 * pushed via {@code BurgConfig.refreshBuildingOutputCap}).
 *
 * <p>What this test does NOT cover (intentional residuals):
 * <ul>
 *   <li>{@code produce()} — the per-tick path goes through
 *       {@code ProductionManager}; the cap discipline is identical
 *       (both paths call {@code applyOutputCap}) so the {@code forceAdd}
 *       seam is the canonical pin.</li>
 *   <li>Cross-building ledger aggregation (a per-building cap, not a
 *       town-level one — act-5 town cap is a separate concern).</li>
 * </ul>
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class PlacedBuildingLiveTest {

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "placed_building")
    public static void forceAdd_accumulatesIntoOutputLedger(GameTestHelper helper) {
        PlacedBuilding building = new PlacedBuilding(
            "test_def", BlockPos.ZERO, new BoundingBox(0, 0, 0, 1, 1, 1), Rotation.NONE);

        building.forceAdd(Items.WHEAT, 5);
        building.forceAdd(Items.CARROT, 3);
        building.forceAdd(Items.POTATO, 2);

        helper.assertTrue(
            building.outputLedger().get(ItemId.of("minecraft:wheat")) == 5,
            "wheat quantity accumulates on the SoT ledger");
        helper.assertTrue(
            building.outputLedger().get(ItemId.of("minecraft:carrot")) == 3,
            "carrot quantity accumulates on the SoT ledger");
        helper.assertTrue(
            building.outputLedger().get(ItemId.of("minecraft:potato")) == 2,
            "potato quantity accumulates on the SoT ledger");
        helper.assertTrue(
            building.outputLedger().size() == 3,
            "ledger holds all 3 distinct Items at the loaded default cap");
        helper.assertTrue(
            building.getStockedItems().contains(Items.WHEAT)
                && building.getStockedItems().contains(Items.CARROT)
                && building.getStockedItems().contains(Items.POTATO),
            "the MC mirror reflects all three Items — SoT and visual side stay in lockstep");

        helper.succeed();
    }

    @GameTest(template = "empty5x5", timeoutTicks = 60, batch = "placed_building")
    public static void forceAdd_atCap_dropsOldestViaFifo(GameTestHelper helper) {
        BuildingOutputCap original = BuildingOutputCap.current();
        BuildingOutputCap.setCurrent(new BuildingOutputCap(2));
        try {
            PlacedBuilding building = new PlacedBuilding(
                "test_def", BlockPos.ZERO, new BoundingBox(0, 0, 0, 1, 1, 1), Rotation.NONE);

            building.forceAdd(Items.WHEAT, 1);
            building.forceAdd(Items.CARROT, 2);
            building.forceAdd(Items.POTATO, 3);  // overflow -> FIFO drop wheat

            helper.assertTrue(
                building.outputLedger().size() == 2,
                "post-cap: ledger carries exactly 2 entries — wheat was FIFO-dropped");
            helper.assertTrue(
                building.outputLedger().get(ItemId.of("minecraft:wheat")) == 0,
                "wheat is absent from the SoT ledger");
            helper.assertTrue(
                building.outputLedger().get(ItemId.of("minecraft:carrot")) == 2,
                "carrot quantity survives");
            helper.assertTrue(
                building.outputLedger().get(ItemId.of("minecraft:potato")) == 3,
                "potato quantity survives");
            helper.assertFalse(
                building.getStockedItems().contains(Items.WHEAT),
                "the MC mirror dropped wheat too — SoT and visual side stay in lockstep");
            helper.assertTrue(
                building.getStockedItems().contains(Items.CARROT)
                    && building.getStockedItems().contains(Items.POTATO),
                "carrot and potato remain on the MC mirror");

            helper.succeed();
        } finally {
            BuildingOutputCap.setCurrent(original);
        }
    }
}
