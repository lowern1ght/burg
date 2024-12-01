package org.dawnoftime.onceuponatown.structure;

import net.minecraft.core.BlockPos;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.Build;
import org.dawnoftime.onceuponatown.building.type.BuildType;
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
            int townHeight = context.chunkGenerator().getFirstOccupiedHeight(context.chunkPos().getMinBlockX(), context.chunkPos().getMinBlockZ(), Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            BlockPos townCenterPos = new BlockPos(context.chunkPos().getMinBlockX(), townHeight, context.chunkPos().getMinBlockZ());

            Ouat.info("Town at + " + townCenterPos.toShortString() + " : started generating pieces...");
            ProtoTown town = new ProtoTown(culture, "Saucisson", townCenterPos);
            town.buildStarterPack();

            // Generate the pieces for each Build.
            for(int i = 0; i < town.getBuilds().size(); i++){
                Build<BuildType> build = town.getBuilds().get(i);
                build.generatePieces(builder, context.structureTemplateManager(), i == 0 ? town : null);
            }
        }catch(CorruptedCultureException | CorruptedTownException e){
            Ouat.error(e.getMessage());
        }
    }

    public @NotNull StructureType<?> type() {
        return StructureTypeRegistry.REGISTRY.TOWN_STRUCTURE.get();
    }
}
