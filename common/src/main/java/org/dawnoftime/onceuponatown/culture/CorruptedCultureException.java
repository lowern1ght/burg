package org.dawnoftime.onceuponatown.culture;

import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Utils;

public class CorruptedCultureException extends RuntimeException {
    public CorruptedCultureException(String cultureId, String message) {
        super("Culture [" + cultureId + "] is corrupted. " + message);
    }

    public static CorruptedCultureException missingFile(String cultureId, String fileName, ResourceLocation fileRl, String objectName) {
        return new CorruptedCultureException(cultureId, "Missing " + objectName + " file " + fileName + ". Please verify, it should be located at " + Utils.serverRlToDebug(fileRl));
    }

    public static CorruptedCultureException invalidFile(String cultureId, String fileName, ResourceLocation fileRl, String objectName, String message) {
        return new CorruptedCultureException(cultureId, "There is an error in " + objectName + " file " + fileName + ". " + message + ". Please check the file at " + Utils.serverRlToDebug(fileRl));
    }

    public static CorruptedCultureException missingField(String cultureId, String fileName, ResourceLocation fileRl, String objectName, String field, String fieldLocation, String fieldType) {
        return new CorruptedCultureException(cultureId, "There is an error in " + objectName + " file " + fileName + (fieldLocation.isEmpty() ? "." : " " + fieldLocation + ".") + " Missing " + fieldType + " property '" + field + "'. Please check the file at " + Utils.serverRlToDebug(fileRl));
    }

    public static CorruptedCultureException wrongFieldType(String cultureId, String fileName, ResourceLocation fileRl, String objectName, String field, String fieldLocation, String expectedFieldType, String foundFieldType) {
        return new CorruptedCultureException(cultureId, "There is an error in " + objectName + " file " + fileName + (fieldLocation.isEmpty() ? "." : " " + fieldLocation + ".") + " Property '" + field + "' should be a " + expectedFieldType + ", not a " + foundFieldType + ". Please check the file at " + Utils.serverRlToDebug(fileRl));
    }

    public static CorruptedCultureException invalidField(String cultureId, String fileName, ResourceLocation fileRl, String objectName, String field, String fieldLocation, String message) {
        return new CorruptedCultureException(cultureId, "There is an error in " + objectName + " file " + fileName + (fieldLocation.isEmpty() ? "." : " " + fieldLocation + ".") + " Invalid property '" + field + "'. " + message + ". Please check the file at " + Utils.serverRlToDebug(fileRl));
    }
}
