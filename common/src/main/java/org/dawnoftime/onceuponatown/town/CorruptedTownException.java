package org.dawnoftime.onceuponatown.town;

import net.minecraft.core.BlockPos;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;

public class CorruptedTownException extends RuntimeException{

    public CorruptedTownException(ProtoTown town, String errorMessage){
        super("Town '%s' in %s: %s".formatted(town.getName(), str(town.getCenter()), errorMessage));
    }

    public static String str(BlockPos pos){
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }
}
