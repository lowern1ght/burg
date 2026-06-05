package org.dawnoftime.onceuponatown.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.culture.ServerCultures;
import org.dawnoftime.onceuponatown.registry.StructureTypeRegistry;
import org.dawnoftime.onceuponatown.town.CorruptedTownException;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static org.dawnoftime.onceuponatown.Utils.blockPosToString;

public class TownStructure extends Structure {
    public final String cultureId;
    public static final Codec<TownStructure> CODEC = RecordCodecBuilder.create((p) -> p.group(settingsCodec(p), Codec.STRING.fieldOf("culture").forGetter((town) -> town.cultureId)).apply(p, TownStructure::new));

    public TownStructure(StructureSettings structureSettings, String cultureId) {
        super(structureSettings);
        this.cultureId = cultureId;
    }

    @Override
    protected @NotNull Optional<GenerationStub> findGenerationPoint(@NotNull GenerationContext context) {
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, (builder) -> this.generatePieces(builder, context));
    }

    private void generatePieces(StructurePiecesBuilder builder, GenerationContext context) {
        try {
            Culture culture = ServerCultures.getCultureOrDefault(cultureId);
            ChunkGenerator chunkGenerator = context.chunkGenerator();
            int townHeight = chunkGenerator.getFirstOccupiedHeight(context.chunkPos().getMinBlockX(), context.chunkPos().getMinBlockZ(), Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            BlockPos townCenterPos = new BlockPos(context.chunkPos().getMinBlockX(), townHeight, context.chunkPos().getMinBlockZ());
            // TODO Improve the code to give names to new Towns.
            ProtoTown town = ProtoTown.create(culture, townCenterPos, (x, z) -> chunkGenerator.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()));
            if (town != null) {
                // Creates a StructurePiece for each town Build. The first one contains the data to register the Town on the future ServerLevel
                for (int i = 0; i < town.getBuilds().size(); ++i) {
                    builder.addPiece((town.getBuilds().get(i).createStructurePiece(culture, i == 0 ? town : null)));
                }
            } else {
                Ouat.info("Failed to generate a %s Town in %s.".formatted(cultureId, blockPosToString(townCenterPos)));
            }
        } catch (CorruptedCultureException | CorruptedTownException e) {
            Ouat.error(e.getMessage());
        }
    }

    public @NotNull StructureType<?> type() {
        return StructureTypeRegistry.REGISTRY.TOWN_STRUCTURE.get();
    }
}
