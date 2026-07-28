package org.dawnoftime.onceuponatown.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.registry.BlockRegistry;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;

/**
 * Diagnostic test: does placing a Town Anchor block register a Town in {@link LevelTowns}?
 *
 * <p>Expected outcome: <b>FAIL</b>. Town registration is worldgen-only — see
 * {@code ChunkGeneratorMixin} which hooks {@code StructureStart.placeInChunk} for the
 * {@code onceuponatown:plains_town} structure. The {@code TownAnchorBlockEntity} and
 * {@code TownAnchorBlock} never call {@code LevelTowns.registerTown()}.
 *
 * <p>This test documents that behaviour: a manually-placed Town Anchor (via creative
 * inventory or {@code /setblock}) does <em>not</em> create a Town, so
 * {@code TownAnchorBlock.useWithoutItem} returns {@code FAIL} when right-clicked.
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class TownAnchorGameTest {

    /**
     * Places a Town Anchor block and checks if a Town gets registered in LevelTowns.
     *
     * <p>If this test FAILS with "No Town registered" — that confirms Town
     * registration does NOT happen on block placement. The registration path is
     * exclusively via {@code ChunkGeneratorMixin.onStructurePlaced} during worldgen
     * (structure {@code onceuponatown:plains_town}).
     */
    @GameTest(template = "empty5x5", timeoutTicks = 200, batch = "town_anchor")
    public static void placeTownAnchor_registersTown(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();

        level.setBlockAndUpdate(pos, BlockRegistry.TOWN_ANCHOR.defaultBlockState());

        helper.runAfterDelay(100, () -> {
            Town town = LevelTowns.get(level).getTownAt(pos).orElse(null);
            if (town == null) {
                helper.fail("No Town registered at " + pos
                    + " after placing Town Anchor block. Registration is worldgen-only"
                    + " (ChunkGeneratorMixin hooks StructureStart.placeInChunk for plains_town)."
                    + " A manually-placed anchor will never open the hub.");
            } else {
                helper.succeed();
            }
        });
    }

    /**
     * Smoke test: verifies the mixin infrastructure loaded correctly by checking
     * that {@link LevelTowns} (which the mixin calls during worldgen) is available
     * and non-null. If the refmap is missing or the mixin config is malformed, the
     * mod fails to load entirely and this test never runs.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 100, batch = "town_mixin")
    public static void levelTowns_availableAfterMixinLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LevelTowns levelTowns = LevelTowns.get(level);

        if (levelTowns == null) {
            helper.fail("LevelTowns.get(level) returned null — mod or mixin infrastructure failed to load");
        } else {
            helper.succeed();
        }
    }
}
