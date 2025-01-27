package org.dawnoftime.onceuponatown.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.dawnoftime.onceuponatown.building.schematic.SchematicContent;
import org.dawnoftime.onceuponatown.building.type.BuildType;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.town.generation.MapPart;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.dawnoftime.onceuponatown.town.generation.TownMapUtils;
import org.dawnoftime.onceuponatown.town.generation.bud.BuildBud;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An NPC construction (building, road, bridge, wall...) in the Minecraft world. <br>
 * Can be part of a Town or simply a lone structure. <br>
 * Its characteristics (structure schematic, if NPCs can live inside, if it can be upgraded...) are defined by its BuildType. <br>
 * Build and BuildType share the same relation as Block and BlockState do. Build is the instance in a Level, where BuildType is the description of its behavior. <br>
 * Can be upgraded by a Builder NPC, to be fancier or more interesting for the Player. <br>
 */
public abstract class Build implements MapPart {
    public static final byte BUD = 0; // Fast enumeration for storing in NBT
    public static final byte ROAD = 1;
    public static final byte BUILDING = 2;
    public static final byte BRIDGE = 3;
    private final BuildType buildType; // BuildType of this Build (church, small house, fancy road...) Defines its unique characteristics
    private BlockPos originPos = BlockPos.ZERO; // North-west corner of the build's plot
    private Direction direction = Direction.NORTH; // Direction this Build is facing. For Buildings, it will be the Direction the main entrance is facing
    private Rotation rotation = Rotation.NONE;
    private Mirror mirror = Mirror.NONE;
    private int level; // Minimum is 1. Each upgrade will increase the level by 1. Upgrades are defined by the BuildType

    /**
     * Constructor for newly created Builds.
     *
     * @param buildType BuildType of this Build.
     * @param level     Starting level for this Build. Has to be between 1 and the maximum level defined by the BuildType.
     */
    protected Build(BuildType buildType, int level) {
        this.buildType = buildType;
        this.level = level;
    }

    /**
     * Constructor for loading a Build from NBT.
     *
     * @param culture The Culture this Build belongs to.
     * @param tag     NBT containing the Build's data.
     */
    protected Build(Culture culture, CompoundTag tag) {
        this(culture.getBuildType(tag.getString("BuildType")), tag.getInt("Level"));
        originPos = NbtUtils.readBlockPos(tag.getCompound("OriginPos"));
        direction = Direction.byName(tag.getString("Direction"));
    }

    /**
     * Loads a Build from NBT.
     */
    public static Build load(Culture culture, CompoundTag tag) {
        return switch (tag.getByte("BuildCategory")) {
            case BUILDING -> new Building(culture, tag);
            case ROAD -> new Road(culture, tag);
            default -> null; // TODO improve this
        };
    }

    /**
     * Saves this Build to NBT.
     */
    public CompoundTag saveNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("BuildCategory", getBuildCategory());
        tag.putString("BuildType", buildType.getId());
        tag.put("OriginPos", NbtUtils.writeBlockPos(originPos));
        tag.putString("Direction", direction.getName());
        tag.putInt("Level", level);
        return tag;
    }

    public void setOriginAndDirection(BlockPos originPos, Direction direction) {
        this.originPos = originPos;
        this.direction = direction;
    }

    /**
     * Upgrades this Build to its next level if possible.
     */
    public boolean upgrade() {
        if (this.canUpgrade()) {
            ++level;
            return true;
        } else {
            return false;
        }
    }

    public boolean canUpgrade() {
        return level < getBuildType().getLevels().size();
    }

    /**
     * Called after this Build was added to a Town. <br>
     * Overrides may add post placement steps, like Buds generation.
     *
     * @param town Town to add this Build to.
     */
    public abstract void onAddedToTown(ProtoTown town);

    /**
     * Finds a suitable y position in era to place this Build on a BuildingBud. <br>
     * For example, Buildings will try to place themselves so that their main entrance is connected to a street.
     *
     * @param originPos BlockPos to place this Build at. Only x and z are important here, since this method will find the correct y.
     * @param dir       Direction being evaluated.
     */
    public int getSuitableAltitudeForPlacement(BlockPos originPos, Direction dir) {
        return originPos.getY();
    }

    /**
     * @param originPos North-West corner of this Build.
     * @param testedPos BlockPos studied within this Build.
     * @return The Y position of this Build in the given testedPos when placed at the given originPos. By default, returns the Y value of
     * the position being checked.
     */
    public int getYOnPosForTestedOrigin(@Nullable BlockPos originPos, BlockPos testedPos) {
        // TODO Improve this method name and javadoc please
        return testedPos.getY();
    }

    /**
     * @return BlockPos of the given corner of this Build.
     */
    public BlockPos getCornerPos(TownMapUtils.Corner corner) {
        return TownMapUtils.Corner.NORTH_WEST.getCornerPosOfBuild(originPos, this, direction, corner);
    }

    /**
     * @param dir Direction in which we want the size of this Build.
     * @return The width of the side of this Build facing the given Direction.
     */
    public int getSize(@Nullable Direction dir) {
        if (dir == null) {
            dir = Direction.NORTH;
        }
        return dir.getAxis() == Direction.Axis.Z ? this.getSizeX() : this.getSizeZ();
    }

    /**
     * @param dir Direction of the Build. If null, returns the size corresponding to the direction North.
     * @return The size of the side of this Build on the X Axis.
     */
    public int getSizeX(@Nullable Direction dir) {
        if (dir == null) {
            return this.getNorthSizeX();
        }
        return dir.getAxis() == Direction.Axis.Z ? this.getNorthSizeX() : this.getNorthSizeZ();
    }

    /**
     * @return The current size of this Build on the Axis X based on its direction.
     */
    public int getSizeX() {
        return this.getSizeX(this.getDirection());
    }

    /**
     * @param dir Direction of the Build. If null, returns the size corresponding to the direction North.
     * @return The size of the side of this Build on the Z Axis.
     */
    public int getSizeZ(@Nullable Direction dir) {
        if (dir == null) {
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
     * @return The X size of the Build when it is in the default direction North.
     */
    public abstract int getNorthSizeX();

    /**
     * @return The Z size of the Build when it is in the default direction North.
     */
    public abstract int getNorthSizeZ();


    public abstract int getSizeY();

    /**
     * Determines if this Build can be placed on a Bud. <br>
     * For example, it will test if the terrain is flat enough.
     *
     * @param town     Town to place this Build in.
     * @param buildBud Candidate BuildBud to place this Build at.
     * @param dir      Direction of the Road to which this Build will be connected.
     */
    public abstract boolean canBeBuiltOnBud(ProtoTown town, BuildBud buildBud, Direction dir);

    /**
     * Creates a StructurePiece for placing this Build during Chunk generation.
     */
    public abstract StructurePiece createStructurePiece(Culture culture, @Nullable ProtoTown town);

    /**
     * @return NBT tag containing various elements to be displayed in GUI (BuildType, level, inhabitants...)
     */
    public CompoundTag getGuiDescription() {
        CompoundTag descriptionTag = new CompoundTag();
        descriptionTag.putString("BuildType", getBuildType().getId());
        descriptionTag.put("OriginPos", NbtUtils.writeBlockPos(getOriginPos()));
        descriptionTag.putString("Direction", this.getDirection().getName());
        descriptionTag.putInt("SizeX", getSizeX());
        descriptionTag.putInt("SizeZ", getSizeZ());
        descriptionTag.putInt("Level", getLevel());
        return descriptionTag;
    }

    public abstract SchematicContent getSchematicContent(ResourceManager resourceManager);

    protected abstract byte getBuildCategory();

    @Override
    public @Nullable Build getBuild() {
        return this;
    }

    public BuildType getBuildType() {
        return buildType;
    }

    public BlockPos getOriginPos() {
        return originPos;
    }

    /**
     * Used when this Build must be extended.
     */
    protected void setOriginPos(BlockPos newOrigin) {
        originPos = newOrigin;
    }

    /**
     * @return The Direction of this Build, North if this Build is not part of a Town.
     */
    public @NotNull Direction getDirection() {
        return direction;
    }

    public Rotation getRotation() {
        return rotation;
    }

    public Mirror getMirror() {
        return mirror;
    }

    public int getLevel() {
        return level;
    }
}
