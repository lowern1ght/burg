package org.dawnoftime.onceuponatown.datapack.core;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class StringDataHandler extends DataHandler {
    private final @NotNull String key;
    private final @Nullable String value;

    public StringDataHandler(@NotNull JsonObject rootJson, @NotNull String key) {
        super(rootJson);
        this.key = key;
        this.value = this.getString(rootJson, key);
    }

    public @Nullable String get(){
        return this.isValid() ? value : null;
    }

    @Override
    public JsonObject toJson(@NotNull JsonObject rootJson) {
        rootJson.addProperty(key, this.get());
        return rootJson;
    }

    @Override
    public @NotNull ArrayList<String> getErrors() {
        ArrayList<String> errors = new ArrayList<>();
        if (value == null) {
            errors.add(missingOrIncorrect(key));
        } else if (value.isBlank()) {
            errors.add( "Field '" + key + "' is blank.");
        }
        return errors;
    }
}
