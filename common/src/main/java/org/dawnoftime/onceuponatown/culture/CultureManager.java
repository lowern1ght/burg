package org.dawnoftime.onceuponatown.culture;

import com.google.gson.JsonElement;
import org.dawnoftime.onceuponatown.Ouat;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class CultureManager implements PreparableReloadListener {
    public static final String CULTURE_FOLDER_NAME = "ouat_cultures";
    public static final String CULTURE_JSON_FILE_NAME = "ouat_culture.json";
    private static final Map<String, Culture> LOADED_CULTURES = new HashMap<>();

    public static void loadCultures(ResourceManager manager) {
        LOADED_CULTURES.clear();
        var detectedCultures = new HashMap<>(manager.listResources(CULTURE_FOLDER_NAME, (rl) -> rl.getPath().endsWith("/" + CULTURE_JSON_FILE_NAME)));
        Ouat.info(detectedCultures.size() + " detected cultures will be loaded");
        detectedCultures.forEach((rl, res) -> {
            String path = rl.getPath();
            String cultureId = path.substring((CULTURE_FOLDER_NAME + "/").length(), path.length() - ("/" + CULTURE_JSON_FILE_NAME).length());
            if (LOADED_CULTURES.containsKey(cultureId)) {
                Ouat.error("Culture [%s]: Failed to register the culture. Another culture was already registered with the same id.".formatted(cultureId));
            } else {
                Culture culture = Culture.createCulture(cultureId, rl, res, manager);
                if (culture != null) {
                    LOADED_CULTURES.put(cultureId, culture);
                } else {
                    Ouat.error("Culture [%s]: Failed to register the culture.".formatted(cultureId));
                }
            }
        });
        StringJoiner joiner = new StringJoiner(", ");
        LOADED_CULTURES.keySet().forEach(joiner::add);
        Ouat.info("%s cultures loaded : %s".formatted(LOADED_CULTURES.size(), joiner));
    }

    public static List<Culture> getLoadedCultures() {
        return LOADED_CULTURES.values().stream().toList();
    }

    public static Culture getCultureById(String cultureId) throws CorruptedCultureException {
        Culture culture = LOADED_CULTURES.get(cultureId);
        if (culture == null) {
            throw new CorruptedCultureException(cultureId, "This culture could not be found in the list of loaded cultures.");
        }
        return culture;
    }

    public @NotNull CompletableFuture<Void> reload(PreparationBarrier stage, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller preparationsProfiler, @NotNull ProfilerFiller reloadProfiler, @NotNull Executor backgroundExecutor, @NotNull Executor gameExecutor) {
        return CompletableFuture.allOf(CompletableFuture.runAsync(() -> {
        }, backgroundExecutor)).thenCompose(stage::wait);
    }

    public static JsonElement tryGet(String cultureId, JsonObject objectJson, String objectClassName, String targetField, String targetFieldLocation, String fileName, ResourceLocation fileRL) throws CorruptedCultureException {
        JsonElement elem = objectJson.get(targetField);
        if (elem == null) {
            throw CorruptedCultureException.missingField(cultureId, objectClassName, fileName, targetField, " " + targetFieldLocation, fileRL);
        }
        return elem;
    }

    public static JsonElement tryGet(String cultureId, JsonObject objectJson, String objectClassName, String targetField, String fileName, ResourceLocation fileRL) throws CorruptedCultureException {
        return tryGet(cultureId, objectJson, objectClassName, targetField, "", fileName, fileRL);
    }

    public static class CultureJsonFile {
        String cultureId;
        JsonObject objectJson;
        String objectName;
        String fileName;
        ResourceLocation fileResourceLocation;

        public CultureJsonFile(String cultureId, String objectName, JsonObject objectJson, ResourceLocation fileResourceLocation, String fileName) {
            this.cultureId = cultureId;
            this.objectName = objectName;
            this.objectJson = objectJson;
            this.fileResourceLocation = fileResourceLocation;
            this.fileName = fileName;
        }

        public JsonElement tryGet(String targetField) {
            return CultureManager.tryGet(cultureId, objectJson, objectName, targetField, fileName, fileResourceLocation);
        }

        public JsonElement tryGet(JsonObject subObject, String targetField) {
            return CultureManager.tryGet(cultureId, subObject, objectName, targetField, fileName, fileResourceLocation);
        }

        public JsonElement tryGet(String targetField, String targetFieldLocation) {
            return CultureManager.tryGet(cultureId, objectJson, objectName, targetField, targetFieldLocation, fileName, fileResourceLocation);
        }

        public JsonElement tryGet(JsonObject subObject, String targetField, String targetFieldLocation) {
            return CultureManager.tryGet(cultureId, subObject, objectName, targetField, targetFieldLocation, fileName, fileResourceLocation);
        }
    }
}
