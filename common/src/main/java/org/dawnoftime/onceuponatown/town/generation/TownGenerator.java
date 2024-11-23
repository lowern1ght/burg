package org.dawnoftime.onceuponatown.town.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.placement.RoadPlacement;
import org.dawnoftime.onceuponatown.building.type.BuildType;
import org.dawnoftime.onceuponatown.building.type.SliceBuildType;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.town.Town;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import org.dawnoftime.onceuponatown.town.generation.bud.BuildBud;

import java.util.List;

import static org.dawnoftime.onceuponatown.Config.DEFAULT_PATH_LENGTH;

public class TownGenerator {

    /**
     *
     * @param culture
     * @param builder
     * @param context
     */
    public static void tryGenerateTown(Culture culture, StructurePiecesBuilder builder, Structure.GenerationContext context){
        StructureTemplateManager manager = context.structureTemplateManager();
        StructurePieceAccessor accessor = context.
        int townHeight = context.chunkGenerator().getFirstOccupiedHeight(context.chunkPos().getMinBlockX(), context.chunkPos().getMinBlockZ(), Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        BlockPos townCenterPos = new BlockPos(context.chunkPos().getMinBlockX(), townHeight, context.chunkPos().getMinBlockZ());
        RandomSource rand = context.random();
        List<BuildType> starterPack = culture.getRandomStarterPack(rand);
        SliceBuildType wideRoad = culture.getSliceBuild(Culture.ROAD_TYPE_NAME); //TODO to replace with wide road.
        boolean flipped = rand.nextBoolean(); //TODO Will be used to decided the direction of the central road.

        Ouat.info("Town at + " + townCenterPos.toShortString() + " : started generating pieces...");
        TownMap townMap = new TownMap(townCenterPos);

        // First let's put the vertical big path, with length of 2 * mini_size + big_width
        int halfBigPath = wideRoad.getWidth() / 2;
        BuildBud firstBuildBud = BuildBud.createBud(townMap, townMap.getCenter().offset(-halfBigPath, 0, -halfBigPath - DEFAULT_PATH_LENGTH), TownMapUtils.Corner.NORTH_WEST, new Direction[]{Direction.NORTH});
        RoadPlacement mainPath = new RoadPlacement(2 * DEFAULT_PATH_LENGTH + wideRoad.getWidth(), true);
        boolean success = townMap.tryBuild(mainPath, firstBuildBud);


    }

    public static boolean addBuilding(Town town, BuildingType buildingType){
        return true;
    }
}
