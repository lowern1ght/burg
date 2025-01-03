package org.dawnoftime.onceuponatown.town.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.dawnoftime.onceuponatown.Config;
import org.dawnoftime.onceuponatown.Utils;
import org.dawnoftime.onceuponatown.building.Building;
import org.dawnoftime.onceuponatown.building.NpcBuild;
import org.dawnoftime.onceuponatown.building.Road;
import org.dawnoftime.onceuponatown.building.SliceBuild;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import org.dawnoftime.onceuponatown.building.type.SliceBuildType;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.town.generation.bud.BuildBud;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.dawnoftime.onceuponatown.Config.DEFAULT_PATH_LENGTH;
import static org.dawnoftime.onceuponatown.Config.MINI_PATH_SPACE;
import static org.dawnoftime.onceuponatown.culture.Culture.ROAD_TYPE_NAME;
import static org.dawnoftime.onceuponatown.culture.Culture.WIDE_ROAD_TYPE_NAME;

public class ProtoTown {
    public static final RandomSource RANDOM_SOURCE = RandomSource.create();

    protected final UUID uuid;
    protected final Culture culture;
    private final String name;
    private BlockPos center;
    private final BlockPos.MutableBlockPos NWCorner;
    private final BlockPos.MutableBlockPos SECorner;
    private final List<BuildBud> buildBuds;
    private final List<NpcBuild> builds;
    // The variables below are not saved.
    private final BiFunction<Integer, Integer, Integer> getSurfaceY;
    private MapBlock[][] townMap;

    public ProtoTown(UUID uuid, Culture culture, String name, BlockPos center, BlockPos.MutableBlockPos NWCorner, BlockPos.MutableBlockPos SECorner, List<BuildBud> buildBuds, List<NpcBuild> builds, BiFunction<Integer, Integer, Integer> getSurfaceY) {
        this.uuid = uuid;
        this.culture = culture;
        this.name = name;
        this.center = center;
        this.NWCorner = NWCorner;
        this.SECorner = SECorner;
        this.buildBuds = buildBuds;
        this.builds = builds;
        this.getSurfaceY = getSurfaceY;
        this.createTownMap();
    }

    public ProtoTown(Culture culture, String name, BlockPos center, BiFunction<Integer, Integer, Integer> getSurfaceY) {
        this(Mth.createInsecureUUID(RANDOM_SOURCE), culture, name, center, center.mutable(), center.mutable(), new ArrayList<>(), new ArrayList<>(), getSurfaceY);
    }

    public CompoundTag writeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("UUID", this.uuid);
        tag.putString("Culture", this.culture.getId());
        tag.putString("Name", this.name);
        tag.put("Center", NbtUtils.writeBlockPos(this.center));
        tag.put("NWCorner", NbtUtils.writeBlockPos(this.NWCorner.immutable()));
        tag.put("SECorner", NbtUtils.writeBlockPos(this.SECorner.immutable()));
        ListTag buds = new ListTag();
        this.buildBuds.forEach((bud) -> buds.add(bud.writeNBT()));
        tag.put("BuildBuds", buds);
        ListTag bds = new ListTag();
        this.builds.forEach((build) -> bds.add(build.save()));
        tag.put("Builds", bds);
        return tag;
    }

    /**
     * @return An array that contains the MapBlock instance based on the builds in this Town. Used when a Town is loaded from NBT.
     */
    public void createTownMap() {
        this.townMap = new MapBlock[this.SECorner.getZ() - this.NWCorner.getZ() + 1][this.SECorner.getX() - this.NWCorner.getX() + 1];
        for (NpcBuild build : this.builds) {
            int xStart = this.getMapX(build.getOriginPos().getX());
            int zStart = this.getMapZ(build.getOriginPos().getZ());
            for (int x = 0; x < build.getSizeX(); x++) {
                for (int z = 0; z < build.getSizeZ(); z++) {
                    this.setMapBlockInTownMap(xStart + x, zStart + z, build);
                }
            }
        }
    }

    public String getName() {
        return this.name;
    }

    public Component getDisplayName() {
        return Component.literal(getName());
    }

    public Culture getCulture() {
        return culture;
    }

    public UUID getUuid() {
        return uuid;
    }

    public List<NpcBuild> getBuilds() {
        return this.builds;
    }

    /**
     * Function that generate the town started pack and roads.
     * If one of the building could not be placed, the function will return false.
     *
     * @return True if the town creation was successful.
     */
    public boolean buildStarterPack() {
        // Corrupted cultures don't generate towns
        if (culture == Culture.DEFAULT_CULTURE) {
            return false;
        }
        List<BuildingType> starterPack = this.culture.getRandomStarterPack(RANDOM_SOURCE);
        SliceBuildType wideRoad = (SliceBuildType) this.culture.getBuildType(Culture.WIDE_ROAD_TYPE_NAME);

        // First let's put the main vertical wide road, with length of 2 * mini_size + big_width
        int halfBigPath = wideRoad.getWidth() / 2;
        BuildBud firstBud = this.addToBuds(new BuildBud(BuildBud.BudType.DEFAULT, this, this.getCenter().getX() - halfBigPath, this.getCenter().getZ() - halfBigPath - DEFAULT_PATH_LENGTH, TownMapUtils.Corner.NORTH_WEST, new Direction[]{Direction.NORTH}));
        SliceBuild mainRoad = new Road(wideRoad, 2 * DEFAULT_PATH_LENGTH + wideRoad.getWidth(), 1);
        boolean success = this.tryBuild(mainRoad, firstBud);
        if (success) {
            // Working on the west side of the road.
            if (RANDOM_SOURCE.nextBoolean()) {
                // We put a perpendicular road.
                BuildBud bud = this.addToBuds(new BuildBud(BuildBud.BudType.DEFAULT, this, mainRoad.getOriginPos().getX() - 1, mainRoad.getOriginPos().getZ() + RANDOM_SOURCE.nextInt(3) * DEFAULT_PATH_LENGTH, TownMapUtils.Corner.NORTH_EAST, new Direction[]{Direction.EAST}));
                SliceBuild road = new Road(wideRoad, DEFAULT_PATH_LENGTH, 1);
                success = this.tryBuild(road, bud);
            } else {
                // We just add a bud.
                this.addToBuds(new BuildBud(BuildBud.BudType.DEFAULT, this, mainRoad.getOriginPos().getX() - 1, mainRoad.getOriginPos().getZ() + RANDOM_SOURCE.nextInt(3) * DEFAULT_PATH_LENGTH, TownMapUtils.Corner.NORTH_EAST, new Direction[]{Direction.EAST}));
            }
            // And now on the east side.
            if (RANDOM_SOURCE.nextBoolean()) {
                // We put a perpendicular road.
                BuildBud bud = this.addToBuds(new BuildBud(BuildBud.BudType.DEFAULT, this, mainRoad.getOriginPos().getX() + wideRoad.getWidth(), mainRoad.getOriginPos().getZ() + RANDOM_SOURCE.nextInt(3) * DEFAULT_PATH_LENGTH, TownMapUtils.Corner.NORTH_WEST, new Direction[]{Direction.WEST}));
                SliceBuild road = new Road(wideRoad, DEFAULT_PATH_LENGTH, 1);
                success &= this.tryBuild(road, bud);
            } else {
                // We just add a bud.
                this.addToBuds(new BuildBud(BuildBud.BudType.DEFAULT, this, mainRoad.getOriginPos().getX() + wideRoad.getWidth(), mainRoad.getOriginPos().getZ() + RANDOM_SOURCE.nextInt(3) * DEFAULT_PATH_LENGTH, TownMapUtils.Corner.NORTH_WEST, new Direction[]{Direction.WEST}));
            }
        }
        for (BuildingType type : starterPack) {
            this.addBuilding(type);
        }
        return success;
    }

    /**
     * Tries to place the given Build on the Bud. For each adjacent BuildRoad to this bud, we try the corresponding rotation
     * of the Build. If the TownMap is empty, and the MC Level allows the placement, the Build is added to the map.
     *
     * @param build    Build we want to try to build.
     * @param buildBud Bud on which we try to build.
     * @return True if the Build was successfully built, false otherwise.
     */
    public boolean tryBuild(NpcBuild build, @Nullable BuildBud buildBud) {
        if (buildBud == null) {
            return false;
        }
        for (Direction dir : buildBud.getAdjacentRoads()) {
            if (build.canBeBuiltOnBud(this, buildBud, dir)) {
                this.removeFromBuds(buildBud);
                build.addToTown(this, buildBud, dir);
                return true;
            }
        }
        return false;
    }

    /**
     * Function used to add new buildings in the town !
     * Tries to add the build in parameter.
     *
     * @param type Building to be added in the town.
     */
    public @Nullable Building addBuilding(BuildingType type) {
        //TODO What if there is no Bud left ?
        this.getBuds().sort(Comparator.comparingInt(BuildBud::getSquaredDistToCenter));
        BuildBud[] buildBuds = this.getBuds().toArray(new BuildBud[0]);
        Building building = new Building(type, type.getRandomVariant(RANDOM_SOURCE), 1);
        for (BuildBud buildBud : buildBuds) {
            if (this.tryBuild(building, buildBud)) {
                return building;
            } else {
                // If it was not possible to build, we check if the Bud has enough free space to stay.
                // Just in case, we check if the Map is still empty on this bud Pos.
                if (this.isEmpty(buildBud.getRealPos())) {
                    // Now we check if the space is bigger than the minimum.
                    if (buildBud.asEnoughSpace(this)) {
                        continue;
                    }
                }
                this.removeFromBuds(buildBud);
            }
        }
        return null;
    }

    /**
     * Function used to add the new TownCenter in the town !
     * This will change the center of the town and the position of the new buildings.
     *
     * @param building Building to be added in the town.
     */
    public void addTownCenter(Building building) {
        ArrayList<BuildBud> monoPathBuildBuds = new ArrayList<>();
        for (BuildBud buildBud : this.getBuds()) {
            if (buildBud.getType() == BuildBud.BudType.DEFAULT) {
                Direction[] dirs = buildBud.getAdjacentRoads();
                if (dirs.length == 1) {
                    if (this.getBuild(buildBud.getRealPos().relative(dirs[0])) instanceof Road path) {
                        if (path.isWide()) {
                            monoPathBuildBuds.add(buildBud);
                        }
                    }
                } else {
                    boolean onlyBig = true;
                    for (Direction dir : dirs) {
                        if (this.getBuild(buildBud.getRealPos().relative(dir)) instanceof Road path) {
                            if (!path.isWide()) {
                                onlyBig = false;
                            }
                        }
                    }
                    if (onlyBig) {
                        //TODO Test ce bud
                    }
                }
            }
        }
        for (BuildBud buildBud : monoPathBuildBuds) {
            //TODO Add another BigPath
            //TODO Test ce bud
        }
        /*
        Idée : on boucle sur les buds. Si on a un Bud à double path BIG, on teste directement si c'est constructible.
        Si on tombe sur un Bud avec un Big Path simple, on le stock dans une liste.
        Si on tombe sur un Bud avec un Small Path simple ou double, on le dégage.
        Si à la fin de la boucle on a rien trouvé, on boucle sur la liste des Big Simple.
        Pour chacun de ses buds, on crée un Big Path, puis on teste la construction.

        Quand on teste la construction, il faut tester la taille du build + la taille des bigs path en horizontal et vertical
        mais pas x2 car ce sera forcément déjà à un coin double big path.

         */
    }

    /**
     * Put the given build in this TownMap builds dictionary and update the TownMap.
     * Called directly when a new Build is created.
     *
     * @param build Build to be added.
     */
    public void addNewBuilds(NpcBuild build) {
        this.builds.add(build);
        this.updateTownMap(build);
    }

    /**
     * Update the TownMap by resizing it so that the new build fits, and add its ids in the TownMap matrix.
     *
     * @param build Build that must be added or updated on the TownMap.
     */
    public void updateTownMap(NpcBuild build) {
        this.resizeTownMap(build);
        int xStart = this.getMapX(build.getOriginPos().getX());
        int zStart = this.getMapZ(build.getOriginPos().getZ());
        for (int x = 0; x < build.getSizeX(); x++) {
            for (int z = 0; z < build.getSizeZ(); z++) {
                this.setMapBlockInTownMap(xStart + x, zStart + z, build);
            }
        }
    }

    /**
     * Resize the TownMap matrix so that the given Build can fit inside.
     *
     * @param build Build that must be added or updated on the TownMap.
     */
    private void resizeTownMap(NpcBuild build) {
        int north = Math.max(0, this.NWCorner.getZ() - build.getOriginPos().getZ());
        int east = Math.max(0, build.getCornerPos(TownMapUtils.Corner.SOUTH_EAST).getX() - this.SECorner.getX());
        int south = Math.max(0, build.getCornerPos(TownMapUtils.Corner.SOUTH_EAST).getZ() - this.SECorner.getZ());
        int west = Math.max(0, this.NWCorner.getX() - build.getOriginPos().getX());
        if (north + east + south + west > 0) {
            this.resizeTownMap(north, east, south, west);
        }
    }

    /**
     * Resize the VilleMap matrix by adding rows and columns.
     *
     * @param north Number of rows to add to the north of the matrix.
     * @param east  Number of columns to add to the east of the matrix.
     * @param south Number of rows to add to the south of the matrix.
     * @param west  Number of columns to add to the west of the matrix.
     */
    private void resizeTownMap(int north, int east, int south, int west) {
        this.NWCorner.move(-west, 0, -north);
        this.SECorner.move(east, 0, south);
        int rows = this.townMap.length;
        int cols = this.townMap[0].length;
        // Create the new matrix, Java fills it with 0 by default.
        MapBlock[][] newTownMap = new MapBlock[north + rows + south][west + cols + east];
        // Copy the townMap into the newTownMap
        for (int i = 0; i < rows; i++) {
            System.arraycopy(this.townMap[i], 0, newTownMap[i + north], west, cols);
        }
        this.townMap = newTownMap;
    }

    /**
     * Tries to create a BuildRoad on each Bud created when the adjacent BuildRoads to a new building are updated.
     *
     * @param buildBud Bud to be tested.
     */
    public void tryCreateRoad(@NotNull BuildBud buildBud) {
        // If this bud has only one adjacent Path, we try
        if (buildBud.getAdjacentRoads().length == 1 && RANDOM_SOURCE.nextFloat() < Config.ROAD_SPAWN_RATE) {
            boolean mustBeWide = false;
            Direction pathDir = buildBud.getAdjacentRoads()[0];
            if (this.getBuild(buildBud.getRealPos().relative(pathDir)) instanceof Road road) {
                if (road.isWide()) {
                    mustBeWide = RANDOM_SOURCE.nextFloat() < Config.WIDE_ROAD_SPAWN_RATE;
                }
            }
            SliceBuildType road = (SliceBuildType) this.culture.getBuildType(mustBeWide ? WIDE_ROAD_TYPE_NAME : ROAD_TYPE_NAME);
            // We check if there is already a road quite close to this one.
            Direction secondDir = buildBud.getCorner().getLeftDirection() == pathDir ? buildBud.getCorner().getRightDirection() : buildBud.getCorner().getLeftDirection();
            if (this.roadTooCloseInDir(buildBud.getRealPos().mutable().move(secondDir), secondDir) && this.roadTooCloseInDir(buildBud.getRealPos().mutable().move(secondDir, -road.getWidth()), secondDir.getOpposite())) {
                this.tryBuild(new Road(road, DEFAULT_PATH_LENGTH, 1), buildBud);
            }
        }

    }

    /**
     * Remove the given bud from this TownMap Buds list. Called when a Bud is used to place a Build.
     *
     * @param buildBud Bud to be removed.
     */
    public void removeFromBuds(BuildBud buildBud) {
        this.buildBuds.remove(buildBud);
    }

    /**
     * Checks the given direction starting at the given cursor position, and return the empty length (or maxLength).
     *
     * @param cursor    BlockPos mutable where we should start checking (i.e. for maxLength = 3, the code will check the starting
     *                  position and move 2 times the cursor). The cursor is moved during the process, and will be at the last
     *                  empty position when this function stops (or the starting vec3 if it returns 0).
     * @param dir       Direction in which the cursor will move.
     * @param maxLength Maximum length of the loop. It includes the starting position of the cursor.
     * @return The integer corresponding to the number of empty positions. 0 if the cursor position is not empty.
     */
    public int getEmptyLength(BlockPos.MutableBlockPos cursor, Direction dir, int maxLength) {
        if (!this.isEmpty(cursor)) {
            return 0;
        }
        for (int length = 1; length < maxLength; length++) {
            cursor.move(dir);
            if (!this.isEmpty(cursor)) {
                cursor.move(dir, -1);
                return length;
            }
        }
        return maxLength;
    }

    /**
     * Checks the given direction starting at the given cursor position to detect other BuildRoads.
     *
     * @param cursor BlockPos mutable where we should start checking. The cursor is moved during the process.
     * @param dir    Direction in which the cursor will move.
     * @return False if a BuildRoad was detected at less than MINI_PATH_SPACE blocks, true otherwise.
     */
    public boolean roadTooCloseInDir(BlockPos.MutableBlockPos cursor, Direction dir) {
        for (int length = 0; length < MINI_PATH_SPACE; length++) {
            if (this.getBuild(cursor) instanceof Road) {
                return false;
            }
            cursor.move(dir);
        }
        return true;
    }

    /**
     * @param pos Real MC position we want to test.
     * @return True if the corresponding position in the TownMap is empty.
     */
    public boolean isEmpty(BlockPos pos) {
        return this.isEmpty(pos, null);
    }

    /**
     * @param pos             Real MC position we want to test.
     * @param allowedMapBlock ID of a Build that we still accept as empty (often the ID of the Build trying to be placed).
     * @return True if the corresponding position in the TownMap is empty or contains the allowedMapBlock.
     */
    public boolean isEmpty(BlockPos pos, @Nullable MapBlock allowedMapBlock) {
        return this.isEmpty(this.getMapX(pos.getX()), this.getMapZ(pos.getZ()), allowedMapBlock);
    }

    /**
     * @param xMap            The coordinate x of the block we want to test in map coordinate.
     * @param zMap            The coordinate z of the block we want to test in map coordinate.
     * @param allowedMapBlock ID of a Build that we still accept as empty (often the ID of the Build trying to be placed).
     * @return True if the corresponding position in the TownMap is empty or contains the acceptedID.
     */
    private boolean isEmpty(int xMap, int zMap, @Nullable MapBlock allowedMapBlock) {
        MapBlock currentMapBlock = this.getMapBlockInMapPos(xMap, zMap);
        return currentMapBlock == null || currentMapBlock == allowedMapBlock;
    }

    /**
     * Returns the ID of the content of the block in the town map.
     *
     * @param pos The real BlockPos of the block we study.
     * @return The ID of the content or 0 if the block has no building or is out of the TownMap.
     */
    public MapBlock getMapBlockInMapPos(BlockPos pos) {
        return this.getMapBlockInMapPos(this.getMapX(pos.getX()), this.getMapZ(pos.getZ()));
    }

    /**
     * Returns the ID of the content of the block in the town map.
     *
     * @param xMap The coordinate x of the block in map coordinate.
     * @param zMap The coordinate z of the block in map coordinate.
     * @return The ID of the content or 0 if the block has no building or is out of the TownMap.
     */
    public @Nullable MapBlock getMapBlockInMapPos(int xMap, int zMap) {
        // Get the Map ID (null if outside the map)
        return (xMap < 0 || zMap < 0 || zMap >= this.townMap.length || xMap >= this.townMap[0].length) ? null : this.townMap[zMap][xMap];
    }

    /**
     * Modify the TownMap matrix by replacing the ID stored at the given coordinate.
     *
     * @param xMap     The coordinate x of the block in map coordinate.
     * @param zMap     The coordinate z of the block in map coordinate.
     * @param mapBlock The ID of the Build to set in the corresponding coordinates.
     */
    private void setMapBlockInTownMap(int xMap, int zMap, MapBlock mapBlock) {
        this.townMap[zMap][xMap] = mapBlock;
    }

    /**
     * @param xMap The coordinate x of the block in map coordinate.
     * @param zMap The coordinate z of the block in map coordinate.
     * @return The corresponding instance of Build, or null if there is no building.
     */
    public @Nullable NpcBuild getBuild(int xMap, int zMap) {
        MapBlock mapBlock = this.getMapBlockInMapPos(xMap, zMap);
        return mapBlock instanceof NpcBuild build ? build : null;
    }

    /**
     * @param pos The real BlockPos of the block we study.
     * @return The corresponding instance of Build, or null if there is no building.
     */
    public @Nullable NpcBuild getBuild(BlockPos pos) {
        return this.getBuild(this.getMapX(pos.getX()), this.getMapZ(pos.getZ()));
    }

    /**
     * Computes the surface of the world.
     *
     * @param x X coordinate.
     * @param z Z coordinate.
     * @return The Y value based on the BiFunction associated to this town.
     */
    public int getSurfaceY(int x, int z) {
        return this.getSurfaceY.apply(x, z);
    }

    /**
     * @return The BlockPos of the center of the town.
     */
    public BlockPos getCenter() {
        return this.center;
    }

    /**
     * @return The 2D array that describes the map of the town.
     */
    public MapBlock[][] getTownMap() {
        return this.townMap;
    }

    /**
     * Add the given Bud in the TownMap buds list.
     *
     * @param newBud Bud to be added.
     * @return The new bud instance.
     */
    public BuildBud addToBuds(BuildBud newBud) {
        for (BuildBud buildBud : this.getBuds()) {
            if (newBud.equals(buildBud)) {
                return buildBud;
            }
        }
        newBud.setSquaredDistToCenter(this.center);
        this.buildBuds.add(newBud);
        return newBud;
    }

    /**
     * @return The list of Buds currently available in the TownMap.
     */
    public List<BuildBud> getBuds() {
        return this.buildBuds;
    }

    /**
     * Replace the current center of the town with a new one. Used when the town center is built.
     *
     * @param newCenter The new center of the town.
     */
    private void setCenter(BlockPos newCenter) {
        this.center = newCenter;
        //TODO Compute the new distance bud-center.
    }

    public int getMapX(int realX) {
        return realX - this.NWCorner.getX();
    }

    public int getMapZ(int realZ) {
        return realZ - this.NWCorner.getZ();
    }

    public BlockPos getNWCorner() {
        return this.NWCorner.immutable();
    }

    public BlockPos getSECorner() {
        return this.SECorner.immutable();
    }

    public CompoundTag getTownMapDataForGui() {
        CompoundTag mapDataTag = new CompoundTag();
        mapDataTag.putString("TownName", getName());
        mapDataTag.put("NWCorner", NbtUtils.writeBlockPos(getNWCorner()));
        mapDataTag.put("SECorner", NbtUtils.writeBlockPos(getSECorner()));
        ListTag elementsTag = new ListTag();
        for (NpcBuild build : getBuilds()) {
            elementsTag.add(build.getDescriptionForGui());
        }
        for (BuildBud bud : getBuds()) {
            elementsTag.add(bud.getDataForGui());
        }
        mapDataTag.put("Elements", elementsTag);
        return mapDataTag;
    }

    /**
     * Prints a description of the current Town, its size and builds.
     */
    public void printDescription() {
        System.out.println("//---------------------------------------------- " + this.getName() + " [" + this.builds.size() + " Builds ] ---------------------------------------------//");
        System.out.println("Town Center: " + Utils.blockPosToString(this.getCenter()));
        System.out.println("Size: " + this.getTownMap()[0].length + "×" + this.getTownMap().length);
        System.out.println("Builds:");
        for (NpcBuild build : this.builds) {
            System.out.println("    - " + build.getClass().getSimpleName()
                    + " [type: " + build.getBuildType().getId()
                    + ", direction: " + build.getDirection().getName()
                    + ", origin: " + Utils.blockPosToString(build.getOriginPos())
                    + ", size: " + build.getSizeX() + "×" + build.getSizeZ() + "]");
        }
        System.out.println("Buds:");
        for (BuildBud buildBud : this.getBuds()) {
            System.out.println("    - Bud [origin: " + Utils.blockPosToString(buildBud.getRealPos()) + ", distance: " + buildBud.getSquaredDistToCenter() + ", corner: " + buildBud.getCorner() + "]");
        }
        System.out.println("//------------------------------------------------------------------------------------------------------------------//");
    }
}
