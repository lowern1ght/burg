package org.dawnoftime.onceuponatown.structure.pieces;

import net.minecraft.server.MinecraftServer;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.SliceBuild;
import org.dawnoftime.onceuponatown.building.schematic.SchematicContent;
import org.dawnoftime.onceuponatown.building.type.SliceBuildType;
import org.dawnoftime.onceuponatown.construction.BlockInfo;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.culture.CultureManager;
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
    private final BlockPos originPos;
    private final SliceBuild<? extends SliceBuildType> sliceBuild;
    private final String cultureId;
    private final @Nullable CompoundTag townTag;

    /**
     * Generate a Piece that will manage the world generation of the SliceBuilds.
     * @param originPos BlockPos corner of the building. BE CAREFUL ! If the slice build was extended and found in the process a lower Y, this BlockPos should have the corresponding Y value !
     * @param orientation Direction of the building.
     * @param build Instance of building that is placed.
     */
    public SliceBuildPiece(BlockPos originPos, Direction orientation, SliceBuild<? extends SliceBuildType> build, Culture culture, @Nullable ProtoTown protoTown) {
        super(StructurePieceRegistry.REGISTRY.SLICE_BUILD_PIECE.get(), 0, makeBoundingBox(originPos.getX(), originPos.getY(), originPos.getZ(), orientation, build.getNorthSizeX(), build.getYSize(), build.getNorthSizeZ()));
        // We set the orientation to north, because the direction will try to rotate the BlockState and we already managed it.
        this.setOrientation(Direction.NORTH);
        this.originPos = originPos;
        this.sliceBuild = build;
        this.cultureId = culture.getId();
        this.townTag = (protoTown == null) ? null : protoTown.writeNBT();
    }

    public SliceBuildPiece(CompoundTag tag) {
        super(StructurePieceRegistry.REGISTRY.SLICE_BUILD_PIECE.get(), tag);
        // We set the orientation to north, because the direction will try to rotate the BlockState and we already managed it.
        this.setOrientation(Direction.NORTH);
        this.originPos = NbtUtils.readBlockPos(tag.getCompound("OriginPos"));
        this.cultureId = tag.getString("CultureId");
        Culture culture = CultureManager.getCultureById(this.cultureId);
        this.sliceBuild = new SliceBuild<>(culture, SliceBuildType.class, tag.getCompound("Build"));
        this.townTag = (tag.contains("Town")) ? tag.getCompound("Town") : null;
    }

    @Override
    protected void addAdditionalSaveData(@NotNull StructurePieceSerializationContext context, CompoundTag tag) {
        tag.put("OriginPos", NbtUtils.writeBlockPos(this.originPos));
        tag.putString("CultureId", this.cultureId);
        tag.put("Build", this.sliceBuild.writeNBT());
        if(this.townTag != null){
            tag.put("Town", this.townTag);
        }
    }

    @Override
    public void postProcess(@NotNull WorldGenLevel level, @NotNull StructureManager structureManager, @NotNull ChunkGenerator generator, @NotNull RandomSource random, @NotNull BoundingBox box, @NotNull ChunkPos chunkPos, @NotNull BlockPos pos) {
        MinecraftServer server = level.getServer();
        if(server != null){
            SchematicContent schematicContent = this.sliceBuild.getSchematicContent(server.getResourceManager());
            for(BlockInfo block: schematicContent.getBlocks()){
                this.placeBlock(level, block.state(), block.pos().getX(), block.pos().getY(), block.pos().getZ(), box);
            }
            //for(EntityInfo entity: schematicContent.getEntities()){} TODO Place the entities !
            if(this.townTag != null){
                LevelTowns manager = LevelTowns.get(level.getLevel());
                manager.initProtoTown(this.townTag);
            }
        }else{
            Ouat.debug("PAS DE SERVER ????"); //TODO Is it even possible to have this bug ?
        }
    }
}
