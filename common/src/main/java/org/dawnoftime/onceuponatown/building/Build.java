package org.dawnoftime.onceuponatown.building;

import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.dawnoftime.onceuponatown.building.schematic.SchematicContent;
import org.dawnoftime.onceuponatown.building.type.BuildType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.town.generation.MapBlock;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.dawnoftime.onceuponatown.town.generation.TownMapUtils;
import org.dawnoftime.onceuponatown.town.generation.bud.BuildBud;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

public abstract class Build implements MapBlock {
    private final BuildType buildType;

    private BlockPos originPos;
    private Direction direction = Direction.NORTH;
    private Mirror mirror = Mirror.NONE;
    private Rotation rotation = Rotation.NONE;
    private int level = 1;

    public Build(BuildType buildType) {
        this.buildType = buildType;
    }

    public Build(Culture culture, CompoundTag tag){
        this(culture.getBuildType(tag.getString("BuildType")));
        this.originPos = NbtUtils.readBlockPos(tag.getCompound("OriginPos"));
        this.direction = Direction.byName(tag.getString("Direction"));
        this.level = tag.getInt("Level");
    }

    public CompoundTag writeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("BuildCategory", this.getBuildTypeCategory().toString());
        tag.putString("BuildType", this.buildType.getName());
        tag.put("OriginPos", NbtUtils.writeBlockPos(this.originPos));
        tag.putString("Direction", this.direction.getName());
        tag.putInt("Level", this.level);
        return tag;
    }

    public abstract SchematicContent getSchematicContent(ResourceManager resourceManager);

    public int getLevel() {
        return this.level;
    }

    public Build mirror(Mirror mirror) {
        //TODO Is it useful ?
        this.mirror = mirror;
        return this;
    }

    public Mirror getMirror() {
        return this.mirror;
    }

    public Build rotation(Rotation rotation) {
        //TODO Is it correct ?
        this.rotation = rotation;
        return this;
    }

    public Rotation getRotation() {
        return this.rotation;
    }

    public BuildType getBuildType() {
        return this.buildType;
    }

    /**
     * @return The BlockPos of the NORTH_WEST corner of this Build (its origin).
     */
    public BlockPos getOriginPos(){
        return this.originPos;
    }

    /**
     * @return The BlockPos of the given corner of this Build.
     */
    public BlockPos getCornerPos(TownMapUtils.Corner corner){
        return TownMapUtils.Corner.NORTH_WEST.getCornerPos(this.originPos, this, this.direction, corner);
    }

    /**
     * @return The Direction of the Build. Returns NORTH if the Build is not on the TownMap yet.
     */
    public @NotNull Direction getDirection(){
        return this.direction;
    }

    /**
     * @param originPos BlockPos that we want to test in order to find the correct Y coordinate.
     * @param dir Direction of this Build we are testing.
     * @return The Y coordinate adapted to this build and the given BlockPos.
     */
    public int findAdaptedY(BlockPos originPos, Direction dir){
        return originPos.getY();
    }

    /**
     * @param originPos North-West corner of the Build.
     * @param testedPos BlockPos studied within this Build.
     * @return The Y position of this Build in the given testedPos when placed at the given originPos. By default, returns the Y value of
     * the position being checked.
     */
    public int getYOnPosForTestedOrigin(@Nullable BlockPos originPos, BlockPos testedPos) {
        return testedPos.getY();
    }

    /**
     * @param dir Direction in which we want the size of this Build.
     * @return The width of the side of this Build facing the given Direction.
     */
    public int getSize(@Nullable Direction dir){
        if(dir == null){
            dir = Direction.NORTH;
        }
        return dir.getAxis() == Direction.Axis.Z ? this.getSizeX() : this.getSizeZ();
    }

    /**
     * @return The X size of the Build when it is in the default direction North.
     */
    public abstract int getNorthSizeX();

    /**
     * @param dir Direction of the Build. If null, returns the size corresponding to the direction North.
     * @return the size of the side of this Build on the X Axis.
     */
    public int getSizeX(@Nullable Direction dir){
        if(dir == null){
            return this.getNorthSizeX();
        }
        return dir.getAxis() == Direction.Axis.Z ? this.getNorthSizeX() : this.getNorthSizeZ();
    }

    /**
     * @return The Z size of the Build when it is in the default direction North.
     */
    public abstract int getNorthSizeZ();

    /**
     * @return The current size of this Build on the Axis X based on its direction.
     */
    public int getSizeX(){
        return this.getSizeX(this.getDirection());
    }

    /**
     * @param dir Direction of the Build. If null, returns the size corresponding to the direction North.
     * @return the size of the side of this Build on the Z Axis.
     */
    public int getSizeZ(@Nullable Direction dir) {
        if(dir == null){
            return this.getNorthSizeZ();
        }
        return dir.getAxis() == Direction.Axis.Z ? this.getNorthSizeZ() : this.getNorthSizeX();
    }

    /**
     * @return The current size of this Build on the Axis Z based on its direction.
     */
    public int getSizeZ() {
        return this.getSizeZ(this.getDirection());
    }

    /**
     * Function called to add this Build in the Town. This function assume all the condition to place it are met.
     * This function will set the origin BlockPos and the direction of the Build, and call the post placement effects
     * associated to this build.
     * @param town Town in which this Build will be added.
     * @param buildBud Bud used to put set this Building on the TownMap.
     * @param dir Direction corresponding to the orientation of this Build.
     */
    public void addToTownMap(ProtoTown town, BuildBud buildBud, Direction dir){
        this.originPos = buildBud.findOriginPos(this, dir);
        this.direction = dir;
        town.addNewBuilds(this);
        this.onAddedToTown(town);
    }

    /**
     * Replace the NW Corner BlockPos with the given position. Used when the Build must be extended.
     * @param newOrigin BlockPos from which this Build now starts.
     */
    public void setOriginPos(BlockPos newOrigin){
        this.originPos = newOrigin;
    }


    /**
     * Check whenever the given Build can be placed on the given Bud. I.e., we will test if the map is empty or if the
     * terrain is flat enough.
     * @param map Town where we are trying to build the Build.
     * @param buildBud Bud that we are testing with the given direction.
     * @param dir Direction of the MapPath to which the Build will be connected. The Y position of the Build will correspond
     *            to the Y value of this MapPath at this Build's DoorPoint.
     * @return True if the surface is indeed empty, false otherwise.
     */
    public abstract boolean canBeBuiltOnBud(ProtoTown map, BuildBud buildBud, Direction dir);

    public HashMap<ResourceLocation, Integer> getProduction() {
        return this.buildType.getProduction();
    }

    public BuildType getType() {
        return this.buildType;
    }

    @Override
    public @Nullable Build getBuild() {
        return this;
    }

    public abstract StructurePiece generatePieces(StructureTemplateManager manager, Culture culture, @Nullable ProtoTown town);

    /**
     * Function called just after this Build was added to the TownMap.
     * Override it to add post placement steps, like Buds generation.
     * @param map Town in which we add the Build.
     */
    protected void onAddedToTown(ProtoTown map){}

    protected abstract BuildCategory getBuildTypeCategory();

    public static Build readNBT(Culture culture, CompoundTag tag){
        BuildCategory category = BuildCategory.valueOf(tag.getString("BuildCategory"));
        return switch(category){
            case BUILDING -> new Building(culture, tag);
            case ROAD -> new RoadBuild(culture, tag);
        };
    }

    public enum BuildCategory {
        BUILDING,
        ROAD
    }
}
