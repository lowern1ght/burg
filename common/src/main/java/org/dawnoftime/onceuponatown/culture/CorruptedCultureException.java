package org.dawnoftime.onceuponatown.culture;

import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Utils;

public class CorruptedCultureException extends RuntimeException {
    public CorruptedCultureException(String cultureId, String message) {
        super("Culture [" + cultureId + "] is corrupted. " + message);
    }
}
