package org.dawnoftime.onceuponatown.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.dawnoftime.onceuponatown.datapack.core.DataHandler;
import org.dawnoftime.onceuponatown.datapack.core.IntegerDataHandler;
import org.dawnoftime.onceuponatown.datapack.core.StringDataHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.function.Supplier;

public class RoadDataHandler extends DataHandler {
    public final StringDataHandler id;
    public final ArrayList<RoadLevelsHandler> levels = new ArrayList<>();
    public final ArrayList<RoadVariantHandler> variants = new ArrayList<>();

    public RoadDataHandler(@NotNull JsonObject rootJson) {
        id = new StringDataHandler(rootJson, "id");
        this.getJsonArrayObjects(rootJson, "levels")
                .forEach(obj -> levels.add(new RoadLevelsHandler(obj)));
        this.getJsonArrayObjects(rootJson, "variants")
                .forEach(obj -> variants.add(new RoadVariantHandler(obj)));
    }

    @Override
    public JsonObject toJson(@NotNull JsonObject rootJson) {
        id.toJson(rootJson);
        JsonArray levelsJson = new JsonArray();
        levels.forEach(level -> levelsJson.add(level.toJson(new JsonObject())));
        rootJson.add("levels", levelsJson);
        JsonArray variantsJson = new JsonArray();
        variants.forEach(variant -> variantsJson.add(variant.toJson(new JsonObject())));
        rootJson.add("variants", variantsJson);
        return rootJson;
    }

    @Override
    public @NotNull ArrayList<String> getErrors() {
        ArrayList<String> errors = new ArrayList<>();
        errors.addAll(id.getErrors());
        levels.forEach(spec -> errors.addAll(spec.getErrors()));
        variants.forEach(era -> errors.addAll(era.getErrors()));
        return errors;
    }

    public void resizeLevelLists(int numberOfLevel) {
        resizeList(levels, numberOfLevel, () -> new RoadLevelsHandler(new JsonObject()));
        for (RoadVariantHandler variant : variants) {
            resizeList(variant.levels, numberOfLevel, () -> new RoadVariantLevelHandler(new JsonObject()));
        }
    }

    private static <T> void resizeList(ArrayList<T> list, int targetSize, Supplier<T> supplier) {
        int currentSize = list.size();
        if (currentSize > targetSize) {
            list.subList(targetSize, currentSize).clear();
        } else {
            for (int i = currentSize; i < targetSize; i++) {
                list.add(supplier.get());
            }
        }
    }

    public static class RoadLevelsHandler extends DataHandler {
        public final IntegerDataHandler requiredEra;

        public RoadLevelsHandler(@NotNull JsonObject rootJson) {
            this.requiredEra = new IntegerDataHandler(rootJson, "required_era", 0, 100, 0);
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            requiredEra.toJson(rootJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            return new ArrayList<>(requiredEra.getErrors());
        }
    }

    public static class RoadVariantHandler extends DataHandler {
        public final StringDataHandler name;
        public final StringDataHandler shape;
        public final IntegerDataHandler sizeX;
        public final IntegerDataHandler sizeY;
        public final IntegerDataHandler sizeZ;
        public final ArrayList<RoadVariantLevelHandler> levels = new ArrayList<>();

        public RoadVariantHandler(@NotNull JsonObject rootJson) {
            this.name = new StringDataHandler(rootJson, "name");
            this.shape = new StringDataHandler(rootJson, "shape");
            JsonObject sizeJson = this.getJsonObject(rootJson, "size");
            this.sizeX = new IntegerDataHandler(sizeJson, "x", 1, 1000);
            this.sizeY = new IntegerDataHandler(sizeJson, "y", 1, 1000);
            this.sizeZ = new IntegerDataHandler(sizeJson, "z", 1, 1000);
            this.getJsonArrayObjects(rootJson, "levels")
                    .forEach(level -> levels.add(new RoadVariantLevelHandler(level)));
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            name.toJson(rootJson);
            shape.toJson(rootJson);
            JsonObject sizeJson = new JsonObject();
            sizeX.toJson(sizeJson);
            sizeY.toJson(sizeJson);
            sizeZ.toJson(sizeJson);
            rootJson.add("size", sizeJson);
            JsonArray levelsJson = new JsonArray();
            levels.forEach(level -> levelsJson.add(level.toJson(new JsonObject())));
            rootJson.add("levels", levelsJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            ArrayList<String> errors = name.getErrors();
            errors.addAll(shape.getErrors());
            errors.addAll(sizeX.getErrors());
            errors.addAll(sizeY.getErrors());
            errors.addAll(sizeZ.getErrors());
            levels.forEach(level -> errors.addAll(level.getErrors()));
            return errors;
        }
    }

    public static class RoadVariantLevelHandler extends DataHandler {
        public final StringDataHandler schematic;
        public final ArrayList<WaypointHandler> waypoints = new ArrayList<>();

        public RoadVariantLevelHandler(@NotNull JsonObject rootJson) {
            this.schematic = new StringDataHandler(rootJson, "schematic");
            this.getJsonArrayObjects(rootJson, "waypoints")
                    .forEach(waypoint -> waypoints.add(new WaypointHandler(waypoint)));
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            schematic.toJson(rootJson);
            JsonArray wpJson = new JsonArray();
            waypoints.forEach(wp -> wpJson.add(wp.toJson(new JsonObject())));
            rootJson.add("waypoints", wpJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            ArrayList<String> errors = schematic.getErrors();
            waypoints.forEach(wp -> errors.addAll(wp.getErrors()));
            return errors;
        }
    }

    public static class WaypointHandler extends DataHandler {
        public final StringDataHandler id;
        public final IntegerDataHandler x;
        public final IntegerDataHandler y;
        public final IntegerDataHandler z;

        public WaypointHandler(@NotNull JsonObject rootJson) {
            this.id = new StringDataHandler(rootJson, "id");
            this.x = new IntegerDataHandler(rootJson, "x", 1, 1000);
            this.y = new IntegerDataHandler(rootJson, "y", 1, 1000);
            this.z = new IntegerDataHandler(rootJson, "z", 1, 1000);
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            id.toJson(rootJson);
            x.toJson(rootJson);
            y.toJson(rootJson);
            z.toJson(rootJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            ArrayList<String> errors = id.getErrors();
            errors.addAll(x.getErrors());
            errors.addAll(y.getErrors());
            errors.addAll(z.getErrors());
            return errors;
        }
    }
}
