package org.dawnoftime.onceuponatown.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.datapack.core.DataHandler;
import org.dawnoftime.onceuponatown.datapack.core.IntegerDataHandler;
import org.dawnoftime.onceuponatown.datapack.core.StringDataHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class CultureDataHandler extends DataHandler {
    public final StringDataHandler id;
    public final ArrayList<SpecializationDataHandler> specializations = new ArrayList<>();
    public final ArrayList<EraDataHandler> eras = new ArrayList<>();
    public final ArrayList<StarterPackDataHandler> starterPack = new ArrayList<>();

    public CultureDataHandler(@NotNull JsonObject rootJson) {
        id = new StringDataHandler(rootJson, "id");
        this.getJsonArrayObjects(rootJson, "specializations")
                .forEach(obj -> specializations.add(new SpecializationDataHandler(obj)));
        this.getJsonArrayObjects(rootJson, "eras")
                .forEach(obj -> eras.add(new EraDataHandler(obj)));
        this.getJsonArrayObjects(rootJson, "buildings_starter_pack")
                .forEach(obj -> starterPack.add(new StarterPackDataHandler(obj)));
    }

    @Override
    public JsonObject toJson(@NotNull JsonObject rootJson) {
        id.toJson(rootJson);
        JsonArray specsJson = new JsonArray();
        specializations.forEach(specialization -> specsJson.add(specialization.toJson(new JsonObject())));
        rootJson.add("specializations", specsJson);
        JsonArray erasJson = new JsonArray();
        eras.forEach(era -> erasJson.add(era.toJson(new JsonObject())));
        rootJson.add("eras", erasJson);
        JsonArray starterJson = new JsonArray();
        starterPack.forEach(starter -> starterJson.add(starter.toJson(new JsonObject())));
        rootJson.add("buildings_starter_pack", starterJson);
        return rootJson;
    }

    @Override
    public @NotNull ArrayList<String> getErrors() {
        ArrayList<String> errors = id.getErrors();
        specializations.forEach(spec -> errors.addAll(spec.getErrors()));
        eras.forEach(era -> errors.addAll(era.getErrors()));
        starterPack.forEach(starter -> errors.addAll(starter.getErrors()));
        return errors;
    }

    public static class SpecializationDataHandler extends DataHandler {
        public final StringDataHandler id;
        public final IntegerDataHandler colorRed;
        public final IntegerDataHandler colorGreen;
        public final IntegerDataHandler colorBlue;

        public SpecializationDataHandler(@NotNull JsonObject rootJson) {
            this.id = new StringDataHandler(rootJson, "id");
            this.colorRed = new IntegerDataHandler(rootJson, "colorRed", 0, 255, 100);
            this.colorGreen = new IntegerDataHandler(rootJson, "colorGreen", 0, 255, 100);
            this.colorBlue = new IntegerDataHandler(rootJson, "colorBlue", 0, 255, 100);
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            id.toJson(rootJson);
            colorRed.toJson(rootJson);
            colorGreen.toJson(rootJson);
            colorBlue.toJson(rootJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            ArrayList<String> errors = new ArrayList<>();
            errors.addAll(id.getErrors());
            errors.addAll(colorRed.getErrors());
            errors.addAll(colorGreen.getErrors());
            errors.addAll(colorBlue.getErrors());
            return errors;
        }
    }

    public static class EraDataHandler extends DataHandler {
        public final IntegerDataHandler requiredExperience;
        public final IntegerDataHandler maxBuildingsWeight;

        public EraDataHandler(@NotNull JsonObject rootJson) {
            this.requiredExperience = new IntegerDataHandler(rootJson, "required_experience", 0, Integer.MAX_VALUE, 0);
            this.maxBuildingsWeight = new IntegerDataHandler(rootJson, "max_buildings_weight", 0, Integer.MAX_VALUE, Integer.MAX_VALUE);
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            requiredExperience.toJson(rootJson);
            maxBuildingsWeight.toJson(rootJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            ArrayList<String> errors = new ArrayList<>();
            errors.addAll(requiredExperience.getErrors());
            errors.addAll(maxBuildingsWeight.getErrors());
            return errors;
        }
    }

    public static class StarterPackDataHandler extends DataHandler {
        public final StringDataHandler id;
        public final IntegerDataHandler min;
        public final IntegerDataHandler max;

        public StarterPackDataHandler(@NotNull JsonObject rootJson) {
            this.id = new StringDataHandler(rootJson, "id");
            this.min = new IntegerDataHandler(rootJson, "min", 0, Integer.MAX_VALUE);
            this.max = new IntegerDataHandler(rootJson, "max", 0, Integer.MAX_VALUE);
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            id.toJson(rootJson);
            min.toJson(rootJson);
            max.toJson(rootJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            ArrayList<String> errors = new ArrayList<>();
            errors.addAll(id.getErrors());
            errors.addAll(min.getErrors());
            errors.addAll(max.getErrors());
            if (min.isGreaterThan(max)) {
                errors.add("Field 'min' must be <= to field 'max'");
            }
            return errors;
        }
    }

    public static class FoodsDataHandler extends DataHandler {
        public final StringDataHandler id;

        public FoodsDataHandler(@NotNull JsonObject rootJson) {
            this.id = new StringDataHandler(rootJson, "id");
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            id.toJson(rootJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            return new ArrayList<>(id.getErrors());
        }
    }
}
