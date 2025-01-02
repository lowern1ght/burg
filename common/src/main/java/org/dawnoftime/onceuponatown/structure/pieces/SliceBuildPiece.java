package org.dawnoftime.onceuponatown.structure.pieces;

import net.minecraft.world.level.material.FluidState;
import org.dawnoftime.onceuponatown.building.NpcBuild;
import org.dawnoftime.onceuponatown.building.SliceBuild;
import org.dawnoftime.onceuponatown.building.schematic.SchematicContent;
import org.dawnoftime.onceuponatown.construction.BlockInfo;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.culture.ServerCultures;
import org.dawnoftime.onceuponatown.registry.StructurePieceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SliceBuildPiece extends StructurePiece {
    private final String cultureId;
    private final SliceBuild sliceBuild;
    private final BlockPos originPos;
    private final @Nullable CompoundTag townTag;

    public SliceBuildPiece(String cultureId, SliceBuild sliceBuild, @Nullable ProtoTown protoTown) {
        super(StructurePieceRegistry.REGISTRY.SLICE_BUILD_PIECE.get(), 0, makeBoundingBox(sliceBuild.getOriginPos().getX(), sliceBuild.getOriginPos().getY(), sliceBuild.getOriginPos().getZ(), sliceBuild.getDirection(), sliceBuild.getNorthSizeX(), sliceBuild.getSizeY(), sliceBuild.getNorthSizeZ()));
        setOrientation(Direction.NORTH); // Using our custom orientation system instead
        this.cultureId = cultureId;
        this.sliceBuild = sliceBuild;
        // OriginPos : BE CAREFUL ! If the slice build was extended and found in the process a lower Y, this BlockPos should have the corresponding Y value !
        this.originPos = sliceBuild.getOriginPos();
        this.townTag = (protoTown == null) ? null : protoTown.writeNBT();
    }

    public SliceBuildPiece(CompoundTag tag) {
        super(StructurePieceRegistry.REGISTRY.SLICE_BUILD_PIECE.get(), tag);
        setOrientation(Direction.NORTH); // Using our custom orientation system instead
        cultureId = tag.getString("CultureId");
        Culture culture = ServerCultures.getCultureOrDefault(cultureId);
        sliceBuild = (SliceBuild) NpcBuild.load(culture, tag.getCompound("SliceBuild"));
        originPos = NbtUtils.readBlockPos(tag.getCompound("OriginPos"));
        townTag = (tag.contains("FutureTownData")) ? tag.getCompound("FutureTownData") : null;
    }

    @Override
    protected void addAdditionalSaveData(@NotNull StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("CultureId", cultureId);
        tag.put("SliceBuild", sliceBuild.save());
        tag.put("OriginPos", NbtUtils.writeBlockPos(originPos));
        if (townTag != null) {
            tag.put("FutureTownData", townTag);
        }
    }

    @Override
    public void postProcess(@NotNull WorldGenLevel level, @NotNull StructureManager structureManager, @NotNull ChunkGenerator chunkGenerator, @NotNull RandomSource random, @NotNull BoundingBox boundingBox, @NotNull ChunkPos chunkPos, @NotNull BlockPos pos) {
        SchematicContent schematicContent = sliceBuild.getSchematicContent(level.getServer().getResourceManager());
        BlockPos.MutableBlockPos cursorPos = new BlockPos(0, 0, 0).mutable();
        for(BlockInfo block: schematicContent.getBlocks()) {
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
        }
        // TODO Place the schematic entities as well
        if (townTag != null) {
            LevelTowns.of(level.getLevel()).initProtoTown(townTag);
        }
    }
}
