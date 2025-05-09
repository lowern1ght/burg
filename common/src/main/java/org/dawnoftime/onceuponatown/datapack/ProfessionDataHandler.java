package org.dawnoftime.onceuponatown.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.dawnoftime.onceuponatown.datapack.core.DataHandler;
import org.dawnoftime.onceuponatown.datapack.core.IntegerDataHandler;
import org.dawnoftime.onceuponatown.datapack.core.StringDataHandler;
import org.dawnoftime.onceuponatown.datapack.core.StringEnumDataHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;

public class ProfessionDataHandler extends DataHandler {
    public final StringDataHandler id;
    public final StringDataHandler schedule;
    public final StringDataHandler workAI;
    public final ProfessionLevelsDataHandler levels;

    public ProfessionDataHandler(@NotNull JsonObject rootJson) {
        super(rootJson);
        id = new StringDataHandler(rootJson, "id");
        schedule = new StringDataHandler(rootJson, "schedule");
        workAI = new StringDataHandler(rootJson, "work_ai");
        levels = new ProfessionLevelsDataHandler(rootJson, "levels");
    }

    @Override
    public JsonObject toJson(@NotNull JsonObject rootJson) {
        id.toJson(rootJson);
        schedule.toJson(rootJson);
        workAI.toJson(rootJson);
        levels.toJson(rootJson);
        return rootJson;
    }

    @Override
    public @NotNull ArrayList<String> getErrors() {
        ArrayList<String> errors = new ArrayList<>();
        errors.addAll(id.getErrors());
        errors.addAll(schedule.getErrors());
        errors.addAll(workAI.getErrors());
        errors.addAll(levels.getErrors());
        return errors;
    }

    public static class ProfessionLevelsDataHandler extends DataHandler {
        private final @NotNull String key;
        public final HashMap<Integer, ProfessionLevelDataHandler> levels = new HashMap<>();

        public ProfessionLevelsDataHandler(@NotNull JsonObject rootJson, @NotNull String key) {
            super(rootJson);
            this.key = key;
            JsonObject levelsJson = this.getJsonObject(rootJson, key);
            for (int level = 1; level <= levelsJson.size(); level++) {
                levels.put(level, new ProfessionLevelDataHandler(this.getJsonObject(levelsJson, String.valueOf(level))));
            }
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            JsonObject levelsJson = new JsonObject();
            levels.forEach((level, data) -> levelsJson.add(String.valueOf(level), data.toJson(new JsonObject())));
            rootJson.add(this.key, levelsJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            ArrayList<String> errors = new ArrayList<>();
            levels.values().forEach(level -> errors.addAll(level.getErrors()));
            return errors;
        }
    }

    public static class ProfessionLevelDataHandler extends DataHandler {
        public final ArrayList<ItemQuantityDataHandler> productions = new ArrayList<>();
        public final ArrayList<ProfessionCraftsDataHandler> crafts = new ArrayList<>();
        public final ArrayList<ProfessionTradesDataHandler> trades = new ArrayList<>();

        public ProfessionLevelDataHandler(@NotNull JsonObject rootJson) {
            super(rootJson);
            this.getJsonArrayObjects(rootJson, "productions")
                    .forEach(production -> productions.add(new ItemQuantityDataHandler(production)));
            this.getJsonArrayObjects(rootJson, "crafts")
                    .forEach(craft -> crafts.add(new ProfessionCraftsDataHandler(craft)));
            this.getJsonArrayObjects(rootJson, "trades")
                    .forEach(trade -> trades.add(new ProfessionTradesDataHandler(trade)));
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            JsonArray productionsJson = new JsonArray();
            productions.forEach(production -> productionsJson.add(production.toJson(new JsonObject())));
            rootJson.add("productions", productionsJson);
            JsonArray craftsJson = new JsonArray();
            crafts.forEach(craft -> craftsJson.add(craft.toJson(new JsonObject())));
            rootJson.add("crafts", craftsJson);
            JsonArray tradesJson = new JsonArray();
            trades.forEach(trade -> tradesJson.add(trade.toJson(new JsonObject())));
            rootJson.add("trades", tradesJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            ArrayList<String> errors = new ArrayList<>();
            productions.forEach(production -> errors.addAll(production.getErrors()));
            crafts.forEach(craft -> errors.addAll(craft.getErrors()));
            trades.forEach(trade -> errors.addAll(trade.getErrors()));
            return errors;
        }
    }

    public static class ItemQuantityDataHandler extends DataHandler {
        private final @Nullable String key;
        public final StringDataHandler item;
        public final IntegerDataHandler amount;

        public ItemQuantityDataHandler(@NotNull JsonObject rootJson) {
            this(rootJson, null);
        }

        public ItemQuantityDataHandler(@NotNull JsonObject rootJson, @Nullable String key) {
            super(rootJson);
            this.key = key;
            this.item = new StringDataHandler(rootJson, "id");
            this.amount = new IntegerDataHandler(rootJson, "amount", 1, 1000);
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            JsonObject jsonObject = (key == null) ? rootJson : new JsonObject();
            item.toJson(jsonObject);
            amount.toJson(jsonObject);
            if (key != null) {
                rootJson.add(key, jsonObject);
            }
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            ArrayList<String> errors = item.getErrors();
            errors.addAll(amount.getErrors());
            return errors;
        }
    }

    public static class ProfessionCraftsDataHandler extends DataHandler {
        //TODO Need to define proper data structure.
        public final StringDataHandler craftId;

        public ProfessionCraftsDataHandler(@NotNull JsonObject rootJson) {
            super(rootJson);
            this.craftId = new StringDataHandler(rootJson, "id");
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            craftId.toJson(rootJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            return craftId.getErrors();
        }
    }

    public enum TradeType{
        // TODO Bouger cet enum où on gère les crafts !
        BUY,
        SELL;
    }

    public static class ProfessionTradesDataHandler extends DataHandler {
        public final StringEnumDataHandler<TradeType> type;
        public final ItemQuantityDataHandler costA;
        public final ItemQuantityDataHandler result;

        public ProfessionTradesDataHandler(@NotNull JsonObject rootJson) {
            super(rootJson);
            this.type = new StringEnumDataHandler<>(rootJson, "type", TradeType.class);
            this.costA = new ItemQuantityDataHandler(rootJson, "cost_a");
            this.result = new ItemQuantityDataHandler(rootJson, "result");
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            type.toJson(rootJson);
            costA.toJson(rootJson);
            result.toJson(rootJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            ArrayList<String> errors = type.getErrors();
            errors.addAll(costA.getErrors());
            errors.addAll(result.getErrors());
            return errors;
        }
    }
}
