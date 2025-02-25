package org.dawnoftime.onceuponatown.culture;

import net.minecraft.server.packs.resources.ResourceManager;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.datapack.DataHandler;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;

public class ServerCultures {
    private static final Map<String, Culture> LOADED_CULTURES = new HashMap<>();
    private static final Set<String> CORRUPTED_CULTURES = new HashSet<>();

    public static void loadCultures(ResourceManager manager) {
        LOADED_CULTURES.clear(); // Just in case
        var detectedCultures = new HashMap<>(manager.listResources(DataHandler.CULTURE_FOLDER_NAME, (rl) -> rl.getPath().endsWith("/" + DataHandler.CULTURE_JSON_FILE_NAME)));
        int n = detectedCultures.size();
        if (n == 0) {
            Ouat.error("No cultures detected");
        } else {
            Ouat.info(detectedCultures.size() + " culture" + (n > 1 ? "s" : "") + " will be loaded");
        }
        detectedCultures.forEach((rl, res) -> {
            String path = rl.getPath();
            String detectedId = path.substring((DataHandler.CULTURE_FOLDER_NAME + "/").length(), path.length() - ("/" + DataHandler.CULTURE_JSON_FILE_NAME).length());
            if (LOADED_CULTURES.containsKey(detectedId)) {
                Ouat.error("Culture [%s]: Failed to register the culture. Another culture was already registered with the same id.".formatted(detectedId));
            } else {
                Culture culture = Culture.readCultureFromDataPack(detectedId, rl, res, manager);
                if (culture != null) {
                    LOADED_CULTURES.put(detectedId, culture);
                } else {
                    Ouat.error("Culture [%s]: Failed to load the culture".formatted(detectedId));
                    CORRUPTED_CULTURES.add(detectedId);
                }
            }
        });
        n = LOADED_CULTURES.size();
        if (n > 0) {
            StringJoiner joiner = new StringJoiner(", ");
            LOADED_CULTURES.keySet().forEach(joiner::add);
            Ouat.info(("%s culture" + (n > 1 ? "s" : "") + " loaded : %s").formatted(n, joiner));
        } else {
            Ouat.error("No culture loaded");
        }
    }

    public static @NotNull List<Culture> getLoadedCultures() {
        return LOADED_CULTURES.values().stream().toList();
    }

    public static @NotNull Set<String> getCorruptedCultures() {
        return CORRUPTED_CULTURES;
    }

    public static @NotNull Culture getCultureOrDefault(String cultureId) {
        Culture culture = LOADED_CULTURES.get(cultureId);
        return culture == null ? Culture.DEFAULT_CULTURE : culture;
    }

    public static @Nullable Culture getCultureOrNull(String cultureId) {
        return LOADED_CULTURES.get(cultureId);
    }
}
