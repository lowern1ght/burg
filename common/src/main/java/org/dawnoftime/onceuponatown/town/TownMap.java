package org.dawnoftime.onceuponatown.town;

import org.dawnoftime.onceuponatown.town.building.type.BuildingType;
import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.dawnoftime.onceuponatown.town.building.placement.*;
import org.dawnoftime.onceuponatown.town.TownMapUtils.Corner;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.Structure;

import static org.dawnoftime.onceuponatown.town.TownMapUtils.*;

public class TownMap {
    private BlockPos center;
    private final BlockPos.MutableBlockPos NWCorner;
    private final BlockPos.MutableBlockPos SECorner;
    private int[][] mapGrid;
    // The Integer key must match the key in the Town's builds hashmap
    private final HashMap<Integer, BuildPlacement> builds = new HashMap<>();
    private final ArrayList<BuildBud> buildBuds = new ArrayList<>();
    public static final RandomSource randomSource = RandomSource.create();

    /**
     * Create the new instance of TownMap, object use to position the building in Minecraft.
     * @param center The BlockPos of the center of the town, used to prioritize positions to put new buildings.
     */
    public TownMap(BlockPos center){
        this.center = center;
        this.NWCorner = center.mutable();
        this.SECorner = center.mutable();
        this.mapGrid = new int[1][1];
        this.init();
    }

    public TownMap(CompoundTag tag){
        this.center = NbtUtils.readBlockPos(tag.getCompound("Center"));
        this.NWCorner = NbtUtils.readBlockPos(tag.getCompound("NWCorner")).mutable();
        this.SECorner = NbtUtils.readBlockPos(tag.getCompound("SECorner")).mutable();
        //TODO: Read other data
    }

    public void saveToNBT(CompoundTag tag) {
        tag.put("Center", NbtUtils.writeBlockPos(this.center));
        tag.put("NWCorner", NbtUtils.writeBlockPos(this.NWCorner.immutable()));
        tag.put("SECorner", NbtUtils.writeBlockPos(this.SECorner.immutable()));
        //TODO: Save other data
    }

    public static TownMap createTownMapWorldGen(Structure.GenerationContext context, List<BuildingType> starterPack) {
        //TODO: Scan the terrain and create the best town map.
        // Respect the starter pack. All buildings must be spawned.
        // If impossible (ex. due to bad terrain ), return null
        return null;
    }

    /**
     * Generate the basic architecture for a new town : 1+2 big paths forming a cross with 4 buds.
     */
    private void init(){
        // First let's put the vertical big path, with length of 2 * mini_size + big_width
        int halfBigPath = BIG_WIDTH / 2;
        BuildBud firstBuildBud = BuildBud.createBud(this, this.center.offset(-halfBigPath, 0, -halfBigPath - DEFAULT_PATH_LENGTH), Corner.NORTH_WEST, new Direction[]{Direction.NORTH});
        RoadPlacement mainPath = new RoadPlacement(2 * DEFAULT_PATH_LENGTH + BIG_WIDTH, true);
        boolean success = this.tryBuild(mainPath, firstBuildBud);
        success &= this.tryBuild(new RoadPlacement(DEFAULT_PATH_LENGTH, true), BuildBud.createBud(this, mainPath.getOriginPos().offset(-1, 0, DEFAULT_PATH_LENGTH), Corner.NORTH_EAST, new Direction[]{Direction.WEST}));
        success &= this.tryBuild(new RoadPlacement(DEFAULT_PATH_LENGTH, true), BuildBud.createBud(this, mainPath.getOriginPos().offset(BIG_WIDTH, 0, DEFAULT_PATH_LENGTH), Corner.NORTH_WEST, new Direction[]{Direction.EAST}));
        //TODO Add a check : if the real landscape is not flat enough, some path could possibly not be generated.
    }

    /**
     * Tries to place the given MapBuild on the Bud. For each adjacent MapPath to this bud, we try the corresponding rotation
     * of the MapBuild. If the TownMap is empty, and the MC Level allows the placement, the MapBuild is added to the map.
     * @param build MapBuild we want to try to build.
     * @param buildBud Bud on which we try to build.
     * @return True if the MapBuild was successfully built, false otherwise.
     */
    public boolean tryBuild(BuildPlacement build, @Nullable BuildBud buildBud){
        if(buildBud == null){
            return false;
        }
        for(Direction dir : buildBud.getAdjacentPaths()){
            if(build.canBeBuiltOnBud(this, buildBud, dir)){
                this.removeFromBuds(buildBud);
                build.addToMap(this, buildBud, dir);
                return true;
            }
        }
        return false;
    }

    /**
     * Function used to add new buildings in the town !
     * Tries to add the building in parameter.
     * @param building Building to be added in the town.
     */
    public void addBuilding(BuildingPlacement building){
        //TODO What if there is no Bud left ?
        this.getBuds().sort(Comparator.comparingInt(BuildBud::getSquaredDistToCenter));
        BuildBud[] buildBuds = this.getBuds().toArray(new BuildBud[0]);
        for(BuildBud buildBud : buildBuds){
            if(this.tryBuild(building, buildBud)){
                return;
            }else{
                // If it was not possible to build, we check if the Bud has enough free space to stay.
                // Just in case, we check if the Map is still empty on this bud Pos.
                if(this.isEmpty(buildBud.getRealPos())){
                    // Now we check if the space is bigger than the minimum, and we put a MarGarden if not.
                    GardenPlacement clockGarden = buildBud.createAdaptedGarden(this, true);
                    if(clockGarden.getSizeX() < SIDE_SIZE_MAX_GARDEN || clockGarden.getSizeZ() < SIDE_SIZE_MAX_GARDEN){
                        this.removeFromBuds(buildBud);
                        GardenPlacement counterClockGarden = buildBud.createAdaptedGarden(this, false);
                        // We create the smallest MapGarden.
                        if(Math.min(clockGarden.getSizeX(), clockGarden.getSizeZ()) < Math.min(counterClockGarden.getSizeX(), counterClockGarden.getSizeZ())){
                            clockGarden.addToMap(this, buildBud, Direction.NORTH);
                        }else{
                            counterClockGarden.addToMap(this, buildBud, Direction.NORTH);
                        }
                    }
                }else{
                    this.removeFromBuds(buildBud);
                }
            }
        }
    }

    /**
     * Function used to add the new TownCenter in the town !
     * This will change the center of the town and the position of the new buildings.
     * @param building Building to be added in the town.
     */
    public void addTownCenter(BuildingPlacement building){
        ArrayList<BuildBud> monoPathBuildBuds = new ArrayList<>();
        for(BuildBud buildBud : this.getBuds()){
            if(buildBud.getType() == BuildBud.BudType.DEFAULT){
                Direction[] dirs = buildBud.getAdjacentPaths();
                if(dirs.length == 1){
                    if(this.getBuild(buildBud.getRealPos().relative(dirs[0])) instanceof RoadPlacement path) {
                        if(path.isBig()) {
                            monoPathBuildBuds.add(buildBud);
                        }
                    }
                }else{
                    boolean onlyBig = true;
                    for(Direction dir : dirs){
                        if(this.getBuild(buildBud.getRealPos().relative(dir)) instanceof RoadPlacement path){
                            if(!path.isBig()){
                                onlyBig = false;
                            }
                        }
                    }
                    if(onlyBig){
                        //TODO Test ce bud
                    }
                }
            }
        }
        for(BuildBud buildBud : monoPathBuildBuds){
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
     * @return This TownMap map of builds.
     */
    public HashMap<Integer, BuildPlacement> getBuilds() {
        return this.builds;
    }

    /**
     * @return A new ID for a building. This ID is the number of building in the town + 1, because the first ID is 1.
     */
    public int generateNewID() {
        return this.builds.size() + 1;
    }

    /**
     * Put the given build in this TownMap builds dictionary and update the TownMap.
     * Called directly when a new Build is created.
     * @param id ID of the build that we want to add.
     * @param build Build to be added.
     */
    public void addNewBuilds(int id, BuildPlacement build){
        this.builds.put(id, build);
        this.updateTownMap(build);
    }

    /**
     * Update the VilleMap by resizing it so that the new build fits, and add its ids in the TownMap matrix.
     * @param build MapBuild that must be added or updated on the TownMap.
     */
    public void updateTownMap(BuildPlacement build){
        this.resizeTownMap(build);
        int xStart = this.getMapX(build.getOriginPos().getX());
        int zStart = this.getMapZ(build.getOriginPos().getZ());
        for (int x = 0; x < build.getSizeX(); x++) {
            for (int z = 0; z < build.getSizeZ(); z++) {
                this.setIDInMapPos(xStart + x, zStart + z, build.getId());
            }
        }
    }

    /**
     * Resize the VilleMap matrix so that the given MapBuild can fit inside.
     * @param build MapBuild that must be added or updated on the TownMap.
     */
    private void resizeTownMap(BuildPlacement build){
        int north = Math.max(0, this.NWCorner.getZ() - build.getOriginPos().getZ());
        int east = Math.max(0, build.getCornerPos(Corner.SOUTH_EAST).getX() - this.SECorner.getX());
        int south = Math.max(0, build.getCornerPos(Corner.SOUTH_EAST).getZ() - this.SECorner.getZ());
        int west = Math.max(0, this.NWCorner.getX() - build.getOriginPos().getX());
        if(north + east + south + west > 0){
            this.resizeTownMap(north, east, south, west);
        }
    }

    /**
     * Resize the VilleMap matrix by adding rows and columns.
     * @param north Number of rows to add to the north of the matrix.
     * @param east Number of columns to add to the east of the matrix.
     * @param south Number of rows to add to the south of the matrix.
     * @param west Number of columns to add to the west of the matrix.
     */
    private void resizeTownMap(int north, int east, int south, int west){
        this.NWCorner.move(-west, 0, -north);
        this.SECorner.move(east, 0, south);
        int rows = this.mapGrid.length;
        int cols = this.mapGrid[0].length;
        // Create the new matrix, Java fills it with 0 by default.
        int[][] newTownMap = new int[north + rows + south][west + cols + east];
        // Copy the townMap into the newTownMap
        for(int i = 0; i < rows; i++) {
            System.arraycopy(this.mapGrid[i], 0, newTownMap[i + north], west, cols);
        }
        this.mapGrid = newTownMap;
    }

    /**
     * Add the given Bud in the TownMap buds list.
     * Called directly when a new Bud is created.
     * @param buildBud Bud to be removed.
     */
    public void addToBuds(BuildBud buildBud){
        this.buildBuds.add(buildBud);
    }

    /**
     * Tries to create a MapPath on each Bud created when the adjacent MapPaths to a new building are updated.
     * @param buildBud Bud to be tested.
     */
    public void tryCreatePath(BuildBud buildBud){
        if(buildBud != null){
            // If this bud has only one adjacent Path, we try
            //TODO Replace with Vanilla random.
            if(buildBud.getAdjacentPaths().length == 1 && randomSource.nextFloat() < PATH_SPAWN_RATE){
                boolean big = false;
                Direction pathDir = buildBud.getAdjacentPaths()[0];
                if(this.getBuild(buildBud.getRealPos().relative(pathDir)) instanceof RoadPlacement path){
                    if(path.isBig()){
                        big = randomSource.nextFloat() < BIG_PATH_SPAWN_RATE;
                    }
                }
                // We check if there is already a path quite close to this one.
                Direction secondDir = buildBud.getCorner().getLeftDirection() == pathDir ? buildBud.getCorner().getRightDirection() : buildBud.getCorner().getLeftDirection();
                if(this.noPathToCloseInDir(buildBud.getRealPos().mutable().move(secondDir), secondDir) && this.noPathToCloseInDir(buildBud.getRealPos().mutable().move(secondDir, -RoadPlacement.getWidth(big)), secondDir.getOpposite())){
                    this.tryBuild(new RoadPlacement(DEFAULT_PATH_LENGTH, big), buildBud);
                }
            }
        }
    }

    /**
     * Remove the given bud from this TownMap Buds list. Called when a Bud is used to place a MapBuild.
     * @param buildBud Bud to be removed.
     */
    public void removeFromBuds(BuildBud buildBud){
        this.buildBuds.remove(buildBud);
    }

    /**
     * Checks the given direction starting at the given cursor position, and return the empty length (or maxLength).
     * @param cursor BlockPos mutable where we should start checking (i.e. for maxLength = 3, the code will check the starting
     *               position and move 2 times the cursor). The cursor is moved during the process, and will be at the last
     *               empty position when this function stops (or the starting pos if it returns 0).
     * @param dir Direction in which the cursor will move.
     * @param maxLength Maximum length of the loop. It includes the starting position of the cursor.
     * @return The integer corresponding to the number of empty positions. 0 if the cursor position is not empty.
     */
    public int getEmptyLength(BlockPos.MutableBlockPos cursor, Direction dir, int maxLength){
        if(!this.isEmpty(cursor)){
            return 0;
        }
        for(int length = 1; length < maxLength; length++){
            cursor.move(dir);
            if(!this.isEmpty(cursor)){
                cursor.move(dir, -1);
                return length;
            }
        }
        return maxLength;
    }

    /**
     * Checks the given direction starting at the given cursor position to detect other MapPaths.
     * @param cursor BlockPos mutable where we should start checking. The cursor is moved during the process.
     * @param dir Direction in which the cursor will move.
     * @return False if a MapPath was detected at less than MINI_PATH_SPACE blocks, true otherwise.
     */
    public boolean noPathToCloseInDir(BlockPos.MutableBlockPos cursor, Direction dir){
        for(int length = 0; length < MINI_PATH_SPACE; length++){
            if(this.getBuild(cursor) instanceof RoadPlacement){
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
    public boolean isEmpty(BlockPos pos){
        return this.isEmpty(pos, 0);
    }

    /**
     * @param pos Real MC position we want to test.
     * @param acceptedID ID of a MapBuild that we still accept as empty (often the ID of the MapBuild trying to be placed).
     * @return True if the corresponding position in the TownMap is empty or contains the acceptedID.
     */
    public boolean isEmpty(BlockPos pos, int acceptedID){
        return this.isEmpty(this.getMapX(pos.getX()), this.getMapZ(pos.getZ()), acceptedID);
    }

    /**
     * @param xMap The coordinate x of the block we want to test in map coordinate.
     * @param zMap The coordinate z of the block we want to test in map coordinate.
     * @return True if the corresponding position in the TownMap is empty.
     */
    private boolean isEmpty(int xMap, int zMap){
        return this.isEmpty(xMap, zMap, 0);
    }

    /**
     * @param xMap The coordinate x of the block we want to test in map coordinate.
     * @param zMap The coordinate z of the block we want to test in map coordinate.
     * @param acceptedID ID of a MapBuild that we still accept as empty (often the ID of the MapBuild trying to be placed).
     * @return True if the corresponding position in the TownMap is empty or contains the acceptedID.
     */
    private boolean isEmpty(int xMap, int zMap, int acceptedID){
        int id = this.getIDInMapPos(xMap, zMap);
        return id == 0 || id == acceptedID;
    }

    /**
     * Returns the ID of the content of the block in the town map.
     * @param pos The real BlockPos of the block we study.
     * @return The ID of the content or 0 if the block has no building or is out of the TownMap.
     */
    public int getIDInMapPos(BlockPos pos){
        return this.getIDInMapPos(this.getMapX(pos.getX()), this.getMapZ(pos.getZ()));
    }

    /**
     * Returns the ID of the content of the block in the town map.
     * @param xMap The coordinate x of the block in map coordinate.
     * @param zMap The coordinate z of the block in map coordinate.
     * @return The ID of the content or 0 if the block has no building or is out of the TownMap.
     */
    public int getIDInMapPos(int xMap, int zMap){
        // Get the Map ID (0 if outside the map)
        //TODO Maybe add a -1 value to check for obstacle, like underwater or lava.
        return (xMap < 0 || zMap < 0 || zMap >= this.mapGrid.length || xMap >= this.mapGrid[0].length) ? 0 : this.mapGrid[zMap][xMap];
    }

    /**
     * Modify the TownMap matrix by replacing the ID stored at the given coordinate.
     * @param xMap The coordinate x of the block in map coordinate.
     * @param zMap The coordinate z of the block in map coordinate.
     * @param id The ID of the MapBuild to set in the corresponding coordinates.
     */
    private void setIDInMapPos(int xMap, int zMap, int id){
        this.mapGrid[zMap][xMap] = id;
    }

    /**
     * @param id Unique ID of the MapBuild instance in the TownMap.
     * @return The corresponding instance of MapBuild, or null if this ID does not exist.
     */
    public BuildPlacement getBuild(int id){
        return this.builds.get(id);
    }

    /**
     * @param xMap The coordinate x of the block in map coordinate.
     * @param zMap The coordinate z of the block in map coordinate.
     * @return The corresponding instance of MapBuild, or null if there is no building.
     */
    public BuildPlacement getBuild(int xMap, int zMap){
        return this.getBuild(this.getIDInMapPos(xMap, zMap));
    }

    /**
     * @param pos The real BlockPos of the block we study.
     * @return The corresponding instance of MapBuild, or null if there is no building.
     */
    public BuildPlacement getBuild(BlockPos pos){
        return this.getBuild(this.getIDInMapPos(this.getMapX(pos.getX()), this.getMapZ(pos.getZ())));
    }

    /**
     * @return The BlockPos of the center of the town.
     */
    public BlockPos getCenter(){
        return this.center;
    }

    /**
     * @return The 2D array that describes the map of the town.
     */
    public int[][] getMapGrid(){
        return this.mapGrid;
    }

    /**
     * @return The list of Buds currently available in the TownMap.
     */
    public ArrayList<BuildBud> getBuds(){
        return this.buildBuds;
    }

    /**
     * Replace the current center of the town with a new one. Used when the town center is built.
     * @param newCenter The new center of the town.
     */
    private void setCenter(BlockPos newCenter){
        this.center = newCenter;
        //TODO Compute the new distance bud-center.
    }

    public int getMapX(int realX){
        return realX - this.NWCorner.getX();
    }

    public int getMapZ(int realZ){
        return realZ - this.NWCorner.getZ();
    }

    public BlockPos getNWCorner(){
        return this.NWCorner.immutable();
    }

    /**
     * Prints a description of the current Town, its size and builds.
     */
    public void print_description(){
        HashMap<Integer, BuildPlacement> builds = this.getBuilds();
        System.out.println("//---------------------------------------------- Town Map [" + this.getBuilds().size() + "] ---------------------------------------------//");
        System.out.println("Town Center: " + str(this.getCenter()));
        System.out.println("Size: " + this.getMapGrid()[0].length + "×" + this.getMapGrid().length);
        System.out.println("Builds:");
        for(int id : builds.keySet()){
            BuildPlacement build = builds.get(id);
            System.out.println("    - " + id + " : " + build.getClass().getSimpleName() + " [origin: " + str(build.getOriginPos()) + ", xSize: " + build.getSizeX() + ", zSize: " + build.getSizeZ() + "]");
        }
        System.out.println("Buds:");
        for(BuildBud buildBud : this.getBuds()){
            System.out.println("    - Bud [origin: " + str(buildBud.getRealPos()) + ", distance: " + buildBud.getSquaredDistToCenter() + ", corner: " + buildBud.getCorner() + "]");
        }
        System.out.println("//-------------------------------------------------------------------------------------------------------------//");
    }

    public static String str(BlockPos pos){
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    public void print_map(){
        int height = this.mapGrid.length;
        int width = this.mapGrid[0].length;
        int [][] displayMap = new int[height][];
        // First we make a copy of the real townMap.
        for(int i = 0; i < height; i++)
            displayMap[i] = this.mapGrid[i].clone();
        // Then we put the buds inside.
        for(BuildBud buildBud : this.getBuds()){
            BlockPos pos = buildBud.getRealPos();
            displayMap[this.getMapZ(pos.getZ())][this.getMapX(pos.getX())] = -999;
        }
        // Finally we print each row of the map.
        for(int z = 0; z < height; z++){
            StringBuilder row = new StringBuilder();
            int prevY = -999;
            for(int x = 0; x < width; x++){
                int id = displayMap[z][x];
                int currY = 60; //TODO must be replace with world high this.getFloorHeight(x, z);
                if(prevY == -999){
                    prevY = currY;
                }
                if(id == -999){ // This is a bud !
                    row.append(ColorMap.COLOR_BUD.getSquare(false));
                }else{
                    BuildPlacement build = this.getBuild(id);
                    if(build instanceof BuildingPlacement building){
                        currY = building.getOriginPos().getY();
                        row.append(ColorMap.COLOR_BUILDING.getSquare(currY < prevY));
                    } else if (build instanceof RoadPlacement) {
                        row.append(ColorMap.COLOR_PATH.getSquare(currY < prevY));
                    } else if (build instanceof GardenPlacement) {
                        row.append(ColorMap.COLOR_GARDEN.getSquare(currY < prevY));
                    } else if (id == -1) { // This is Water !
                        row.append(ColorMap.COLOR_WATER.getSquare(currY < prevY));
                    }else{ // This is the floor.
                        row.append(ColorMap.COLOR_FLOOR.getSquare(currY < prevY));
                    }
                }
                prevY = currY;
            }
            System.out.println(row);
        }
    }

    public enum ColorMap{
        COLOR_BUD("\033[0;105m", "\033[0;105m"), // PURPLE_BACKGROUND_BRIGHT & PURPLE_BACKGROUND_BRIGHT
        COLOR_FLOOR("\033[0;102m", "\033[42m"), // GREEN_BACKGROUND_BRIGHT & GREEN_BACKGROUND
        COLOR_WATER("\033[0;104m", "\033[44m"), // BLUE_BACKGROUND_BRIGHT & BLUE_BACKGROUND
        COLOR_BUILDING("\033[0;103m", "\033[43m"), // YELLOW_BACKGROUND_BRIGHT & YELLOW_BACKGROUND
        COLOR_PATH("\033[0;101m", "\033[41m"), // RED_BACKGROUND_BRIGHT & RED_BACKGROUND
        COLOR_GARDEN("\033[0;106m", "\033[46m"); // CYAN_BACKGROUND_BRIGHT & CYAN_BACKGROUND

        /*
        Unused :
        BLACK_BACKGROUND = "\033[40m"
        BLACK_BACKGROUND_BRIGHT = "\033[0;100m"
        WHITE_BACKGROUND = "\033[47m"
        WHITE_BACKGROUND_BRIGHT = "\033[0;107m"
        PURPLE_BACKGROUND = "\033[45m"
         */

        private static final String SQUARE = "   ";  // Square that we color to generate the Map.
        private static final String RESET = "\033[0m";  // Text Reset

        private final String lightSquare;
        private final String darkSquare;

        ColorMap(String lightColor, String darkColor){
            this.lightSquare = lightColor + SQUARE + RESET;
            this.darkSquare = darkColor + SQUARE + RESET;
        }

        public String getSquare(boolean isDark){
            return isDark ? this.darkSquare : this.lightSquare;
        }
    }
}
