package org.dawnoftime.onceuponatown.town;

import org.dawnoftime.onceuponatown.Utils;

public class CorruptedTownException extends RuntimeException {
    public CorruptedTownException(Town town, String errorMessage) {
        super("Corrupted town '%s' at %s: %s".formatted(town.getName(), Utils.blockPosToString(town.getCenter()), errorMessage));
    }
}
