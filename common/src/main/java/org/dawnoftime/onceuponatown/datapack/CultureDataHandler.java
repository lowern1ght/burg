package org.dawnoftime.onceuponatown.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.dawnoftime.onceuponatown.datapack.core.DataHandler;
import org.dawnoftime.onceuponatown.datapack.core.IntegerDataHandler;
import org.dawnoftime.onceuponatown.datapack.core.StringDataHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class CultureDataHandler extends DataHandler {
    private final StringDataHandler id;
    private final ArrayList<SpecializationDataHandler> specializations = new ArrayList<>();
    private final ArrayList<EraDataHandler> eras = new ArrayList<>();
    private final ArrayList<StarterPackDataHandler> starterPack = new ArrayList<>();

    public CultureDataHandler(@NotNull JsonObject rootJson) {
        super(rootJson);
        this.id = new StringDataHandler(rootJson, "id");
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

    private static class SpecializationDataHandler extends DataHandler {
        public final StringDataHandler id;
        public final IntegerDataHandler colorR;
        public final IntegerDataHandler colorG;
        public final IntegerDataHandler colorB;

        public SpecializationDataHandler(@NotNull JsonObject rootJson) {
            super(rootJson);
            this.id = new StringDataHandler(rootJson, "id");
            this.colorR = new IntegerDataHandler(rootJson, "colorR", 0, 255);
            this.colorG = new IntegerDataHandler(rootJson, "colorG", 0, 255);
            this.colorB = new IntegerDataHandler(rootJson, "colorB", 0, 255);
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            id.toJson(rootJson);
            colorR.toJson(rootJson);
            colorG.toJson(rootJson);
            colorB.toJson(rootJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            ArrayList<String> errors = new ArrayList<>();
            errors.addAll(id.getErrors());
            errors.addAll(colorR.getErrors());
            errors.addAll(colorG.getErrors());
            errors.addAll(colorB.getErrors());
            return errors;
        }
    }

    private static class EraDataHandler extends DataHandler {
        public final IntegerDataHandler requiredExperience;
        public final IntegerDataHandler maxBuildingWeight;

        public EraDataHandler(@NotNull JsonObject rootJson) {
            super(rootJson);
            this.requiredExperience = new IntegerDataHandler(rootJson, "required_experience", 0, 100000);
            this.maxBuildingWeight = new IntegerDataHandler(rootJson, "max_buildings_weight", 100, 100000);
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            requiredExperience.toJson(rootJson);
            maxBuildingWeight.toJson(rootJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            ArrayList<String> errors = new ArrayList<>();
            errors.addAll(requiredExperience.getErrors());
            errors.addAll(maxBuildingWeight.getErrors());
            return errors;
        }
    }

    private static class StarterPackDataHandler extends DataHandler {
        private final StringDataHandler id;
        private final IntegerDataHandler min;
        private final IntegerDataHandler max;

        public StarterPackDataHandler(@NotNull JsonObject rootJson) {
            super(rootJson);
            this.id = new StringDataHandler(rootJson, "id");
            this.min = new IntegerDataHandler(rootJson, "min", 0, 100000);
            this.max = new IntegerDataHandler(rootJson, "max", 0, 100000);
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
}
