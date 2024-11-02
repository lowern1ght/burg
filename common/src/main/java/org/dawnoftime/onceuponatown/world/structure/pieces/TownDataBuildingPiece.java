package org.dawnoftime.onceuponatown.world.structure.pieces;

import org.dawnoftime.onceuponatown.town.building.type.BuildingType;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.registry.OuatStructurePiecesRegistry;
import org.dawnoftime.onceuponatown.town.TownManager;
import org.dawnoftime.onceuponatown.town.TownMap;
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

public class TownDataBuildingPiece extends BuildingPiece {
    public final TownMap townMap;
    public boolean townRegistered;

    public TownDataBuildingPiece(StructureTemplateManager manager, ResourceLocation resourceLocation, BlockPos pos, Rotation rotation, BuildingType buildingType, TownMap townMap) {
        super(OuatStructurePiecesRegistry.STRUCTURE_PIECE_REGISTRY.TOWN_DATA_BUILDING_PIECE.get(), manager, resourceLocation, pos, rotation, buildingType);
        this.townMap = townMap;
        this.townRegistered = false;
    }

    public TownDataBuildingPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(OuatStructurePiecesRegistry.STRUCTURE_PIECE_REGISTRY.TOWN_DATA_BUILDING_PIECE.get(), manager, tag);
        this.townMap = new TownMap(tag.getCompound("TownMap"));
        this.townRegistered = tag.getBoolean("TownRegistered");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        CompoundTag townMapTag = new CompoundTag();
        this.townMap.saveToNBT(townMapTag);
        tag.put("TownMap", townMapTag);
        tag.putBoolean("TownRegistered", this.townRegistered);
    }

    @Override
    public void postProcess(WorldGenLevel worldGenLevel, StructureManager manager, ChunkGenerator chunkGenerator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        super.postProcess(worldGenLevel, manager, chunkGenerator, random, box, chunkPos, pos);
        if (!this.townRegistered) {
            TownManager.createNewTownWorldGen(worldGenLevel.getLevel(), Culture.FAKE_PLAINS, this.townMap);
            this.townRegistered = true;
        }
    }

    @Override
    protected void handleDataMarker(String pName, BlockPos pPos, ServerLevelAccessor pLevel, RandomSource pRandom, BoundingBox pBox) {}
}
