package org.dawnoftime.onceuponatown.registry;

import com.mojang.serialization.Codec;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import org.dawnoftime.onceuponatown.structure.TownStructure;

import java.util.function.Supplier;

public abstract class OuatStructureTypesRegistry {
    public static OuatStructureTypesRegistry STRUCTURE_TYPE_REGISTRY;
    public final Supplier<StructureType<TownStructure>> TOWN_STRUCTURE = register("town", get(TownStructure.CODEC));

    public abstract <T extends Structure> Supplier<StructureType<T>> register(final String name, final Supplier<StructureType<T>> structureTypeSupplier);
    private <T extends Structure> Supplier<StructureType<T>> get(Codec<T> codec) {
        return () -> () -> codec;
    }
}