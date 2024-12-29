package org.dawnoftime.onceuponatown.culture;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;

public class CorruptedCultureException extends RuntimeException {

    public CorruptedCultureException(String cultureId, String message) {
        super("Culture [" + cultureId + "] is corrupted. " + message);
    }

    /**
     * An exception raised when an invalid culture loads
     * @param cultureId The culture detected as invalid
     * @param file The invalid file
     * @param field The element in the file that may be invalid
     */
    public CorruptedCultureException(String cultureId, String file, String field, String message) {
        this(cultureId, "Affected file : " + file + ". Affected field : " + field + "." + ((message == null) ? "" : " " + message));
    }

    public static CorruptedCultureException missingField(String cultureId, String objectClassName, String fileName, String missingField, String missingFieldDescription, ResourceLocation rl) {
        return new CorruptedCultureException(cultureId, "Failed to register a %s. '%s' is missing the field '%s'%s. Check at this location : %s".formatted(objectClassName, fileName, missingField, missingFieldDescription, rl.getPath()));
    }

    public static CorruptedCultureException missingFile(String cultureId, String objectClassName, String fileName, ResourceLocation rl) {
        return new CorruptedCultureException(cultureId, "%s file '%s' is missing. It should be located here : %s".formatted(objectClassName, fileName, rl.getPath()));
    }
}
