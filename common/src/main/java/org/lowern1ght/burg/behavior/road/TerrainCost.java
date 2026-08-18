package org.lowern1ght.burg.behavior.road;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-block movement cost for the road planner's A* search.
 *
 * <p>Costs are an integer weight on the edge into a cell. Lower is cheaper;
 * higher is harder. The planner minimises the sum of cell costs along the
 * path, so a grassy plain beats a forest patches beats stone beats lava.
 * Unknown blocks default to a moderate cost (10) so the planner does not
 * drown in a sea of free moves just because an unrecognised block fell out
 * of a mod's registration.
 *
 * <p>The defaults below are calibrated against the author-supplied NBT
 * corpus: grass and dirt are cheap, sand slightly less so (loose footing),
 * wood and leaves are moderate (forest paths are fine but slower), water
 * is expensive (the planner would prefer a detour, but BRIDGE classification
 * can override that), and stone is very expensive (the planner should only
 * ever cross stone if no easier path exists).
 *
 * <p>Test workbenches (and modded worlds with non-vanilla blocks) can
 * adjust on the fly via {@link #registerCost}.
 */
public final class TerrainCost {

    private final Map<Block, Integer> blockCost = new HashMap<>();

    public TerrainCost() {
        registerDefaults();
    }

    private void registerDefaults() {
        // earth is cheap
        blockCost.put(Blocks.GRASS_BLOCK, 1);
        blockCost.put(Blocks.DIRT, 1);
        blockCost.put(Blocks.COARSE_DIRT, 1);
        blockCost.put(Blocks.SAND, 2);
        blockCost.put(Blocks.GRAVEL, 3);
        // forest is moderate
        blockCost.put(Blocks.OAK_LOG, 5);
        blockCost.put(Blocks.SPRUCE_LOG, 5);
        blockCost.put(Blocks.BIRCH_LOG, 5);
        blockCost.put(Blocks.OAK_LEAVES, 4);
        blockCost.put(Blocks.SPRUCE_LEAVES, 4);
        blockCost.put(Blocks.BIRCH_LEAVES, 4);
        // water is expensive (a tunnel/bridge decision can override)
        blockCost.put(Blocks.WATER, 15);
        blockCost.put(Blocks.LAVA, 100);
        // stone is very expensive
        blockCost.put(Blocks.STONE, 20);
        blockCost.put(Blocks.COBBLESTONE, 20);
        blockCost.put(Blocks.DEEPSLATE, 25);
        blockCost.put(Blocks.GRANITE, 20);
        blockCost.put(Blocks.DIORITE, 20);
        blockCost.put(Blocks.ANDESITE, 20);
        // default for any unknown block — air is the cheapest sensible fill
        blockCost.put(Blocks.AIR, 1);
    }

    /** Cost to enter a cell of {@code block}. Unknown blocks → 10. */
    public int costFor(Block block) {
        return blockCost.getOrDefault(block, 10);
    }

    /** Override or add a cost at runtime (tests, world datapacks). */
    public void registerCost(Block block, int cost) {
        blockCost.put(block, cost);
    }
}
