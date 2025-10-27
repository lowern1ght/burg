package org.dawnoftime.onceuponatown.datapack.core;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.dawnoftime.onceuponatown.Ouat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

public abstract class DataHandler {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String CULTURES_FOLDER_NAME = "ouat_cultures";
    public static final String CULTURE_JSON_FILE_NAME = "ouat_culture.json";
    public static final String BUILDINGS_FOLDER_NAME = "buildings";
    public static final String PROFESSIONS_FOLDER_NAME = "professions";
    public static final String ROADS_FOLDER_NAME = "roads";
    public static final String SCHEDULES_FOLDER_NAME = "schedules";
    public static final String ROAD_TYPE_NAME = "road";
    public static final String WIDE_ROAD_TYPE_NAME = "wide_road";
    public static final String BRIDGE_TYPE_NAME = "bridge";
    public static final String WALL_TYPE_NAME = "wall";

    public abstract JsonObject toJson(@NotNull JsonObject rootJson);

    public abstract @NotNull ArrayList<String> getErrors();

    public boolean isValid(){
        return this.getErrors().isEmpty();
    }

    protected String missingOrIncorrect(String field) {
        return "Missing or incorrect field '" + field + "'.";
    }

    protected @NotNull JsonObject getJsonObject(JsonObject container, String property) {
        return container.has(property) && container.get(property).isJsonObject() ? container.get(property).getAsJsonObject() : new JsonObject();
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

    public void saveJson(Path jsonPath, ServerPlayer player, String cultureId) {
        JsonObject json = this.toJson(new JsonObject());
        try {
            Files.createDirectories(jsonPath.getParent());
            Files.writeString(jsonPath, GSON.toJson(json), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            Ouat.clientChat(player, "cc", "culture_error", cultureId);
            Ouat.debug("An error occurred while saving a file of '" + cultureId + "' : " + e);
        }
    }

    public static @NotNull JsonObject loadJson(Path jsonPath) {
        try {
            String jsonContent = Files.readString(jsonPath);
            return JsonParser.parseString(jsonContent).getAsJsonObject();
        } catch (Exception ignored) {
            // An error occurred, we will create a new building.json file.
            return new JsonObject();
        }
    }
}
