package org.dawnoftime.onceuponatown.registry;

import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import org.dawnoftime.onceuponatown.structure.pieces.BuildingPiece;
import org.dawnoftime.onceuponatown.structure.pieces.SliceBuildPiece;

import java.util.function.Supplier;

public abstract class StructurePieceRegistry {
    public static StructurePieceRegistry REGISTRY;

    public final Supplier<StructurePieceType> BUILDING_PIECE = register("building_piece",
            () -> (StructurePieceType.ContextlessType) BuildingPiece::new);

    public final Supplier<StructurePieceType> SLICE_BUILD_PIECE = register("slice_build_piece",
            () -> (StructurePieceType.ContextlessType) SliceBuildPiece::new);

    public abstract Supplier<StructurePieceType> register(final String name, final Supplier<StructurePieceType> itemSupplier);
}

