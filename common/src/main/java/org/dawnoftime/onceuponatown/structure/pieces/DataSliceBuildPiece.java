package org.dawnoftime.onceuponatown.structure.pieces;

import org.dawnoftime.onceuponatown.building.type.BuildingType;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.registry.StructurePieceRegistry;
import org.dawnoftime.onceuponatown.town.TownManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;

public class DataSliceBuildPiece extends BuildPiece {
    public final ProtoTown protoTown;
    public boolean townRegistered;

    public DataSliceBuildPiece(StructureTemplateManager manager, ResourceLocation resourceLocation, BlockPos pos, Rotation rotation, BuildingType buildingType, ProtoTown protoTown) {
        super(StructurePieceRegistry.REGISTRY.DATA_SLICE_BUILD_PIECE.get(), manager, resourceLocation, pos, rotation, buildingType);
        this.protoTown = protoTown;
        this.townRegistered = false;
    }

    public DataSliceBuildPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(StructurePieceRegistry.REGISTRY.DATA_SLICE_BUILD_PIECE.get(), manager, tag);
        this.protoTown = new ProtoTown(tag.getCompound("TownMap"));
        this.townRegistered = tag.getBoolean("TownRegistered");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        CompoundTag townMapTag = new CompoundTag();
        this.protoTown.writeNBT(townMapTag);
        tag.put("TownMap", townMapTag);
        tag.putBoolean("TownRegistered", this.townRegistered);
    }

    @Override
    public void postProcess(WorldGenLevel worldGenLevel, StructureManager manager, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        super.postProcess(worldGenLevel, manager, chunkGenerator, random, box, chunkPos, pos);
        if (!this.townRegistered) {
            TownManager.initGeneratedTown(worldGenLevel.getLevel(), Culture.FAKE_PLAINS, this.protoTown);
            this.townRegistered = true;
        }
    }

    @Override
    protected void handleDataMarker(String pName, BlockPos pPos, ServerLevelAccessor pLevel, RandomSource pRandom, BoundingBox pBox) {}
}
