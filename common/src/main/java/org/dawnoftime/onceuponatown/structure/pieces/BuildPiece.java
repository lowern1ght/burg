package org.dawnoftime.onceuponatown.structure.pieces;

import org.dawnoftime.onceuponatown.building.type.BuildingType;
import org.dawnoftime.onceuponatown.registry.StructurePieceRegistry;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

public class BuildPiece extends TemplateStructurePiece {
    public BuildPiece(StructureTemplateManager manager, ResourceLocation resourceLocation, BlockPos pos, Rotation rotation) {
        this(StructurePieceRegistry.REGISTRY.BUILDING_PIECE.get(), manager, resourceLocation, pos, rotation);
    }

    protected BuildPiece(StructurePieceType type, StructureTemplateManager manager, ResourceLocation resourceLocation, BlockPos pos, Rotation rotation) {
        super(type, 0, manager, resourceLocation, resourceLocation.toString(), new StructurePlaceSettings().setRotation(rotation), pos);
    }

    public BuildPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(StructurePieceRegistry.REGISTRY.BUILDING_PIECE.get(), tag, manager, (rl) -> new StructurePlaceSettings().setRotation(Rotation.valueOf(tag.getString("Rot"))));
        //TODO: Read building type
    }

    protected BuildPiece(StructurePieceType type, StructureTemplateManager manager, CompoundTag tag) {
        super(type, tag, manager, (p) -> new StructurePlaceSettings().setRotation(Rotation.valueOf(tag.getString("Rot"))));
        //TODO: Read building type
    }

    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", this.placeSettings.getRotation().name());
        //TODO : Write building type
    }

    public void postProcess(WorldGenLevel worldGenLevel, StructureManager manager, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        int i = worldGenLevel.getHeight(Heightmap.Types.WORLD_SURFACE_WG, this.templatePosition.getX(), this.templatePosition.getZ());
        this.templatePosition = new BlockPos(this.templatePosition.getX(), i - 1, this.templatePosition.getZ());
        super.postProcess(worldGenLevel, manager, chunkGenerator, random, box, chunkPos, pos);
    }

    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor levelAccessor, RandomSource random, BoundingBox box) {

    }
}
