package org.dawnoftime.onceuponatown.structure.pieces;

import net.minecraft.core.Direction;
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
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BuildingPiece extends TemplateStructurePiece {
    private final @Nullable CompoundTag townTag;

    public BuildingPiece(StructureTemplateManager manager, ResourceLocation resourceLocation, BlockPos pos, Direction dir, @Nullable ProtoTown protoTown) {
        super(StructurePieceRegistry.REGISTRY.BUILDING_PIECE.get(), 0, manager, resourceLocation, resourceLocation.toString(), new StructurePlaceSettings().setRotation(rotFromDir(dir)), pos);
        this.townTag = (protoTown == null) ? null : protoTown.writeNBT();
    }

    public BuildingPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(StructurePieceRegistry.REGISTRY.BUILDING_PIECE.get(), tag, manager, (rl) -> new StructurePlaceSettings().setRotation(Rotation.valueOf(tag.getString("Rot"))));
        this.townTag = (tag.contains("Town")) ? tag.getCompound("Town") : null;
    }

    protected void addAdditionalSaveData(@NotNull StructurePieceSerializationContext context, @NotNull CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", this.placeSettings.getRotation().name());
        if(this.townTag != null){
            tag.put("Town", this.townTag);
        }
    }

    public void postProcess(WorldGenLevel level, @NotNull StructureManager manager, @NotNull ChunkGenerator chunkGenerator, @NotNull RandomSource random, @NotNull BoundingBox box, @NotNull ChunkPos chunkPos, @NotNull BlockPos pos) {
        int i = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, this.templatePosition.getX(), this.templatePosition.getZ());
        this.templatePosition = new BlockPos(this.templatePosition.getX(), i - 1, this.templatePosition.getZ());
        super.postProcess(level, manager, chunkGenerator, random, box, chunkPos, pos);
        if(this.townTag != null){
            LevelTowns levelTowns = LevelTowns.get(level.getLevel());
            levelTowns.initProtoTown(this.townTag);
        }
    }

    protected void handleDataMarker(@NotNull String name, @NotNull BlockPos pos, @NotNull ServerLevelAccessor levelAccessor, @NotNull RandomSource random, @NotNull BoundingBox box) {}

    private static Rotation rotFromDir(Direction dir) {
        return switch (dir) {
            case SOUTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.CLOCKWISE_90;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}
