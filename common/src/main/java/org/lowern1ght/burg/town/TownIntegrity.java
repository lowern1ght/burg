package org.lowern1ght.burg.town;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.lowern1ght.burg.datapack.BuildingDataHandler;
import org.lowern1ght.burg.registry.BlockRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Keeps a saved town and the world it lives in agreeing with each other.
 *
 * <p><b>The town is the record in {@link LevelTowns}; the anchor block is only its door.</b> That
 * distinction was not being enforced in either direction, and both failures are silent. Lose the
 * block and the record survives forever with nothing to open it: the panel is unreachable, the
 * builders keep working for a settlement the player can no longer see, and {@code getNearestTown}
 * still hands the ghost out to every citizen that spawns nearby. Lose the record and the block
 * sits there answering right-clicks with nothing at all.
 *
 * <p>The block is well defended against the ordinary player — hardness {@code -1} and blast
 * resistance {@code Float.MAX_VALUE}, so no pickaxe, no creeper, no TNT — and it holds a block
 * entity, so no piston can move it. What is left is a creative-mode click, {@code /setblock} and
 * {@code /fill}, a wither (blast resistance does not stop one), and any other mod that writes
 * blocks. None of those can be prevented, so instead of trying, this heals: the record knows
 * exactly where its door goes, so the door is put back.
 */
public final class TownIntegrity {

    private static final Logger LOGGER = LoggerFactory.getLogger(TownIntegrity.class);

    private TownIntegrity() {
    }

    /**
     * Restores any town anchor missing from a chunk that has just loaded.
     *
     * <p>Chunk load is the hook because it is the only moment the block is both reachable and
     * cheap to check. A sweep at server start would have to force-load a chunk per town for
     * settlements nobody is anywhere near; a tick loop would re-scan forever. Here every anchor is
     * verified exactly when its chunk comes into memory, which also covers damage done while the
     * chunk was unloaded — a {@code /fill} from far away, or another mod's world edit.
     *
     * <p>Towns are a handful per world, so the linear scan over them costs less than the
     * long-to-position unpack it does per entry. Deliberately not indexed: an index would need
     * invalidating on every register, and a stale index that says "no anchor here" fails silently,
     * which is the exact class of bug this file exists to close.
     *
     * <p>The check reads the {@link ChunkAccess} that was handed to us rather than the level, so it
     * costs one array lookup and needs no server task; the write is the only part that gets
     * deferred, and only when there is one to make. The first version scheduled a task per chunk
     * load whether anything was wrong or not, which is thousands of empty tasks for a player
     * flying.
     */
    public static void healAnchors(ServerLevel level, ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        for (Map.Entry<Long, Town> entry : LevelTowns.get(level).getAllTownEntries()) {
            BlockPos anchorPos = BlockPos.of(entry.getKey());
            if (anchorPos.getX() >> 4 != chunkPos.x || anchorPos.getZ() >> 4 != chunkPos.z) continue;
            if (chunk.getBlockState(anchorPos).is(BlockRegistry.TOWN_ANCHOR)) continue;

            Town town = entry.getValue();
            LOGGER.warn("[OUAT-INTEGRITY] Town anchor missing at {} -- restoring."
                + " town='{}' buildings={}", anchorPos, town.getName(), town.getBuildings().size());
            // Onto the server thread: a block write with UPDATE_ALL notifies neighbours and builds
            // a block entity, and the chunk-loading path is not where that belongs.
            level.getServer().execute(() -> {
                if (level.getBlockState(anchorPos).is(BlockRegistry.TOWN_ANCHOR)) return;
                level.setBlock(anchorPos, BlockRegistry.TOWN_ANCHOR.defaultBlockState(),
                    Block.UPDATE_ALL);
            });
        }
    }

    /**
     * Everything wrong with the towns in this level, one line per finding, worst first.
     *
     * <p>Reads only — it reports and repairs nothing, so it is safe to run at any time and its
     * output is evidence rather than a side effect. Empty means the level is consistent.
     */
    public static List<String> audit(ServerLevel level) {
        List<String> findings = new ArrayList<>();
        LevelTowns towns = LevelTowns.get(level);

        int unreadable = towns.getUnreadableTownCount();
        if (unreadable > 0) {
            findings.add(unreadable + " town(s) in this level's save could not be parsed and are"
                + " being held as raw data — they are NOT running. See the log at world load.");
        }

        for (Map.Entry<Long, Town> entry : towns.getAllTownEntries()) {
            BlockPos anchorPos = BlockPos.of(entry.getKey());
            Town town = entry.getValue();
            String who = "'" + town.getName() + "' at " + anchorPos;

            // Only meaningful where the chunk is in memory; an unloaded anchor is not a fault, and
            // saying so would train the reader to ignore this line.
            if (level.hasChunkAt(anchorPos)) {
                if (!level.getBlockState(anchorPos).is(BlockRegistry.TOWN_ANCHOR)) {
                    findings.add(who + ": anchor block MISSING (heals on next chunk load)");
                }
            } else {
                findings.add(who + ": anchor chunk not loaded, block unverified");
            }

            for (PlacedBuilding b : town.getBuildings()) {
                BuildingDef def = BuildingDataHandler.get(b.defId).orElse(null);
                if (def == null) {
                    findings.add(who + ": building '" + b.defId + "' has no definition —"
                        + " a datapack entry was renamed or removed");
                    continue;
                }
                int lvl = b.getUpgradeLevel();
                if (lvl > def.nbtLevels.size()) {
                    findings.add(who + ": building '" + b.defId + "' is at level " + lvl
                        + " but only " + def.nbtLevels.size() + " level NBT(s) exist");
                }
                // The level the building is AT must be loadable, or the next upgrade diffs
                // against nothing. This is how `settlement_lvl1` being corrupt stayed invisible.
                for (int i = 0; i < def.nbtLevels.size(); i++) {
                    var nbt = def.nbtLevels.get(i).nbt();
                    if (level.getStructureManager().get(nbt).isEmpty()) {
                        findings.add(who + ": '" + b.defId + "' level " + (i + 1)
                            + " NBT cannot be read (" + nbt + ") — that upgrade will refuse");
                    }
                }
            }
        }
        return findings;
    }
}
