package org.dawnoftime.onceuponatown.registry;

import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import org.dawnoftime.onceuponatown.structure.pieces.BuildPiece;
import org.dawnoftime.onceuponatown.structure.pieces.SliceBuildPiece;
import org.dawnoftime.onceuponatown.structure.pieces.DataSliceBuildPiece;

import java.util.function.Supplier;

public abstract class StructurePieceRegistry {
    public static StructurePieceRegistry REGISTRY;

    public final Supplier<StructurePieceType> BUILDING_PIECE = register("building_piece",
            () -> (StructurePieceType.StructureTemplateType) BuildPiece::new);

    public final Supplier<StructurePieceType> DATA_SLICE_BUILD_PIECE = register("data_slice_build_piece",
            () -> (StructurePieceType.StructureTemplateType) DataSliceBuildPiece::new);

    public final Supplier<StructurePieceType> SLICE_BUILD_PIECE = register("slice_build_piece",
            () -> (StructurePieceType.ContextlessType) SliceBuildPiece::new);

    public abstract Supplier<StructurePieceType> register(final String name, final Supplier<StructurePieceType> itemSupplier);
}

