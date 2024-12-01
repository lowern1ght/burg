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
    public static final String CULTURE_FILE = "culture.json";
    private static final Map<String, Culture> LOADED_CULTURES = new HashMap<>();

    public static List<Culture> getLoadedCultures() {
        return LOADED_CULTURES.values().stream().toList();
    }

    public static Culture getCultureById(String cultureId) throws CorruptedCultureException {
        Culture culture = LOADED_CULTURES.get(cultureId);
        if(culture == null){
            throw new CorruptedCultureException(cultureId, "This culture could not be found in the list of loaded cultures.");
        }
        return culture;
    }

    public static void loadCultures(ResourceManager manager) {
        LOADED_CULTURES.clear();
        var detectedCultures = new HashMap<>(manager.listResources("cultures", (rl) -> rl.getPath().endsWith("/" + CULTURE_FILE)));
        Ouat.info(detectedCultures.size() + " detected cultures will be loaded");
        detectedCultures.forEach((rl, res) -> {
            String path = rl.getPath();
            String cultureId = path.substring("cultures/".length(), path.length() - ("/" + CULTURE_FILE).length());
            Culture culture = Culture.createCulture(cultureId, rl, res, manager);
            if(culture != null){
                if (LOADED_CULTURES.containsKey(culture.getId())) { // Error : id duplicate
                    Ouat.error("Culture [%s]: Failed to register the culture. Another culture was already registered with the same id.".formatted(cultureId));
                }else{
                    LOADED_CULTURES.put(cultureId, culture);
                }
            }
        });
        StringJoiner joiner = new StringJoiner(", ");
        LOADED_CULTURES.keySet().forEach(joiner::add);
        Ouat.info("%s cultures loaded : %s".formatted(LOADED_CULTURES.size(), joiner));
    }

    public @NotNull CompletableFuture<Void> reload(PreparationBarrier stage, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller preparationsProfiler, @NotNull ProfilerFiller reloadProfiler, @NotNull Executor backgroundExecutor, @NotNull Executor gameExecutor) {
        return CompletableFuture.allOf(CompletableFuture.runAsync(() -> {
        }, backgroundExecutor)).thenCompose(stage::wait);
    }

    public static JsonElement tryGet(JsonObject element, String field, String fieldLocation, String objectLoadedName, String cultureName, String fileName, ResourceLocation fileRL) throws CorruptedCultureException{
        JsonElement elem = element.get(field);
        if(elem == null){
            throw CorruptedCultureException.missingField(cultureName, objectLoadedName, fileName, field, fieldLocation, fileRL);
        }
        return elem;
    }

    public static JsonElement tryGet(JsonObject element, String field, String objectLoadedName, String cultureName, String fileName, ResourceLocation fileRL) throws CorruptedCultureException{
        return tryGet(element, field, "", objectLoadedName, cultureName, fileName, fileRL);
    }
}
