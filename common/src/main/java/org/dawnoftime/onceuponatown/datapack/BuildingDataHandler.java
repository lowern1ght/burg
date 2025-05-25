package org.dawnoftime.onceuponatown.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.dawnoftime.onceuponatown.datapack.core.DataHandler;
import org.dawnoftime.onceuponatown.datapack.core.IntegerDataHandler;
import org.dawnoftime.onceuponatown.datapack.core.StringDataHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class BuildingDataHandler extends DataHandler {
    public final StringDataHandler id;
    public final IntegerDataHandler weight;
    public final StringDataHandler item;
    public final ArrayList<BuildingLevelsHandler> levels = new ArrayList<>();
    public final ArrayList<BuildingVariantHandler> variants = new ArrayList<>();

    public BuildingDataHandler(@NotNull JsonObject rootJson) {
        super(rootJson);
        id = new StringDataHandler(rootJson, "id");
        weight = new IntegerDataHandler(rootJson, "weight", 0, 100000);
        item = new StringDataHandler(rootJson, "item");
        this.getJsonArrayObjects(rootJson, "levels")
                .forEach(obj -> levels.add(new BuildingLevelsHandler(obj)));
        this.getJsonArrayObjects(rootJson, "variants")
                .forEach(obj -> variants.add(new BuildingVariantHandler(obj)));
    }

    @Override
    public JsonObject toJson(@NotNull JsonObject rootJson) {
        id.toJson(rootJson);
        weight.toJson(rootJson);
        item.toJson(rootJson);
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
        errors.addAll(weight.getErrors());
        errors.addAll(item.getErrors());
        levels.forEach(spec -> errors.addAll(spec.getErrors()));
        variants.forEach(era -> errors.addAll(era.getErrors()));
        return errors;
    }

    public static class BuildingLevelsHandler extends DataHandler {
        public final IntegerDataHandler requiredEra;
        public final IntegerDataHandler dwellingSlots;
        public final ArrayList<WorkingSlotHandler> workingSlots = new ArrayList<>();

        public BuildingLevelsHandler(@NotNull JsonObject rootJson) {
            super(rootJson);
            this.requiredEra = new IntegerDataHandler(rootJson, "required_era", 0, 100);
            this.dwellingSlots = new IntegerDataHandler(rootJson, "dwelling_slots", 0, 100);
            this.getJsonArrayObjects(rootJson, "working_slots")
                    .forEach(obj -> workingSlots.add(new WorkingSlotHandler(obj)));
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            requiredEra.toJson(rootJson);
            dwellingSlots.toJson(rootJson);
            JsonArray worksJson = new JsonArray();
            workingSlots.forEach(slot -> worksJson.add(slot.toJson(new JsonObject())));
            rootJson.add("working_slots", worksJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            ArrayList<String> errors = new ArrayList<>();
            errors.addAll(requiredEra.getErrors());
            errors.addAll(dwellingSlots.getErrors());
            workingSlots.forEach(slot -> errors.addAll(slot.getErrors()));
            return errors;
        }
    }

    public static class WorkingSlotHandler extends DataHandler {
        public final StringDataHandler id;
        public final IntegerDataHandler maxLevel;

        public WorkingSlotHandler(@NotNull JsonObject rootJson) {
            super(rootJson);
            this.id = new StringDataHandler(rootJson, "id");
            this.maxLevel = new IntegerDataHandler(rootJson, "max_level", 0, 100);
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            id.toJson(rootJson);
            maxLevel.toJson(rootJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            ArrayList<String> errors = id.getErrors();
            errors.addAll(maxLevel.getErrors());
            return errors;
        }
    }

    public static class BuildingVariantHandler extends DataHandler {
        public final StringDataHandler name;
        public final IntegerDataHandler sizeX;
        public final IntegerDataHandler sizeY;
        public final IntegerDataHandler sizeZ;
        public final ArrayList<BuildingVariantLevelHandler> levels = new ArrayList<>();

        public BuildingVariantHandler(@NotNull JsonObject rootJson) {
            super(rootJson);
            this.name = new StringDataHandler(rootJson, "name");
            JsonObject sizeJson = this.getJsonObject(rootJson, "size");
            this.sizeX = new IntegerDataHandler(sizeJson, "x", 1, 1000);
            this.sizeY = new IntegerDataHandler(sizeJson, "y", 1, 1000);
            this.sizeZ = new IntegerDataHandler(sizeJson, "z", 1, 1000);
            this.getJsonArrayObjects(rootJson, "levels")
                    .forEach(level -> levels.add(new BuildingVariantLevelHandler(level)));
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            name.toJson(rootJson);
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
            errors.addAll(sizeX.getErrors());
            errors.addAll(sizeY.getErrors());
            errors.addAll(sizeZ.getErrors());
            levels.forEach(level -> errors.addAll(level.getErrors()));
            return errors;
        }
    }

    public static class BuildingVariantLevelHandler extends DataHandler {
        public final StringDataHandler schematic;
        public final ArrayList<WaypointHandler> waypoints = new ArrayList<>();

        public BuildingVariantLevelHandler(@NotNull JsonObject rootJson) {
            super(rootJson);
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
            super(rootJson);
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
