package org.dawnoftime.onceuponatown.town;

import org.dawnoftime.onceuponatown.Utils;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;

public class CorruptedTownException extends RuntimeException{

    public CorruptedTownException(ProtoTown town, String errorMessage){
        super("Town '%s' in %s: %s".formatted(town.getName(), Utils.blockPosToString(town.getCenter()), errorMessage));
    }
}
