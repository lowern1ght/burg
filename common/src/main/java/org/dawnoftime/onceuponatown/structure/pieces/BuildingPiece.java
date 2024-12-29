package org.dawnoftime.onceuponatown.structure.pieces;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.material.FluidState;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.Building;
import org.dawnoftime.onceuponatown.building.schematic.SchematicContent;
import org.dawnoftime.onceuponatown.construction.BlockInfo;
import org.dawnoftime.onceuponatown.registry.StructurePieceRegistry;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BuildingPiece extends StructurePiece {
    private final ResourceLocation schematicLocation;
    private final BlockPos originPos;
    private final Direction buildingOrientation;
    private final @Nullable CompoundTag townTag;

    public BuildingPiece(Building building, @Nullable ProtoTown protoTown) {
        super(StructurePieceRegistry.REGISTRY.BUILDING_PIECE.get(), 0,
                makeBoundingBox(building.getOriginPos().getX(), building.getOriginPos().getY(), building.getOriginPos().getZ(), building.getDirection(), building.getNorthSizeX(), building.getSizeY(), building.getNorthSizeZ()));
        setOrientation(Direction.NORTH); // Using our custom orientation system instead
        schematicLocation = building.getSchematicResourceLocation();
        originPos = building.getOriginPos();
        buildingOrientation = building.getDirection();
        townTag = (protoTown == null) ? null : protoTown.writeNBT();
    }

    public BuildingPiece(CompoundTag tag) {
        super(StructurePieceRegistry.REGISTRY.BUILDING_PIECE.get(), tag);
        setOrientation(Direction.NORTH); // Using our custom orientation system instead
        schematicLocation = new ResourceLocation(tag.getString("SchematicLocation"));
        originPos = NbtUtils.readBlockPos(tag.getCompound("OriginPos"));
        buildingOrientation = Direction.byName(tag.getString("BuildingOrientation"));
        townTag = (tag.contains("FutureTownData")) ? tag.getCompound("FutureTownData") : null;
    }

    @Override
    protected void addAdditionalSaveData(@NotNull StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("SchematicLocation", schematicLocation.toString());
        tag.put("OriginPos", NbtUtils.writeBlockPos(originPos));
        tag.putString("BuildingOrientation", buildingOrientation.getName());
        if (townTag != null) {
            tag.put("FutureTownData", townTag);
        }
    }

    @Override
    public void postProcess(@NotNull WorldGenLevel level, @NotNull StructureManager structureManager, @NotNull ChunkGenerator chunkGenerator, @NotNull RandomSource random, @NotNull BoundingBox boundingBox, @NotNull ChunkPos chunkPos, @NotNull BlockPos pos) {
        SchematicContent schematicContent = SchematicContent.createFromDataPack(schematicLocation, level.getServer().getResourceManager());
        if (schematicContent != null) {
            schematicContent.rotate(buildingOrientation);
            BlockPos.MutableBlockPos cursorPos = new BlockPos(0, 0, 0).mutable();
            for (BlockInfo block : schematicContent.getBlocks()) {
                cursorPos.set(originPos.getX(), originPos.getY(), originPos.getZ());
                cursorPos.move(block.pos());
                if (boundingBox.isInside(cursorPos)) {
                    level.setBlock(cursorPos, block.state(), 2);
                    FluidState fluidState = level.getFluidState(cursorPos);
                    if (!fluidState.isEmpty()) {
                        level.scheduleTick(cursorPos, fluidState.getType(), 0);
                    }
                    // Vanilla if (SHAPE_CHECK_BLOCKS.contains(blockstate.getBlock())) {level.getChunk(blockPos).markPosForPostprocessing(blockPos);}
                }
                // TODO Place the schematic entities as well
                if (townTag != null) {
                    LevelTowns.of(level.getLevel()).initProtoTown(townTag);
                }
            }
        } else {
            Ouat.error("Failed to place a village building during world generation. Check the file at " + schematicLocation);
        }
    }
}
