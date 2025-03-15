package org.dawnoftime.onceuponatown.culture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Utility for reading a data pack file of a culture.
 */
public class CultureFileHelper {
    private final String cultureId;
    private final String fileName;
    private final ResourceLocation fileRl;
    private final String objectName;

    public CultureFileHelper(String cultureId, String fileName, ResourceLocation fileRl, String objectName) {
        this.cultureId = cultureId;
        this.fileName = fileName;
        this.fileRl = fileRl;
        this.objectName = objectName;
    }

    public JsonObject getJsonObject(JsonObject jsonObject, String field) throws CorruptedCultureException {
        return getJsonObject(jsonObject, field, "");
    }

    public JsonObject getJsonObject(JsonObject jsonObject, String field, String fieldLocation) throws CorruptedCultureException {
        if (!jsonObject.has(field)) {
            throwMissingField(field, fieldLocation, "JsonObject");
        }
        JsonElement element = jsonObject.get(field);
        if (!element.isJsonObject()) {
            throwWrongFieldType(field, fieldLocation, "JsonObject", GsonHelper.getType(element));
        }
        return element.getAsJsonObject();
    }

    public JsonObject asJsonObject(JsonElement element, String elementName) throws CorruptedCultureException {
        return asJsonObject(element, elementName, "");
    }

    public JsonObject asJsonObject(JsonElement element, String elementName, String elementLocation) throws CorruptedCultureException {
        if (!element.isJsonObject()) {
            throwWrongFieldType(elementName, elementLocation, "JsonObject", GsonHelper.getType(element));
        }
        return element.getAsJsonObject();
    }

    public JsonArray getJsonArray(JsonObject jsonObject, String field) throws CorruptedCultureException {
        return getJsonArray(jsonObject, field, "");
    }

    public JsonArray getJsonArray(JsonObject jsonObject, String field, String fieldLocation) throws CorruptedCultureException {
        if (!jsonObject.has(field)) {
            throwMissingField(field, fieldLocation, "JsonArray");
        }
        JsonElement element = jsonObject.get(field);
        if (!element.isJsonArray()) {
            throwWrongFieldType(field, fieldLocation, "JsonArray", GsonHelper.getType(element));
        }
        return element.getAsJsonArray();
    }

    public JsonArray asJsonArray(JsonElement element, String elementName) throws CorruptedCultureException {
        return asJsonArray(element, elementName, "");
    }

    public JsonArray asJsonArray(JsonElement element, String elementName, String elementLocation) throws CorruptedCultureException {
        if (!element.isJsonArray()) {
            throwWrongFieldType(elementName, elementLocation, "JsonArray", GsonHelper.getType(element));
        }
        return element.getAsJsonArray();
    }

    public String getString(JsonObject jsonObject, String field) throws CorruptedCultureException {
        return getString(jsonObject, field, "");
    }

    public String getString(JsonObject jsonObject, String field, String fieldLocation) throws CorruptedCultureException {
        if (!jsonObject.has(field)) {
            throwMissingField(field, fieldLocation, "string");
        }
        JsonElement element = jsonObject.get(field);
        if (!element.isJsonPrimitive()) {
            throwWrongFieldType(field, fieldLocation, "string", GsonHelper.getType(element));
        }
        return element.getAsString();
    }

    public int getInt(JsonObject jsonObject, String field) throws CorruptedCultureException {
        return getInt(jsonObject, field, "");
    }

    public int getInt(JsonObject jsonObject, String field, String fieldLocation) throws CorruptedCultureException {
        if (!jsonObject.has(field)) {
            throwMissingField(field, fieldLocation, "integer");
        }
        JsonElement element = jsonObject.get(field);
        if (!(element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber())) {
            throwWrongFieldType(field, fieldLocation, "integer", GsonHelper.getType(element));
        }
        return element.getAsInt();
    }

    public int getPositiveInt(JsonObject jsonObject, String field) throws CorruptedCultureException {
        return getPositiveInt(jsonObject, field, "");
    }

    public int getPositiveInt(JsonObject jsonObject, String field, String fieldLocation) throws CorruptedCultureException {
        int i = getInt(jsonObject, field, fieldLocation);
        if (i < 0) {
            throwInvalidField(field, fieldLocation, "It must be >= 0.");
        }
        return i;
    }

    public float getFloat(JsonObject jsonObject, String field) throws CorruptedCultureException {
        return getFloat(jsonObject, field, "");
    }

    public float getFloat(JsonObject jsonObject, String field, String fieldLocation) throws CorruptedCultureException {
        if (!jsonObject.has(field)) {
            throwMissingField(field, fieldLocation, "float");
        }
        JsonElement element = jsonObject.get(field);
        if (!(element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber())) {
            throwWrongFieldType(field, fieldLocation, "float", GsonHelper.getType(element));
        }
        return element.getAsFloat();
    }

    public boolean getBoolean(JsonObject jsonObject, String field) throws CorruptedCultureException {
        return getBoolean(jsonObject, field, "");
    }

    public boolean getBoolean(JsonObject jsonObject, String field, String fieldLocation) throws CorruptedCultureException {
        if (!jsonObject.has(field)) {
            throwMissingField(field, fieldLocation, "boolean");
        }
        JsonElement element = jsonObject.get(field);
        if (!element.isJsonPrimitive()) {
            throwWrongFieldType(field, fieldLocation, "boolean", GsonHelper.getType(element));
        }
        return element.getAsBoolean();
    }

    public Item getItem(JsonObject jsonObject, String field) throws CorruptedCultureException {
        return getItem(jsonObject, field, "");
    }

    public Item getItem(JsonObject jsonObject, String field, String fieldLocation) throws CorruptedCultureException {
        if (!jsonObject.has(field)) {
            throwMissingField(field, fieldLocation, "item id");
        }
        JsonElement element = jsonObject.get(field);
        if (!element.isJsonPrimitive()) {
            throwWrongFieldType(field, fieldLocation, "item id", GsonHelper.getType(element));
        }
        // TODO make it work with modded items
        String itemId = element.getAsString();
        // Returning default oak planks item if the id founded does not exist.
        // Ouat.COMMON.getItem(new ResourceLocation(itemId));
        return BuiltInRegistries.ITEM.getOptional(new ResourceLocation(itemId)).orElse(Items.OAK_PLANKS);
    }

    public void throwGeneric(String message) throws CorruptedCultureException {
        throw CorruptedCultureException.invalidFile(cultureId, fileName, fileRl, objectName, message);
    }

    public void throwInvalidField(String field, String message) throws CorruptedCultureException {
        throwInvalidField(field, "", message);
    }

    public void throwInvalidField(String field, String fieldLocation, String message) throws CorruptedCultureException {
        throw CorruptedCultureException.invalidField(cultureId, fileName, fileRl, objectName, field, fieldLocation, message);
    }

    private void throwMissingField(String field, String fieldLocation, String fieldType) throws CorruptedCultureException {
        throw CorruptedCultureException.missingField(cultureId, fileName, fileRl, objectName, field, fieldLocation, fieldType);
    }

    private void throwWrongFieldType(String field, String fieldLocation, String expectedFieldType, String foundFieldType) throws CorruptedCultureException {
        throw CorruptedCultureException.wrongFieldType(cultureId, fileName, fileRl, objectName, field, fieldLocation, expectedFieldType, foundFieldType);
    }
}
