package org.dawnoftime.onceuponatown.datapack.core;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class IntegerDataHandler extends DataHandler {
    private final @NotNull String key;
    private @Nullable Integer value;
    private final int min;
    private final int max;

    public IntegerDataHandler(@NotNull JsonObject rootJson, @NotNull String key, int min, int max) {
        this.key = key;
        this.value = this.getInt(rootJson, key);
        this.min = min;
        this.max = max;
    }

    public IntegerDataHandler(@NotNull JsonObject rootJson, @NotNull String key, int min, int max, int fallback) {
        this(rootJson, key, min, max);
        if (value == null) {
            value = fallback;
        }
    }

    public void set(String value) {
        try {
            this.value = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            this.value = null;
        }
    }

    public @Nullable Integer get() {
        return this.isValid() ? value : null;
    }

    public @NotNull String asString() {
        return this.isValid() ? value.toString() : "";
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
        } else if (value < min) {
            errors.add("Field '" + key + "' must be >= " + min);
        } else if (value > max) {
            errors.add("Field '" + key + "' must be <= " + max);
        }
        return errors;
    }

    public boolean isGreaterThan(IntegerDataHandler value) {
        Integer otherInteger = value.get();
        if (this.value == null || otherInteger == null) {
            return false;
        } else {
            return this.value > otherInteger;
        }
    }
}
