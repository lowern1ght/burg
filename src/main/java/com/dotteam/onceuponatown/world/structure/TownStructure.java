package com.dotteam.onceuponatown.world.structure;

import com.dotteam.onceuponatown.culture.BuildingType;
import com.dotteam.onceuponatown.culture.Culture;
import com.dotteam.onceuponatown.culture.CultureManager;
import com.dotteam.onceuponatown.registry.OuatStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class TownStructure extends Structure {
    public final String cultureID;
    public static final Codec<TownStructure> CODEC = RecordCodecBuilder.create((p) -> p.group(settingsCodec(p), Codec.STRING.fieldOf("culture").forGetter((p2) -> p2.cultureID)).apply(p, TownStructure::new));

    public TownStructure(StructureSettings settings, String cultureID) {
        super(settings);
        this.cultureID = cultureID;
    }

    protected @NotNull Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, (builder) -> this.generatePieces(builder, context));
    }

    private void generatePieces(StructurePiecesBuilder builder, GenerationContext context) {
        Culture culture = CultureManager.getCultureById(this.cultureID);
        if (culture == null) return;
        List<BuildingType> starterPack = culture.getRandomStarterPack();
        int townHeight = context.chunkGenerator().getFirstOccupiedHeight(context.chunkPos().getMinBlockX(), context.chunkPos().getMinBlockZ(), Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        BlockPos townCenterPos = new BlockPos(context.chunkPos().getMinBlockX(), townHeight, context.chunkPos().getMinBlockZ());
        TownGenerator.generatePiecesWorldGen(context.structureTemplateManager(), townCenterPos, starterPack, builder, context.random());
    }

    public StructureType<?> type() {
        return OuatStructures.TOWN_STRUCTURE.get();
    }
}
