package org.dawnoftime.onceuponatown.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.building.schematic.SchematicContent;
import org.dawnoftime.onceuponatown.building.type.BuildType;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.entity.Profession;
import org.dawnoftime.onceuponatown.structure.pieces.BuildingPiece;
import org.dawnoftime.onceuponatown.town.generation.MapPart;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.dawnoftime.onceuponatown.town.generation.TownMapUtils.Corner;
import org.dawnoftime.onceuponatown.town.generation.bud.BuildBud;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;

import static org.dawnoftime.onceuponatown.Config.MAXI_Y_DIFFERENCE;
import static org.dawnoftime.onceuponatown.town.generation.TownMapUtils.rectangularPosIterator;

/**
 * Buildings are places where NPCs may work, sleep, relax, hide... <br>
 * Roads, Bridges or Walls are not Buildings
 */
public class Building extends Build {
    private final BuildVariant variant;
    private int claimedBeds;
    private HashMap<Profession, Integer> claimedProfessions = new HashMap<>();

    public Building(BuildType buildType, BuildVariant buildVariant, int level) {
        super(buildType, level);
        this.variant = buildVariant;
    }

    protected Building(Culture culture, CompoundTag tag) {
        super(culture, tag);
        variant = this.getBuildType().getBuildVariants().get(tag.getString("BuildVariant"));
    }

    @Override
    public CompoundTag saveNbt() {
        CompoundTag tag = super.saveNbt();
        tag.putString("BuildVariant", variant.getId());
        return tag;
    }

    @Override
    public void onAddedToTown(ProtoTown town) {
        // Trying to find all the adjacent Roads to extend them and add new Buds.
        // Iterating on a "1 block bigger rectangle" to find all the adjacent Roads.
        HashSet<MapPart> mapPos = new HashSet<>();
        for (BlockPos.MutableBlockPos pos : rectangularPosIterator(this.getOriginPos().north().west(), this.getSizeX() + 2, this.getSizeZ() + 2)) {
            mapPos.add(town.getMapPartAtPos(pos));
            // TODO lock the shape on the corresponding pos.
        }
        for (MapPart mapPart : mapPos) {
            if (mapPart instanceof Road road) {
                road.tryGrowing(town);
            }
        }
    }

    @Override
    public int getSuitableAltitudeForPlacement(BlockPos originPos, Direction dir) {
        return getEntranceYPos(originPos, dir).getY();
    }

    /**
     * @param originPos North-West BlockPos of the Building.
     * @param dir       Direction of the Building.
     * @return The BlockPos of the main entrance of this Building, with the given parameters.
     */
    private BlockPos getEntranceYPos(BlockPos originPos, Direction dir) {
        // TODO Return the entrance waypoint instead
        // TODO Fix this function, it doesn't seem to work properly
        // Assuming that the door is in the middle of the North side.
        int offset = dir.getAxis() == Direction.Axis.X ? this.getSizeZ(dir) : this.getSizeX(dir);
        return Corner.NORTH_WEST.getCornerPosOfBuild(originPos, this, dir, Corner.getCornerNextToDir(dir.getOpposite(), false)).relative(dir.getClockWise(), offset / 2);
    }

    @Override
    public int getYOnPosForTestedOrigin(BlockPos originPos, BlockPos testedPos) {
        return originPos != null ? originPos.getY() : super.getYOnPosForTestedOrigin(null, testedPos);
    }

    @Override
    public int getNorthSizeX() {
        return variant.getDimensions().getX();
    }

    @Override
    public int getNorthSizeZ() {
        return variant.getDimensions().getZ();
    }

    @Override
    public int getSizeY() {
        return variant.getDimensions().getY();
    }

    public int getTotalBeds() {
        return getBuildType().getLevels().get(getLevel() - 1).dwellingSlots();
    }

    public int getClaimedBeds() {
        return claimedBeds;
    }

    public int getFreeBeds() {
        return getTotalBeds() - getClaimedBeds();
    }

    public void addResident() {
        claimedBeds++;
    }

    public void setClaimedBeds(int i) {
        claimedBeds = i;
    }

    public HashMap<Profession, Integer> getTotalProfessions() {
        return getBuildType().getLevels().get(getLevel() - 1).workingSlots();
    }

    public Profession getNextAvailableProfession() {
        HashMap<Profession, Integer> totalProfessions = getTotalProfessions();

        for (Profession profession : totalProfessions.keySet()) {
            if (claimedProfessions.getOrDefault(profession, 0) < totalProfessions.get(profession)) {
                return profession;
            }
        }
        return null;
    }

    public void addWorker(Profession profession) {
        claimedProfessions.compute(profession, (p, i) -> i == null ? 1 : i + 1);
    }

    public HashMap<Profession, Integer> getClaimedProfessions() {
        return claimedProfessions;
    }

    public void setOccupiedProfessions(HashMap<Profession, Integer> professions) {
        claimedProfessions = professions;
    }

    @Override
    public boolean canBeBuiltOnBud(ProtoTown town, BuildBud buildBud, Direction dir) {
        BlockPos testedOriginPos = buildBud.findOriginPosOfBuild(this, dir);
        for (BlockPos.MutableBlockPos testedPos : rectangularPosIterator(testedOriginPos, this.getSizeX(dir), this.getSizeZ(dir))) {
            if (!town.isFreeAt(testedPos) || Math.abs(testedPos.getY() - this.getYOnPosForTestedOrigin(testedOriginPos, testedPos)) > MAXI_Y_DIFFERENCE) {
                return false;
            }
        }
        return true;
    }

    @Override
    public StructurePiece createStructurePiece(Culture culture, @Nullable ProtoTown town) {
        return new BuildingPiece(this, town);
    }

    @Override
    public CompoundTag getGuiDescription() {
        CompoundTag descriptionTag = super.getGuiDescription();
        descriptionTag.putByte("Category", Build.BUILDING);
        if (getBuildType() instanceof BuildingType type) {
            descriptionTag.putString("IconItem", Ouat.COMMON.getResourceLocation(type.getIconItem()).toString());
        }
        return descriptionTag;
    }

    public ResourceLocation getSchematicResourceLocation() {
        return variant.getSchematicRl(this.getLevel());
    }

    @Override
    public SchematicContent getSchematicContent(ResourceManager resourceManager) {
        return variant.getSchematicContent(resourceManager, this.getLevel()).rotate(this.getDirection());
    }

    @Override
    protected byte getBuildCategory() {
        return BUILDING;
    }

    public String toSafeString() {
        return getBuildType().getId() + "_" + getOriginPos().getX() + "_" + getOriginPos().getY() + "_" + getOriginPos().getZ();
    }

    public String getVariantId() {
        return variant.getId();
    }

    public BuildVariant getVariant() {
        return variant;
    }
}
