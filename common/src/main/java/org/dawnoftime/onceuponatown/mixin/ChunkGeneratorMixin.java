package org.dawnoftime.onceuponatown.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.block.Rotation;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.blockentity.TownAnchorBlockEntity;
import org.dawnoftime.onceuponatown.building.schematic.BuildSchematic;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.registry.BlockRegistry;
import org.dawnoftime.onceuponatown.town.ConnectionPoint;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mixin(StructureStart.class)
public class ChunkGeneratorMixin {

    @Inject(
        method = "placeInChunk",
        at = @At("RETURN")
    )
    private void onStructurePlaced(WorldGenLevel level, StructureManager manager,
                                    ChunkGenerator generator, RandomSource random,
                                    BoundingBox chunkBB, ChunkPos chunkPos, CallbackInfo ci) {
        StructureStart self = (StructureStart)(Object)this;

        ResourceLocation structureKey = level.registryAccess()
            .registryOrThrow(Registries.STRUCTURE)
            .getKey(self.getStructure());

        if (structureKey == null) return;
        if (!structureKey.equals(new ResourceLocation(Ouat.MOD_ID, "plains_town"))) return;

        // Use the starter piece (first in list) as anchor reference.
        // The full structure BB shifts with random growth; the starter piece is always the settlement.
        BoundingBox starterBb = self.getPieces().isEmpty()
            ? self.getBoundingBox()
            : self.getPieces().get(0).getBoundingBox();
        BlockPos center = new BlockPos(
            starterBb.minX() + (starterBb.maxX() - starterBb.minX()) / 2,
            starterBb.minY(),
            starterBb.minZ() + (starterBb.maxZ() - starterBb.minZ()) / 2
        );

        ServerLevel serverLevel = level.getLevel();

        // All level mutations must happen on the server thread, not the chunk-gen thread
        serverLevel.getServer().execute(() -> {
            int surfaceY = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, center.getX(), center.getZ());
            BlockPos anchorPos = new BlockPos(center.getX(), surfaceY + 10, center.getZ());

            // Guard against duplicate init: check XZ proximity, not exact pos.
            // MOTION_BLOCKING_NO_LEAVES can return slightly different Y values for the same XZ
            // across multiple chunk-gen firings, so an exact-pos check would allow duplicates.
            boolean alreadyExists = LevelTowns.get(serverLevel).getAllTownEntries().stream()
                .anyMatch(e -> {
                    BlockPos existing = BlockPos.of(e.getKey());
                    int dx = existing.getX() - center.getX();
                    int dz = existing.getZ() - center.getZ();
                    return dx * dx + dz * dz < 64 * 64;
                });
            if (alreadyExists) return;

            serverLevel.setBlock(anchorPos, BlockRegistry.TOWN_ANCHOR.defaultBlockState(), 3);

            TownAnchorBlockEntity be;
            try {
                be = (TownAnchorBlockEntity) serverLevel.getBlockEntity(anchorPos);
            } catch (ClassCastException e) {
                return;
            }
            if (be == null) return;

            Town town = new Town();

            record PieceEntry(BlockPos bbMin, String defId, BoundingBox pieceBb, List<ConnectionPoint> allPoints, Rotation rotation) {}
            List<PieceEntry> entries = new ArrayList<>();
            Set<BlockPos> allJigsawPositions = new HashSet<>();

            // Pass 1: collect all jigsaw positions across every recognized piece
            for (StructurePiece piece : self.getPieces()) {
                if (piece instanceof PoolElementStructurePiece poolPiece) {
                    String defId = extractDefId(poolPiece);
                    if (defId != null && BuildingDataHandler.get(defId).isPresent()) {
                        BoundingBox pieceBb = piece.getBoundingBox();
                        BlockPos bbMin = new BlockPos(pieceBb.minX(), pieceBb.minY(), pieceBb.minZ());
                        Rotation rotation = poolPiece.getRotation();
                        List<ConnectionPoint> allPoints =
                            BuildSchematic.readJigsawPoints(serverLevel, bbMin, defId, rotation);
                        entries.add(new PieceEntry(bbMin, defId, pieceBb, allPoints, rotation));
                        BuildSchematic.readAllJigsawPositions(serverLevel, bbMin, defId, rotation)
                            .forEach(allJigsawPositions::add);
                    }
                }
            }

            // Pass 2: a connection is free only if no peer jigsaw block sits one step ahead
            for (PieceEntry entry : entries) {
                List<ConnectionPoint> free = entry.allPoints().stream()
                    .filter(cp -> !allJigsawPositions.contains(cp.pos().relative(cp.direction())))
                    .toList();
                town.registerBuilding(entry.bbMin(), entry.defId(), free, entry.pieceBb(), entry.rotation());
            }

            // Pass 3: pieces with no building def (vanilla pieces, unrecognized jigsaw elements) are not
            // registered above, but their footprints must still block path placement.
            Set<String> registeredDefIds = new HashSet<>();
            for (PieceEntry entry : entries) registeredDefIds.add(entry.defId());
            for (StructurePiece piece : self.getPieces()) {
                if (piece instanceof PoolElementStructurePiece poolPiece) {
                    String defId = extractDefId(poolPiece);
                    if (defId == null || !registeredDefIds.contains(defId)) {
                        town.addBlockedZone(piece.getBoundingBox());
                    }
                }
            }

            town.addStock(Items.OAK_LOG, 25);

            be.setTown(town);
            LevelTowns.get(serverLevel).registerTown(anchorPos, town);
            LevelTowns.get(serverLevel).markDirty();
        });
    }

    private String extractDefId(PoolElementStructurePiece piece) {
        String templatePath = piece.getElement().toString();
        if (!templatePath.contains(Ouat.MOD_ID)) return null;
        // Sort by id length descending so "settlement_2" is checked before "settlement",
        // preventing the shorter id from matching as a substring of a longer one.
        return BuildingDataHandler.getAll().stream()
            .sorted((a, b) -> Integer.compare(b.id.length(), a.id.length()))
            .filter(def -> templatePath.contains(def.id))
            .map(def -> def.id)
            .findFirst()
            .orElse(null);
    }
}
