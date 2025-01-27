package org.dawnoftime.onceuponatown.town.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.dawnoftime.onceuponatown.Config;
import org.dawnoftime.onceuponatown.building.Build;
import org.dawnoftime.onceuponatown.building.Building;
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
import java.util.function.BiFunction;

import static org.dawnoftime.onceuponatown.Config.DEFAULT_ROAD_LENGTH;
import static org.dawnoftime.onceuponatown.Config.MINI_ROAD_SPACE;
import static org.dawnoftime.onceuponatown.culture.Culture.ROAD_TYPE_NAME;
import static org.dawnoftime.onceuponatown.culture.Culture.WIDE_ROAD_TYPE_NAME;

/**
 * Base Town class. Handles a Town's infrastructure (Roads, Buildings...) : provides methods to add new Builds, grow Roads, etc <br>
 * ProtoTown is not linked to a Level. It can be used on its own during world generation, so we can use Town's logic without having a Level instance. <br>
 * Can be saved to NBT tag, so we can create Towns subclasses later. Towns are linked to a Level and add the additional logic : citizens, players, progression, etc
 */
public class ProtoTown {
    public static final int B_BOX_HEIGHT = 30; // Height of Town's boundaries
    public static final int B_BOX_INFLATED_BY = 10; // Free space around the Town considered as part of it
    public static final RandomSource RANDOM = RandomSource.create();
    protected final Culture culture; // The Town's culture defines the npcs, jobs, buildings, trades, quests...
    private final BlockPos.MutableBlockPos center; // Center position
    private final BlockPos.MutableBlockPos NWCorner; // North-West corner
    private final BlockPos.MutableBlockPos SECorner; // South-East corner
    protected final List<BuildBud> buds; // Buds are positions where new Builds may spawn
    protected final List<Build> builds; // Roads, Buildings...
    protected int buildsWeight; // Sum of the Buildings weights. Used to control the maximum amount of Buildings in a Town
    // Unsaved attributes
    private final BiFunction<Integer, Integer, Integer> getSurfaceY; // Function to get terrain altitude at a given X,Z position
    private MapPart[][] townMap; // 2D array representing the map of the Town. Each map position could be a free space or occupied by a Build
    protected BoundingBox townBox;

    protected ProtoTown(Culture culture,
                        BlockPos center,
                        BlockPos NWCorner,
                        BlockPos SECorner,
                        List<BuildBud> buds,
                        List<Build> builds,
                        int buildsWeight,
                        BiFunction<Integer, Integer, Integer> getSurfaceY) {
        this.culture = culture;
        this.center = center.mutable();
        this.NWCorner = NWCorner.mutable();
        this.SECorner = SECorner.mutable();
        this.buds = buds;
        this.builds = builds;
        this.buildsWeight = buildsWeight;
        this.getSurfaceY = getSurfaceY;
        computeTownMap();
        calculateBoundingBox();
    }

    /**
     * Tries to create a ProtoTown at the given position, by checking if the surrounding terrain is flat enough.
     * If successful, creates the initial Buildings and BuildBuds of the town. No physical buildings are spawned in the world.
     *
     * @param culture     the town's culture
     * @param center      the center position of the town
     * @param getSurfaceY function to get the altitude of the terrain surface
     */
    public static @Nullable ProtoTown create(Culture culture, BlockPos center, BiFunction<Integer, Integer, Integer> getSurfaceY) {
        ProtoTown protoTo = new ProtoTown(culture, center.mutable(), center.mutable(), center.mutable(), new ArrayList<>(), new ArrayList<>(), 0, getSurfaceY);
        return protoTo.buildStarterPack() ? protoTo : null;
    }

    public CompoundTag saveNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Culture", culture.getId());
        tag.put("Center", NbtUtils.writeBlockPos(center));
        tag.put("NWCorner", NbtUtils.writeBlockPos(NWCorner.immutable()));
        tag.put("SECorner", NbtUtils.writeBlockPos(SECorner.immutable()));
        ListTag budsTag = new ListTag();
        buds.forEach((bud) -> budsTag.add(bud.saveNbt()));
        tag.put("BuildBuds", budsTag);
        ListTag buildsTag = new ListTag();
        builds.forEach((build) -> buildsTag.add(build.saveNbt()));
        tag.put("Builds", buildsTag);
        tag.putInt("BuildsWeight", buildsWeight);
        return tag;
    }

    /**
     * Tries to set up a starter pack of Builds by scanning the surrounding terrain and finding
     * places for Roads and Buildings. Fails if a Build could not be placed.
     *
     * @return True if the starter pack was successfully created, false otherwise
     */
    protected boolean buildStarterPack() {
        // Corrupted cultures don't generate towns
        if (culture == Culture.CORRUPTED_CULTURE) {
            return false;
        }
        List<BuildingType> starterPack = culture.getRandomStarterPack(RANDOM);
        SliceBuildType wideRoad = (SliceBuildType) culture.getBuildType(Culture.WIDE_ROAD_TYPE_NAME);

        // First let's put the main vertical wide road, with length of 2 * mini_size + big_width.
        // Since a road can only grow in one direction, we split it in 2 parts.
        int halfBigPath = wideRoad.getWidth() / 2;
        BuildBud firstBud = this.addBud(new BuildBud(BuildBud.BudType.DEFAULT, this, this.getCenter().getX() - halfBigPath, this.getCenter().getZ(), TownMapUtils.Corner.NORTH_WEST, new Direction[]{Direction.NORTH}));
        SliceBuild bottomRoad = new Road(wideRoad, DEFAULT_ROAD_LENGTH + wideRoad.getWidth() / 2, 1);
        boolean success = this.tryBuildOnBud(bottomRoad, firstBud);
        firstBud = this.addBud(new BuildBud(BuildBud.BudType.DEFAULT, this, this.getCenter().getX() - halfBigPath, this.getCenter().getZ() - 1, TownMapUtils.Corner.SOUTH_WEST, new Direction[]{Direction.SOUTH}));
        SliceBuild topRoad = new Road(wideRoad, DEFAULT_ROAD_LENGTH + wideRoad.getWidth() / 2, 1);
        success &= this.tryBuildOnBud(topRoad, firstBud);

        if (success) {
            // Working on the west side of the road.
            if (RANDOM.nextBoolean()) {
                // We put a perpendicular road.
                BuildBud bud = this.addBud(new BuildBud(BuildBud.BudType.DEFAULT, this, topRoad.getOriginPos().getX() - 1, topRoad.getOriginPos().getZ() + RANDOM.nextInt(3) * DEFAULT_ROAD_LENGTH, TownMapUtils.Corner.NORTH_EAST, new Direction[]{Direction.EAST}));
                SliceBuild road = new Road(wideRoad, DEFAULT_ROAD_LENGTH, 1);
                success = this.tryBuildOnBud(road, bud);
            } else {
                // We just add a bud.
                this.addBud(new BuildBud(BuildBud.BudType.DEFAULT, this, topRoad.getOriginPos().getX() - 1, topRoad.getOriginPos().getZ() + RANDOM.nextInt(3) * DEFAULT_ROAD_LENGTH, TownMapUtils.Corner.NORTH_EAST, new Direction[]{Direction.EAST}));
            }
            // And now on the east side.
            if (RANDOM.nextBoolean()) {
                // We put a perpendicular road.
                BuildBud bud = this.addBud(new BuildBud(BuildBud.BudType.DEFAULT, this, topRoad.getOriginPos().getX() + wideRoad.getWidth(), topRoad.getOriginPos().getZ() + RANDOM.nextInt(3) * DEFAULT_ROAD_LENGTH, TownMapUtils.Corner.NORTH_WEST, new Direction[]{Direction.WEST}));
                SliceBuild road = new Road(wideRoad, DEFAULT_ROAD_LENGTH, 1);
                success &= this.tryBuildOnBud(road, bud);
            } else {
                // We just add a bud.
                this.addBud(new BuildBud(BuildBud.BudType.DEFAULT, this, topRoad.getOriginPos().getX() + wideRoad.getWidth(), topRoad.getOriginPos().getZ() + RANDOM.nextInt(3) * DEFAULT_ROAD_LENGTH, TownMapUtils.Corner.NORTH_WEST, new Direction[]{Direction.WEST}));
            }
        }
        for (BuildingType type : starterPack) {
            success &= (this.tryAddBuilding(type, 1) != null);
        }
        return success;
    }

    /**
     * Recreates the 2D array representing this ProtoTown's map, by iterating over each Build.
     */
    protected void computeTownMap() {
        townMap = new MapPart[SECorner.getZ() - NWCorner.getZ() + 1][SECorner.getX() - NWCorner.getX() + 1];
        for (Build build : builds) {
            int xStart = this.getMapX(build.getOriginPos().getX());
            int zStart = this.getMapZ(build.getOriginPos().getZ());
            for (int x = 0; x < build.getSizeX(); x++) {
                for (int z = 0; z < build.getSizeZ(); z++) {
                    this.setMapPartAtPos(xStart + x, zStart + z, build);
                }
            }
        }
    }

    private void calculateBoundingBox() {
        townBox = BoundingBox.fromCorners(NWCorner.below(B_BOX_HEIGHT), SECorner.above(B_BOX_HEIGHT)).inflatedBy(B_BOX_INFLATED_BY);
    }

    /**
     * Tries to place the given Build on the given BuildBud. For each adjacent Road to the bud, we try the corresponding rotation
     * of the Build. If the town map is empty, and the MC Level allows the placement, the Build is added to the map.
     *
     * @param build    Build we want to build.
     * @param bud Bud on which we try to build.
     * @return true if the Build was successfully added, false otherwise.
     */
    private boolean tryBuildOnBud(Build build, @Nullable BuildBud bud) {
        if (bud == null) {
            return false;
        }
        for (Direction dir : bud.getAdjacentRoads()) {
            if (build.canBeBuiltOnBud(this, bud, dir)) {
                this.dropBud(bud);
                build.setOriginAndDirection(bud.findOriginPosOfBuild(build, dir), dir);
                this.addBuild(build);
                build.onAddedToTown(this);
                return true;
            }
        }
        return false;
    }

    /**
     * Tries to add a new Building to this ProtoTown. Does not place any block in the world.
     * @param type the BuildingType of the Building to add.
     * @param startingLevel the initial level of the Building to add.
     * @return the freshly added Building or false if the placement was unsuccessful.
     */
    public @Nullable Building tryAddBuilding(BuildingType type, int startingLevel) {
        // TODO What if there is no Bud left ?
        this.getBuds().sort(Comparator.comparingInt(bud -> bud.getSqrDistToTownCenter(center)));
        BuildBud[] buildBuds = this.getBuds().toArray(new BuildBud[0]);
        Building building = new Building(type, type.getRandomVariant(RANDOM), startingLevel);
        for (BuildBud buildBud : buildBuds) {
            if (this.tryBuildOnBud(building, buildBud)) {
                return building;
            } else {
                // If it was not possible to build, we check if the Bud has enough free space to stay.
                // Just in case, we check if the Map is still empty on this bud Pos.
                if (this.isFreeAt(buildBud.getPosition())) {
                    // Now we check if the space is bigger than the minimum.
                    if (buildBud.hasEnoughSpaceToBuildOnIt(this)) {
                        continue;
                    }
                }
                this.dropBud(buildBud);
            }
        }
        return null;
    }

    /**
     * Tries to create a Road that starts from the given BuildBud. Called after modifications in the town map.
     */
    public void tryCreateRoad(@NotNull BuildBud bud) {
        // If this bud has only one adjacent Road, we try to create a new road.
        if (bud.getAdjacentRoads().length == 1) {
            // If the number of bud is too low or if the randomly rolled value is correct, we create a new road.
            if (buds.size() <= Config.CRITICAL_BUDS_NUMBER || RANDOM.nextFloat() < Config.ROAD_SPAWN_RATE) {
                boolean mustBeWide = false;
                Direction pathDir = bud.getAdjacentRoads()[0];
                if (this.getBuild(bud.getPosition().relative(pathDir)) instanceof Road road) {
                    if (road.isWide()) {
                        mustBeWide = RANDOM.nextFloat() < Config.WIDE_ROAD_SPAWN_RATE;
                    }
                }
                SliceBuildType road = (SliceBuildType) culture.getBuildType(mustBeWide ? WIDE_ROAD_TYPE_NAME : ROAD_TYPE_NAME);
                // We check if there is already a road quite close to this one.
                Direction secondDir = bud.getCorner().getLeftDirection() == pathDir ? bud.getCorner().getRightDirection() : bud.getCorner().getLeftDirection();
                if (this.roadTooCloseInDir(bud.getPosition().mutable().move(secondDir), secondDir) && this.roadTooCloseInDir(bud.getPosition().mutable().move(secondDir, -road.getWidth()), secondDir.getOpposite())) {
                    this.tryBuildOnBud(new Road(road, DEFAULT_ROAD_LENGTH, 1), bud);
                }
            }
        }
    }

    /**
     * Directly insert a Build in builds list, without verifications. Updates the town's map.
     */
    private void addBuild(Build newBuild) {
        builds.add(newBuild);
        this.updateTownMap(newBuild);
        buildsWeight += newBuild.getBuildType().getWeight();
    }

    /**
     * Removes a Build from this town. Updates the town's map.
     */
    protected boolean removeBuild(Build toRemove) {
        boolean removed = builds.remove(toRemove);
        if (removed) {
            // ? computeTownMap();
        }
        return removed;
    }

    /**
     * Adds a BuildingBud to this town.
     *
     * @param newBud the bud to add.
     * @return the newly added bud instance, or the bud to add if it is already part of this town.
     */
    public BuildBud addBud(BuildBud newBud) {
        for (BuildBud buildBud : this.getBuds()) {
            if (newBud.equals(buildBud)) {
                return buildBud;
            }
        }
        buds.add(newBud);
        return newBud;
    }

    /**
     * Remove the given bud from this TownMap Buds list. Called when a Bud is used to place a Build.
     *
     * @param buildBud Bud to be removed.
     */
    private void dropBud(BuildBud buildBud) {
        buds.remove(buildBud);
    }

    /**
     * Adds a TownCenter Building in this ProtoTown.
     * Changes the center of this ProtoTown and the position of the new buildings.
     *
     * @param townCenter TownCenter Building to add.
     */
    public void addTownCenter(Building townCenter) {
        ArrayList<BuildBud> monoPathBuildBuds = new ArrayList<>();
        for (BuildBud buildBud : this.getBuds()) {
            if (buildBud.getType() == BuildBud.BudType.DEFAULT) {
                Direction[] dirs = buildBud.getAdjacentRoads();
                if (dirs.length == 1) {
                    if (this.getBuild(buildBud.getPosition().relative(dirs[0])) instanceof Road path) {
                        if (path.isWide()) {
                            monoPathBuildBuds.add(buildBud);
                        }
                    }
                } else {
                    boolean onlyBig = true;
                    for (Direction dir : dirs) {
                        if (this.getBuild(buildBud.getPosition().relative(dir)) instanceof Road path) {
                            if (!path.isWide()) {
                                onlyBig = false;
                            }
                        }
                    }
                    if (onlyBig) {
                        //TODO Test this bud
                    }
                }
            }
        }
        for (BuildBud buildBud : monoPathBuildBuds) {
            //TODO Add another BigPath
            //TODO Test this bud
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
     * Updates this ProtoTown's map after a new Build was added to the town.
     */
    public void updateTownMap(Build newBuild) {
        this.resizeTownMap(newBuild);
        int xStart = this.getMapX(newBuild.getOriginPos().getX());
        int zStart = this.getMapZ(newBuild.getOriginPos().getZ());
        for (int x = 0; x < newBuild.getSizeX(); x++) {
            for (int z = 0; z < newBuild.getSizeZ(); z++) {
                this.setMapPartAtPos(xStart + x, zStart + z, newBuild);
            }
        }
    }

    private void resizeTownMap(Build newBuild) {
        int north = Math.max(0, this.NWCorner.getZ() - newBuild.getOriginPos().getZ());
        int east = Math.max(0, newBuild.getCornerPos(TownMapUtils.Corner.SOUTH_EAST).getX() - this.SECorner.getX());
        int south = Math.max(0, newBuild.getCornerPos(TownMapUtils.Corner.SOUTH_EAST).getZ() - this.SECorner.getZ());
        int west = Math.max(0, this.NWCorner.getX() - newBuild.getOriginPos().getX());
        if (north + east + south + west > 0) {
            this.resizeTownMap(north, east, south, west);
        }
    }

    /**
     * Resizes this ProtoTown's map by adding rows and columns.
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
        MapPart[][] newTownMap = new MapPart[north + rows + south][west + cols + east];
        // Copy the townMap into the newTownMap
        for (int i = 0; i < rows; i++) {
            System.arraycopy(this.townMap[i], 0, newTownMap[i + north], west, cols);
        }
        this.townMap = newTownMap;
        calculateBoundingBox();
    }

    /**
     * Checks the given direction starting at the given cursor position, and return the empty length (or maxLength).
     *
     * @param cursor    mutable BlockPos where we should start checking (i.e. for maxLength = 3, the code will check the starting
     *                  position and move 2 times the cursor). The cursor is moved during the process, and will be at the last
     *                  empty position when this method stops (or the starting vec3 if it returns 0).
     * @param dir       Direction in which the cursor will move.
     * @param maxLength maximum length of the loop. It includes the starting position of the cursor.
     * @return the integer corresponding to the number of empty positions. 0 if the cursor position is not empty.
     */
    public int getEmptyLength(BlockPos.MutableBlockPos cursor, Direction dir, int maxLength) {
        if (!this.isFreeAt(cursor)) {
            return 0;
        }
        for (int length = 1; length < maxLength; length++) {
            cursor.move(dir);
            if (!this.isFreeAt(cursor)) {
                cursor.move(dir, -1);
                return length;
            }
        }
        return maxLength;
    }

    /**
     * Checks the given direction starting at the given cursor position to detect other Roads.
     *
     * @param cursor mutable BlockPos where we should start checking. The cursor is moved during the process.
     * @param dir    Direction in which the cursor will move.
     * @return false if a Road was detected at less than MINI_PATH_SPACE blocks, true otherwise.
     */
    private boolean roadTooCloseInDir(BlockPos.MutableBlockPos cursor, Direction dir) {
        for (int length = 0; length < MINI_ROAD_SPACE; length++) {
            if (this.getBuild(cursor) instanceof Road) {
                return false;
            }
            cursor.move(dir);
        }
        return true;
    }

    /**
     * @return True if the town map contains a free space at the given position.
     */
    public boolean isFreeAt(BlockPos pos) {
        return this.isFreeAt(pos, null);
    }

    /**
     * @param allowedContent MapPart that should be considered as a free space (often a Build that we try to place).
     * @return true if the town map contains a free space at the given position, or contains the allowed MapPart.
     */
    public boolean isFreeAt(BlockPos pos, @Nullable MapPart allowedContent) {
        return this.isFreeAt(this.getMapX(pos.getX()), this.getMapZ(pos.getZ()), allowedContent);
    }

    /**
     * @param xMap            x coordinate in the town map.
     * @param zMap            z coordinate in the town map.
     * @param allowedMapPart MapPart that should be considered as a free space (often a Build that we try to place).
     * @return true if the town map contains a free space at the given position, or contains the allowed MapPart.
     */
    private boolean isFreeAt(int xMap, int zMap, @Nullable MapPart allowedMapPart) {
        MapPart currentMapPart = this.getMapPartAtPos(xMap, zMap);
        return currentMapPart == null || currentMapPart == allowedMapPart;
    }

    /**
     * @return the MapPart in the town map at the given position, null if nothing was founded.
     */
    public MapPart getMapPartAtPos(BlockPos pos) {
        return this.getMapPartAtPos(this.getMapX(pos.getX()), this.getMapZ(pos.getZ()));
    }

    /**
     * @param xMap x coordinate in the town map.
     * @param zMap y coordinate in the town map.
     * @return the MapPart in the town map at the given position, null if nothing was founded.
     */
    private @Nullable MapPart getMapPartAtPos(int xMap, int zMap) {
        // null if outside the map
        return (xMap < 0 || zMap < 0 || zMap >= townMap.length || xMap >= townMap[0].length) ? null : townMap[zMap][xMap];
    }

    /**
     * Set a MapPart at given town map coordinates.
     *
     * @param xMap     x coordinate in the town map.
     * @param zMap     z coordinate in the town map.
     * @param mapPart The MapPart to set at the given coordinates.
     */
    private void setMapPartAtPos(int xMap, int zMap, MapPart mapPart) {
        townMap[zMap][xMap] = mapPart;
    }

    /**
     * @param xMap x coordinate in the town map.
     * @param zMap z coordinate in the town map.
     * @return the instance of Build at the given coordinates, null if nothing was founded.
     */
    public @Nullable Build getBuild(int xMap, int zMap) {
        MapPart mapPart = this.getMapPartAtPos(xMap, zMap);
        return mapPart instanceof Build build ? build : null;
    }

    /**
     * @return the instance of Build at the given coordinates, null if nothing was founded.
     */
    public @Nullable Build getBuild(BlockPos pos) {
        return this.getBuild(this.getMapX(pos.getX()), this.getMapZ(pos.getZ()));
    }

    /**
     * Gets the terrain surface altitude at the given (x,y) position.
     */
    public int getSurfaceY(int x, int z) {
        return getSurfaceY.apply(x, z);
    }

    protected int calculateBuildsWeight() {
        int sum = 0;
        for (Build build : getBuilds()) {
            sum += build.getBuildType().getWeight();
        }
        return sum;
    }

    public int getMapX(int realX) {
        return realX - NWCorner.getX();
    }

    public int getMapZ(int realZ) {
        return realZ - NWCorner.getZ();
    }

    public List<Building> getBuildings() {
        return getBuilds().stream().filter(Building.class::isInstance).map(Building.class::cast).toList();
    }

    public List<Road> getRoads() {
        return getBuilds().stream().filter(Road.class::isInstance).map(Road.class::cast).toList();
    }

    public Culture getCulture() {
        return culture;
    }

    public BlockPos getCenter() {
        return center.immutable();
    }

    /**
     * Replaces the current center position of the town with a new one.
     */
    private void setCenter(BlockPos newCenter) {
        //TODO Compute the new distance bud-center.
    }

    public BlockPos getNWCorner() {
        return NWCorner.immutable();
    }

    public BlockPos getSECorner() {
        return SECorner.immutable();
    }

    public List<BuildBud> getBuds() {
        return buds;
    }

    public List<Build> getBuilds() {
        return this.builds;
    }

    /**
     * @return a 2D array that describes the map of this town.
     */
    public MapPart[][] getTownMap() {
        return townMap.clone();
    }
}
