package org.dawnoftime.onceuponatown.registry;

import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import org.dawnoftime.onceuponatown.world.structure.pieces.BuildingPiece;
import org.dawnoftime.onceuponatown.world.structure.pieces.PathPiece;
import org.dawnoftime.onceuponatown.world.structure.pieces.TownDataBuildingPiece;

import java.util.function.Supplier;

public abstract class OuatStructurePiecesRegistry {
    public static OuatStructurePiecesRegistry STRUCTURE_PIECE_REGISTRY;

    public final Supplier<StructurePieceType> BUILDING_PIECE = register("building_piece", () -> (StructurePieceType.StructureTemplateType) BuildingPiece::new);
    public final Supplier<StructurePieceType> TOWN_DATA_BUILDING_PIECE = register("town_data_building_piece", () -> (StructurePieceType.StructureTemplateType) TownDataBuildingPiece::new);
    public final Supplier<StructurePieceType> PATH_PIECE = register("path_piece", () -> (StructurePieceType.ContextlessType) PathPiece::new);

    public abstract Supplier<StructurePieceType> register(final String name, final Supplier<StructurePieceType> itemSupplier);
}

