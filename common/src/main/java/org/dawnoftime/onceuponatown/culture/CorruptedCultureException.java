package org.dawnoftime.onceuponatown.culture;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;

public class CorruptedCultureException extends RuntimeException{

    public CorruptedCultureException(String cultureName, String errorMessage){
        super("Culture [" + cultureName + "]: " + errorMessage);
    }

    /**
     * An exception raised when an invalid culture loads
     * @param culture The culture detected as invalid
     * @param file The invalid file
     * @param element The element in the file that may be invalid
     */
    public CorruptedCultureException(String cultureName, String file, String element, String message) {
        this(cultureName, "This culture is corrupted. Affected file : " + file + ". Affected element in file : " + element + "." + ((message == null) ? "" : " " + message));
    }

    public static CorruptedCultureException missingField(String cultureName, String objectClassName, String fileName, String missingField, String missingFieldDescription, ResourceLocation rl){
        return new CorruptedCultureException(cultureName, "Failed to register a %s. '%s' is missing the field '%s'%s. Please check the file: %s".formatted(objectClassName, fileName, missingField, missingFieldDescription, rl.getPath()));
    }

    public static CorruptedCultureException missingFile(String cultureName, String objectClassName, String fileName, ResourceLocation rl){
        return new CorruptedCultureException(cultureName, "Impossible to find the %s file '%s'. Please check at this location: %s".formatted(objectClassName, fileName, rl.getPath()));
    }
}
