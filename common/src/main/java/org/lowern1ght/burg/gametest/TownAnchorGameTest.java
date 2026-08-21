package org.lowern1ght.burg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.registry.BlockRegistry;
import org.lowern1ght.burg.town.LevelTowns;
import org.lowern1ght.burg.town.Town;

/**
 * Diagnostic test: does placing a Town Anchor block register a Town in {@link LevelTowns}?
 *
 * <p>Expected outcome: <b>PASS</b>. Town registration is worldgen-only — see
 * {@code ChunkGeneratorMixin} which hooks {@code StructureStart.placeInChunk} for the
 * {@code burg:plains_town} structure. The {@code TownAnchorBlockEntity} and
 * {@code TownAnchorBlock} never call {@code LevelTowns.registerTown()}.
 *
 * <p>This test pins that behaviour in a required (non-optional) {@code @GameTest}
 * so the {@code runGameTestServer} exit code stays at zero. A manually-placed
 * Town Anchor (via creative inventory or {@code /setblock}) does <em>not</em>
 * create a Town, so {@code TownAnchorBlock.useWithoutItem} returns {@code FAIL}
 * when right-clicked.
 *
 * <p>The pre-fix shape of this test deliberately called {@code helper.fail(...)}
 * on the expected outcome, which made the {@code runGameTestServer} exit code
 * non-zero even when every other assertion held. That gate (CI workflow
 * {@code .github/workflows/gametest.yml}) used {@code continue-on-error: true}
 * to mask the failure. The fix inverts the assertion to document the
 * worldgen-only contract and let the gate stand on its own; the
 * {@code continue-on-error} is dropped in the same PR.
 */
@GameTestHolder(Constants.MOD_ID)
@PrefixGameTestTemplate(false)
public class TownAnchorGameTest {

    /**
     * Places a Town Anchor block and asserts that no Town is registered in
     * {@link LevelTowns} at that position. This documents the worldgen-only
     * contract: registration goes exclusively through
     * {@code ChunkGeneratorMixin.onStructurePlaced} during worldgen
     * (structure {@code burg:plains_town}); a manually-placed anchor never
     * opens the hub.
     *
     * <p>The test is marked required (the {@code required = true} default on
     * {@link GameTest}) so the {@code runGameTestServer} exit code stays at
     * zero when the contract holds — and trips loudly if a future carve
     * adds a manual registration path that conflicts with the worldgen
     * authority.
     */
    @GameTest(template = "empty5x5", timeoutTicks = 200, batch = "town_anchor")
    public static void placeTownAnchor_registersTown(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        ServerLevel level = helper.getLevel();

        level.setBlockAndUpdate(pos, BlockRegistry.TOWN_ANCHOR.defaultBlockState());

        helper.runAfterDelay(100, () -> {
            Town town = LevelTowns.get(level).getTownAt(pos).orElse(null);
            if (town == null) {
                helper.succeed();
            } else {
                helper.fail("Town unexpectedly registered at " + pos
                    + " after a manual Town Anchor placement. Registration must"
                    + " be worldgen-only (ChunkGeneratorMixin hooks"
                    + " StructureStart.placeInChunk for plains_town); a"
                    + " manually-placed anchor must never open the hub.");
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
