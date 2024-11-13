package org.dawnoftime.onceuponatown.culture;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;

public class CorruptedCultureException extends RuntimeException{

    public CorruptedCultureException(String errorMessage){
        super(errorMessage);
    }

    /**
     * An exception raised when an invalid culture loads
     * @param culture The culture detected as invalid
     * @param file The invalid file
     * @param element The element in the file that may be invalid
     */
    public CorruptedCultureException(String culture, String file, String element, String message) {
        this("Once upon a town MOD : " + culture + " is corrupted. Affected file : " + file + ". Affected element in file : " + element + "." + ((message == null) ? "" : " " + message));
    }

    public CorruptedCultureException(String culture, String file, String message) {
        this("Once upon a town MOD : " + culture + " is corrupted. Affected file : " + file + "." + ((message == null) ? "" : " " + message));
    }

    public CorruptedCultureException(String culture, String message) {
        this("Once upon a town MOD : " + culture + " is corrupted." + ((message == null) ? "" : " " + message));
    }

    public static CorruptedCultureException missingField(String cultureName, String objectClassName, String fileName, String missingField, String missingFieldDescription, ResourceLocation rl){
        return new CorruptedCultureException("Culture [%s]: Failed to register a %s. '%s' is missing the field '%s'%s. Please check the file: %s".formatted(cultureName, objectClassName, fileName, missingField, missingFieldDescription, rl.getPath()));
    }

    public static CorruptedCultureException missingFile(String cultureName, String objectClassName, String fileName, ResourceLocation rl){
        return new CorruptedCultureException("Culture [%s]: Impossible to find the %s file '%s'. Please check at this location: %s".formatted(cultureName, objectClassName, fileName, rl.getPath()));
    }
}
