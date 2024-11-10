package org.dawnoftime.onceuponatown.world.structure;

import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import org.dawnoftime.onceuponatown.town.TownMap;
import org.dawnoftime.onceuponatown.town.TownMapUtils;
import org.dawnoftime.onceuponatown.building.placement.BuildPlacement;
import org.dawnoftime.onceuponatown.building.placement.BuildingPlacement;
import org.dawnoftime.onceuponatown.building.placement.RoadPlacement;
import org.dawnoftime.onceuponatown.world.structure.pieces.BuildingPiece;
import org.dawnoftime.onceuponatown.world.structure.pieces.PathPiece;
import org.dawnoftime.onceuponatown.world.structure.pieces.TownDataBuildingPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.*;

public class TownGenerator {
    public static final int STARTER_PACK_SIZE = 21;
    public static final int HEIGHTMAP_SCAN_RADIUS = 100;
    public static final BuildingInfo[] TEST_BUILDINGS = new BuildingInfo[] {
            new BuildingInfo(Ouat.createOuatResource("plains/bigchurch"), 19, 32),
            new BuildingInfo(Ouat.createOuatResource("plains/bighouse"), 13, 11),
            new BuildingInfo(Ouat.createOuatResource("plains/bighousefront"), 9, 8),
            new BuildingInfo(Ouat.createOuatResource("plains/bighousestand"), 15, 10),
            new BuildingInfo(Ouat.createOuatResource("plains/church"), 15, 24),
            new BuildingInfo(Ouat.createOuatResource("plains/cowshed"), 16, 11),
            new BuildingInfo(Ouat.createOuatResource("plains/doubledeckhouse"), 7, 15),
            new BuildingInfo(Ouat.createOuatResource("plains/fountainplace"), 13, 13),
            new BuildingInfo(Ouat.createOuatResource("plains/leathershop"), 14, 9),
            new BuildingInfo(Ouat.createOuatResource("plains/littlefarm"), 9, 7),
            new BuildingInfo(Ouat.createOuatResource("plains/lonegarden"), 14, 13),
            new BuildingInfo(Ouat.createOuatResource("plains/loneresources"), 14, 11),
            new BuildingInfo(Ouat.createOuatResource("plains/mediumhouse"), 11, 10),
            new BuildingInfo(Ouat.createOuatResource("plains/merchantshop"), 15, 12),
            new BuildingInfo(Ouat.createOuatResource("plains/smallfarm"), 17, 10),
            new BuildingInfo(Ouat.createOuatResource("plains/smallhouse"), 8, 9),
            new BuildingInfo(Ouat.createOuatResource("plains/smallhousefancy"), 14, 10),
            new BuildingInfo(Ouat.createOuatResource("plains/smallhousegarden"), 14, 8),
            new BuildingInfo(Ouat.createOuatResource("plains/smallmarket"), 25, 18),
            new BuildingInfo(Ouat.createOuatResource("plains/soldierhouse"), 13, 13),
            new BuildingInfo(Ouat.createOuatResource("plains/wildspot"), 10, 6)
    };

    public static void generatePiecesWorldGen(StructureTemplateManager manager, BlockPos townCenterPos, List<BuildingType> starter_pack, StructurePieceAccessor pieces, WorldgenRandom random) {
        Ouat.info("Town at + " + townCenterPos.toShortString() + " : started generating pieces");
        List<BuildingInfo> availableBuildings = new LinkedList<>(Arrays.asList(TEST_BUILDINGS));
        List<BuildingInfo> starterPack = new ArrayList<>();
        for (int i = 0; i < STARTER_PACK_SIZE; ++i) {
            BuildingInfo building = availableBuildings.remove(Mth.nextInt(random, 0, availableBuildings.size() - 1));
            starterPack.add(building);
            if (availableBuildings.isEmpty()) {
                availableBuildings.addAll(Arrays.asList(TEST_BUILDINGS));
            }
        }
        TownMap townMap = createTownMap(townCenterPos, starterPack);
        Ouat.info("Town at + " + townCenterPos.toShortString() + " : created town map");
        List<BuildingPlacement> mapBuildingList = new ArrayList<>();
        List<RoadPlacement> mapRoadList = new ArrayList<>();

        HashMap<Integer, BuildPlacement> mapBuilds = townMap.getBuilds();
        for (Integer key : mapBuilds.keySet()) {
            BuildPlacement build = mapBuilds.get(key);
            if (build instanceof BuildingPlacement building) {
                mapBuildingList.add(building);
            } else if (build instanceof RoadPlacement path) {
                mapRoadList.add(path);
            }
        }
        // Buildings
        for (int i = 0; i < starterPack.size(); ++i) {
            BuildingPlacement building = mapBuildingList.get(i);
            Rotation rotation = rotFromDir(building.getDirection() != null ? building.getDirection() : Direction.NORTH);
            TownMapUtils.Corner corner = cornerFromDir(building.getDirection() != null ? building.getDirection() : Direction.NORTH);
            ResourceLocation buildingName = starterPack.get(i).name;
            if (i == 0) {
                var piece = new TownDataBuildingPiece(manager, buildingName, building.getCornerPos(corner), rotation, BuildingType.DEFAULT_TYPE, townMap);
                pieces.addPiece(piece);
            } else {
                var piece = new BuildingPiece(manager, buildingName, building.getCornerPos(corner), rotation, BuildingType.DEFAULT_TYPE);
                pieces.addPiece(piece);
            }
        }
        // Paths
        mapRoadList.forEach((mapRoad) -> {
            PathPiece pathPiece = new PathPiece(mapRoad.getOriginPos(), mapRoad.getSizeX(), mapRoad.getSizeZ());
            pieces.addPiece(pathPiece);
        });

        Ouat.info("Town at + " + townCenterPos.toShortString() + " : finished generating pieces");
    }

    private static TownMap createTownMap(BlockPos townCenterPos, List<BuildingInfo> starterPack) {
        TownMap townMap = new TownMap(townCenterPos);
        starterPack.forEach((buildingInfo -> townMap.addBuilding(new BuildingPlacement(buildingInfo.sizeXNorth, buildingInfo.sizeZNorth))));
        return townMap;
    }

    public static void generateTownPlan(StructurePiecesBuilder builder, int[][] terrainHeightmap) {

    }

    private static Rotation rotFromDir(Direction dir) {
        return switch (dir) {
            case NORTH, DOWN, UP -> Rotation.NONE;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.CLOCKWISE_90;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
        };
    }

    private static TownMapUtils.Corner cornerFromDir(Direction dir) {
        return switch (dir) {
            case NORTH, DOWN, UP -> TownMapUtils.Corner.NORTH_WEST;
            case SOUTH -> TownMapUtils.Corner.SOUTH_EAST;
            case EAST -> TownMapUtils.Corner.NORTH_EAST;
            case WEST -> TownMapUtils.Corner.SOUTH_WEST;
        };
    }

    private int[][] getTerrainHeightmap(BlockPos origin, int radius, ChunkGenerator generator, LevelHeightAccessor heightAccessor, RandomState randomState) {
        //generator.getFirstFreeHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState);
        return null;
    }

    public record BuildingInfo(ResourceLocation name, int sizeXNorth, int sizeZNorth) {}
}
