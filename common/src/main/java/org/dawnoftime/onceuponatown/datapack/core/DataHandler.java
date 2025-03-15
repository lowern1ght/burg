package org.dawnoftime.onceuponatown.datapack.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

public abstract class DataHandler {
    public static final String CULTURES_FOLDER_NAME = "ouat_cultures";
    public static final String CULTURE_JSON_FILE_NAME = "ouat_culture.json";
    public static final String BUILDINGS_FOLDER_NAME = "buildings";
    public static final String ROAD_TYPE_NAME = "road";
    public static final String WIDE_ROAD_TYPE_NAME = "wide_road";
    public static final String BRIDGE_TYPE_NAME = "bridge";
    public static final String WALL_TYPE_NAME = "wall";

    public DataHandler(@NotNull JsonObject rootJson){}

    public abstract JsonObject toJson(@NotNull JsonObject rootJson);

    public abstract @NotNull ArrayList<String> getErrors();

    public boolean isValid(){
        return this.getErrors().isEmpty();
    }

    protected String missingOrIncorrect(String field) {
        return "Missing or incorrect field '" + field + "'.";
    }


    protected @Nullable JsonObject getJsonObject(JsonObject container, String property) {
        return container.has(property) && container.get(property).isJsonObject() ? container.get(property).getAsJsonObject() : null;
    }

    protected @NotNull List<JsonObject> getJsonArrayObjects(JsonObject container, String property) {
        JsonArray array = this.getJsonArray(container, property);
        if (array != null){
            return StreamSupport.stream(array.spliterator(), false)
                    .filter(JsonElement::isJsonObject)
                    .map(JsonElement::getAsJsonObject)
                    .toList();
        }
        return new ArrayList<>();
    }

    protected @Nullable JsonArray getJsonArray(JsonObject container, String property) {
        return container.has(property) && container.get(property).isJsonArray() ? container.get(property).getAsJsonArray() : null;
    }

    protected @Nullable String getString(JsonObject container, String property) {
        return container.has(property) && container.get(property).isJsonPrimitive() ? container.get(property).getAsString() : null;
    }

    protected @Nullable Integer getInt(JsonObject container, String property) {
        if (container.has(property)) {
            JsonElement element = container.get(property);
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                return element.getAsInt();
            }
        }
        return null;
    }

    protected @Nullable Float getFloat(JsonObject container, String property) {
        if (container.has(property)) {
            JsonElement element = container.get(property);
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                return element.getAsFloat();
            }
        }
        return null;
    }

    protected @Nullable Boolean getBoolean(JsonObject container, String property) {
        if (container.has(property)) {
            JsonElement element = container.get(property);
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
                return element.getAsBoolean();
            }
        }
        return null;
    }

    protected @Nullable Item getItem(JsonObject container, String property) {
        if (container.has(property)) {
            JsonElement element = container.get(property);
            if (element.isJsonPrimitive()) {
                String itemId = element.getAsString();
                return BuiltInRegistries.ITEM.getOptional(new ResourceLocation(itemId)).orElse(Items.OAK_PLANKS);
            }
        }
        return null;
    }
}
