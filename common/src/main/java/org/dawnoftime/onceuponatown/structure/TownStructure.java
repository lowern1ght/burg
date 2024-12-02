package org.dawnoftime.onceuponatown.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.Utils;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.culture.CultureManager;
import org.dawnoftime.onceuponatown.registry.StructureTypeRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.dawnoftime.onceuponatown.town.CorruptedTownException;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static org.dawnoftime.onceuponatown.Utils.blockPosToString;

public class TownStructure extends Structure {
    public final String cultureID;
    public static final Codec<TownStructure> CODEC = RecordCodecBuilder.create((p) -> p.group(settingsCodec(p), Codec.STRING.fieldOf("culture").forGetter((town) -> town.cultureID)).apply(p, TownStructure::new));

    public TownStructure(StructureSettings settings, String cultureID) {
        super(settings);
        this.cultureID = cultureID;
    }

    protected @NotNull Optional<GenerationStub> findGenerationPoint(@NotNull GenerationContext context) {
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, (builder) -> this.generatePieces(builder, context));
    }

    private void generatePieces(StructurePiecesBuilder builder, GenerationContext context) {
        try {
            Culture culture = CultureManager.getCultureById(this.cultureID);
            ChunkGenerator chunk = context.chunkGenerator();
            int townHeight = chunk.getFirstOccupiedHeight(context.chunkPos().getMinBlockX(), context.chunkPos().getMinBlockZ(), Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            BlockPos townCenterPos = new BlockPos(context.chunkPos().getMinBlockX(), townHeight, context.chunkPos().getMinBlockZ());

            Ouat.info("Town at " + blockPosToString(townCenterPos) + " : started generating pieces...");
            // TODO Improve the code to give names to new Towns.
            String name = "Town " + Mth.nextInt(RandomSource.create(), 0, 100);
            ProtoTown town = new ProtoTown(culture, name, townCenterPos, (x, z) -> chunk.getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState()));
            if(town.buildStarterPack()){
                // Generate the pieces for each Build. The first one contains the data to create the Town.
                for(int i = 0; i < town.getBuilds().size(); i++){
                    builder.addPiece((town.getBuilds().get(i).generatePieces(context.structureTemplateManager(), culture, i == 0 ? town : null)));
                }
            }else{
                Ouat.info("Could not generate a %s Town in %s.".formatted(this.cultureID, blockPosToString(townCenterPos)));
            }
        }catch(CorruptedCultureException | CorruptedTownException e){
            Ouat.error(e.getMessage());
        }
    }

    public @NotNull StructureType<?> type() {
        return StructureTypeRegistry.REGISTRY.TOWN_STRUCTURE.get();
    }
}
