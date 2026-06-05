package org.dawnoftime.onceuponatown.datapack.core;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class StringEnumDataHandler<T extends Enum<T>> extends DataHandler {
    private final @NotNull String key;
    private @Nullable String value;
    private final Class<T> enumClass;

    public StringEnumDataHandler(@NotNull JsonObject rootJson, @NotNull String key, Class<T> enumClass) {
        this.key = key;
        this.value = this.getString(rootJson, key);
        this.enumClass = enumClass;
    }

    public StringEnumDataHandler(@NotNull JsonObject rootJson, @NotNull String key, Class<T> enumClass, @NotNull String fallback) {
        this(rootJson, key, enumClass);
        if (value == null) {
            value = fallback;
        }
    }

    public void set(String value) {
        this.value = value;
    }

    public @Nullable String get(){
        return this.isValid() ? value : null;
    }

    public @Nullable T getEnum(){
        return this.isValid() ? Enum.valueOf(enumClass, value.toUpperCase()) : null;
    }

    public @NotNull String asString() {
        return this.isValid() ? value : "";
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
        } else {
            List<String> values = Stream.of(enumClass.getEnumConstants())
                    .map(Enum::name)
                    .toList();
            if (values.stream().noneMatch(name -> name.equalsIgnoreCase(value))) {
                errors.add("Field '" + key + "' contains '" + value + "', which is not an accepted value: [" + String.join(", ", values) + "].");
            }
        }
        return errors;
    }
}
