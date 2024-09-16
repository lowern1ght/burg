package org.dawnoftime.onceuponatown.registry;

import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.world.structure.TownStructure;
import org.dawnoftime.onceuponatown.world.structure.pieces.BuildingPiece;
import org.dawnoftime.onceuponatown.world.structure.pieces.PathPiece;
import org.dawnoftime.onceuponatown.world.structure.pieces.TownDataBuildingPiece;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class OuatStructures {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Constants.MOD_ID);
    public static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(Registries.STRUCTURE_PIECE, Constants.MOD_ID);

    public static final RegistryObject<StructureType<TownStructure>> TOWN_STRUCTURE = STRUCTURE_TYPES.register("town", () -> get(TownStructure.CODEC));
    public static final RegistryObject<StructurePieceType> BUILDING_PIECE = STRUCTURE_PIECES.register("building_piece", () -> (StructurePieceType.StructureTemplateType) BuildingPiece::new);
    public static final RegistryObject<StructurePieceType> TOWN_DATA_BUILDING_PIECE = STRUCTURE_PIECES.register("town_data_building_piece", () -> (StructurePieceType.StructureTemplateType) TownDataBuildingPiece::new);
    public static final RegistryObject<StructurePieceType> PATH_PIECE = STRUCTURE_PIECES.register("path_piece", () -> (StructurePieceType.ContextlessType) PathPiece::new);


    private static <T extends Structure> StructureType<T> get(Codec<T> codec) {
        return () -> codec;
    }

    public static void register(IEventBus bus) {
        STRUCTURE_TYPES.register(bus);
        STRUCTURE_PIECES.register(bus);
    }
}

