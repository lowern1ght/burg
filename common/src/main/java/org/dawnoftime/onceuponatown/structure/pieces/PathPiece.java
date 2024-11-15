package org.dawnoftime.onceuponatown.structure.pieces;

import org.dawnoftime.onceuponatown.registry.StructurePieceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

@Deprecated
public class PathPiece extends StructurePiece {
    private BlockPos originPos;
    private int sizeX;
    private int sizeZ;

    public PathPiece(BlockPos originPos, int sizeX, int sizeZ) {
        super(StructurePieceRegistry.REGISTRY.PATH_PIECE.get(), 0, makeBoundingBox(originPos.getX(), originPos.getY(), originPos.getZ(), Direction.NORTH, sizeX, 1, sizeZ));
        this.originPos = originPos;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
    }

    public PathPiece(CompoundTag tag) {
        super(StructurePieceRegistry.REGISTRY.PATH_PIECE.get(), tag);
        this.originPos = NbtUtils.readBlockPos(tag.getCompound("OriginPos"));
        this.sizeX = tag.getInt("SizeX");
        this.sizeZ = tag.getInt("SizeZ");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.put("OriginPos", NbtUtils.writeBlockPos(this.originPos));
        tag.putInt("SizeX", this.sizeX);
        tag.putInt("SizeZ", this.sizeZ);
    }

    @Override
    public void postProcess(WorldGenLevel worldGenLevel, StructureManager manager, ChunkGenerator generator, RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos) {
        int maxX = this.originPos.getX() + this.sizeX - 1;
        int maxZ = this.originPos.getZ() + this.sizeZ - 1;
        for (int x = this.originPos.getX(); x <= maxX; ++x) {
            for (int z = this.originPos.getZ(); z <= maxZ ; ++z) {
                int y = this.originPos.getY();
                int rand = Mth.nextInt(random, 0, 100);
                BlockState state;
                if (rand <= 6) {
                    state = Blocks.PACKED_MUD.defaultBlockState();
                } else if (rand > 6 && rand <= 8) {
                    state = Blocks.GRASS_BLOCK.defaultBlockState();
                } else if (rand > 8 && rand <= 10) {
                    state = Blocks.COARSE_DIRT.defaultBlockState();
                } else {
                    state = Blocks.DIRT_PATH.defaultBlockState();
                }
                placeBlock(worldGenLevel, state, x, y, z, box);
                /* if (box.isInside(x, y, z)) {
                    worldGenLevel.getLevel().setBlock(new BlockPos(x, y, z), Blocks.DIRT_PATH.defaultBlockState(), 2);
                }*/
            }
        }
    }
}
