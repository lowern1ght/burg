package org.dawnoftime.onceuponatown.town.generation.bud;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import org.dawnoftime.onceuponatown.building.instance.Build;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.dawnoftime.onceuponatown.town.generation.TownMapUtils.Corner;

import java.util.Objects;

import static org.dawnoftime.onceuponatown.Config.BUD_MINIMAL_SPACE;

/**
 * Represents the intersection position of two Roads, or the intersection position of a Road and a Build border.
 * Buds serves for placing new Builds. This logic tries to imitate how towns naturally expands.
 */
public class BuildBud {
    private final BlockPos position;
    private final Corner corner;
    private final Direction[] adjacentRoads;
    private final BudType type;

    /**
     * @param adjacentRoads Directions of the adjacent Roads.
     */
    public BuildBud(BudType type, ProtoTown town, int xPos, int zPos, Corner corner, Direction[] adjacentRoads) {
        this.type = type;
        this.position = new BlockPos(xPos, town.getSurfaceY(xPos, zPos), zPos);
        this.corner = corner;
        this.adjacentRoads = adjacentRoads;
    }

    public BuildBud(CompoundTag tag) {
        this.type = BudType.valueOf(tag.getString("Type"));
        this.position = NbtUtils.readBlockPos(tag.getCompound("Position"));
        this.corner = Corner.valueOf(tag.getString("Corner"));
        ListTag tags = tag.getList("AdjacentRoads", ListTag.TAG_STRING);
        this.adjacentRoads = tags.stream()
                .map(tagElement -> Direction.byName(tagElement.getAsString()))
                .toArray(Direction[]::new);
    }

    public CompoundTag saveNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", this.type.toString());
        tag.put("Position", NbtUtils.writeBlockPos(this.position));
        tag.putString("Corner", this.corner.toString());
        ListTag tags = new ListTag();
        for (Direction dir : this.adjacentRoads) {
            tags.add(StringTag.valueOf(dir.toString()));
        }
        tag.put("AdjacentRoads", tags);
        return tag;
    }

    /**
     * Gets the NORTH_WEST position of a Build placed on this Bud. Computes the correct altitude for the Build.
     * @param dir   Direction of the Build, used to get the size on X and Z axis.
     * @return The NORTH_WEST position of the Build, at the correct Y altitude.
     */
    public BlockPos findOriginPosOfBuild(Build build, Direction dir) {
        BlockPos origin = this.corner.getOriginPosOfBuild(this.position, build, dir);
        return origin.atY(build.getSuitableAltitudeForPlacement(origin, dir));
    }

    /**
     * Checks if this Bud has enough free space around it, to know if we can place a Build on it in the future.
     * @return true if the available maximum rectangle is bigger than the minimum square defined in the configs.
     */
    public boolean hasEnoughSpaceToBuildOnIt(ProtoTown town) {
        if (this.hasEnoughSpaceToBuildOnIt(town, true)) {
            return true;
        }
        return this.hasEnoughSpaceToBuildOnIt(town, false);
    }

    /**
     * Computes the bigger rectangle following empty blocks in clockwise or counter-clockwise rotation.
     *
     * @param clockwise true to rotate in clockwise rotation, false for counter-clockwise.
     * @return true if the rectangle is bigger than the minimum square defined in the configs.
     */
    private boolean hasEnoughSpaceToBuildOnIt(ProtoTown town, boolean clockwise) {
        int[] sizes = new int[4];
        Direction dir = clockwise ? this.getCorner().getLeftDirection() : this.getCorner().getRightDirection();
        dir = dir.getOpposite();
        BlockPos.MutableBlockPos cursor = this.getPosition().mutable();
        int sideIndex = 0;
        // The last value of sizes will be different to 0 only if the loop was able to create a rectangle.
        while (sizes[3] == 0) {
            int max = sideIndex < 2 ? BUD_MINIMAL_SPACE : sizes[sideIndex - 2];
            sizes[sideIndex] = town.getEmptyLength(cursor, dir, max);
            // If the opposite side has a different size, we come back 2 side before, and restart the process with a shorter size.
            if (sideIndex >= 2) {
                if (sizes[sideIndex] != sizes[sideIndex - 2]) {
                    sizes[sideIndex - 2] = sizes[sideIndex];
                    sizes[sideIndex - 1] = 0;
                    sizes[sideIndex] = 0;
                    sideIndex -= 2;
                    dir = dir.getOpposite();
                }
            }

            // For next loop.
            sideIndex++;
            dir = clockwise ? dir.getClockWise() : dir.getCounterClockWise();
        }
        return sizes[0] >= BUD_MINIMAL_SPACE && sizes[1] >= BUD_MINIMAL_SPACE;
    }

    public CompoundTag getGuiDescription() {
        CompoundTag displayData = new CompoundTag();
        displayData.putByte("Category", Build.BUD);
        displayData.put("Position", NbtUtils.writeBlockPos(getPosition()));
        return displayData;
    }

    /**
     * Gets this Bud's squared distance to the center of a Town.
     */
    public int getSqrDistToTownCenter(BlockPos townCenterPos) {
        int difX = this.position.getX() - townCenterPos.getX();
        int difZ = this.position.getZ() - townCenterPos.getZ();
        return difX * difX + difZ * difZ;
    }

    /**
     * @return the Directions of this Bud's adjacent Roads.
     */
    public Direction[] getAdjacentRoads() {
        return this.adjacentRoads;
    }

    /**
     * @return this Bud's Corner.
     */
    public Corner getCorner() {
        return this.corner;
    }

    public BlockPos getPosition() {
        return this.position;
    }

    public BudType getType() {
        return this.type;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof BuildBud bud) {
            return (bud.position.getX() == this.position.getX()
                && bud.position.getZ() == this.position.getZ()
                    && bud.corner == this.corner
                    && bud.type == this.type);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(position.getX(), position.getZ(), corner, type);
    }

    public enum BudType {
        DEFAULT,
        AGRICULTURAL,
        BRIDGE;
    }
}
