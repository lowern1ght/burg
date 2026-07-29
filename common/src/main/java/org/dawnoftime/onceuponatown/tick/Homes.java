package org.dawnoftime.onceuponatown.tick;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.entity.CitizenNames;
import org.dawnoftime.onceuponatown.people.Person;
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.PlacedBuilding;
import org.dawnoftime.onceuponatown.town.Town;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Gives people beds to sleep in.
 *
 * <p>A person's home is <b>an actual bed at an actual position</b>, not a house they are counted
 * against. The difference matters as soon as they have a schedule: at dusk they walk somewhere, and
 * "the building you are notionally a resident of" is not somewhere you can walk to and lie down in.
 *
 * <p>Beds are found by reading the world, not the NBT. The author's houses carry their own beds —
 * measured, {@code house} has 2 and {@code house_3_lvl3} has 4 — and a building may have been
 * upgraded, half-built, or had a bed broken by a player since it was placed. The world is the only
 * copy of that state that is right.
 *
 * <p>One person per bed. Two people asleep in one bed is a thing Minecraft will happily render and
 * nobody wants to look at.
 */
public final class Homes {

    private static final Logger LOGGER = LoggerFactory.getLogger(Homes.class);

    /** Once every ten seconds. Housing changes at the pace of building, not of ticking. */
    private static final int EVERY_TICKS = 200;

    private Homes() {
    }

    public static void tick(ServerLevel level, Town town, long gameTime) {
        if (gameTime % EVERY_TICKS != 0) return;

        List<Person> homeless = town.people().needingHomes();
        if (homeless.isEmpty()) return;

        List<BlockPos> free = freeBeds(level, town);
        if (free.isEmpty()) return;

        int given = 0;
        for (Person person : homeless) {
            if (given >= free.size()) break;
            BlockPos bed = free.get(given++);
            person.setHomeKey(bed.asLong());
            LOGGER.debug("[OUAT-HOME] {} took the bed at {}", CitizenNames.of(person.id()), bed);
        }
        if (given > 0) LevelTowns.get(level).markDirty();
    }

    /**
     * Every bed in the town's own buildings that nobody has been assigned to.
     *
     * <p>Only the HEAD half of each bed is offered. A bed is two blocks and vanilla's own sleeping
     * uses the head; counting both would double the town's apparent housing and put two people in
     * one bed, which is the sort of thing that is obvious in a screenshot and invisible in code.
     *
     * <p>Skips a building whose chunk is not loaded rather than forcing it: a bed nobody can reach
     * is not a bed anybody can be sent to tonight, and assignment runs again in ten seconds.
     */
    private static List<BlockPos> freeBeds(ServerLevel level, Town town) {
        List<BlockPos> out = new ArrayList<>();
        for (PlacedBuilding building : town.getBuildings()) {
            BuildingDef def = BuildingDataHandler.get(building.getDefId()).orElse(null);
            if (def == null || def.residents <= 0) continue;   // not somewhere people live
            BoundingBox bb = building.bb;
            if (bb == null) continue;
            if (!level.isLoaded(new BlockPos(bb.minX(), bb.minY(), bb.minZ()))) continue;

            for (BlockPos pos : BlockPos.betweenClosed(
                    new BlockPos(bb.minX(), bb.minY(), bb.minZ()),
                    new BlockPos(bb.maxX(), bb.maxY(), bb.maxZ()))) {
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof BedBlock)) continue;
                if (state.getValue(BedBlock.PART) != BedPart.HEAD) continue;
                BlockPos bed = pos.immutable();
                if (town.people().occupants(bed.asLong()) == 0) out.add(bed);
            }
        }
        return out;
    }

    /**
     * Whether the bed a person was given is still a bed.
     *
     * <p>Called before sending anyone to sleep. A house can be upgraded, rebuilt or vandalised
     * between dusk and dusk, and walking to a coordinate where a bed used to be is exactly the
     * kind of fault that reads as broken pathfinding.
     */
    public static boolean stillABed(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return true;      // unknown is not the same as gone
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof BedBlock
            && state.getValue(BedBlock.PART) == BedPart.HEAD;
    }
}
