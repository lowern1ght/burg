package org.dawnoftime.onceuponatown.town.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.town.Town;
import org.dawnoftime.onceuponatown.building.type.BuildType;

import java.util.List;

public class TownGenerator {

    /**
     *
     * @param culture
     * @param builder
     * @param context
     */
    public static void tryGenerateTown(Culture culture, StructurePiecesBuilder builder, Structure.GenerationContext context){
        StructureTemplateManager manager = context.structureTemplateManager();
        int townHeight = context.chunkGenerator().getFirstOccupiedHeight(context.chunkPos().getMinBlockX(), context.chunkPos().getMinBlockZ(), Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        BlockPos townCenterPos = new BlockPos(context.chunkPos().getMinBlockX(), townHeight, context.chunkPos().getMinBlockZ());
        RandomSource rand = context.random();
        List<BuildType> starterPack = culture.getRandomStarterPack(rand);

        Ouat.info("Town at + " + townCenterPos.toShortString() + " : started generating pieces...");
    }

    public static boolean addBuilding(Town town, BuildType buildingType){
        return true;
    }

    private static void townStarterX(RandomSource rand, Culture culture, Town town){

    }
}
