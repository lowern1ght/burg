package org.dawnoftime.onceuponatown.datapack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.dawnoftime.onceuponatown.datapack.core.DataHandler;
import org.dawnoftime.onceuponatown.datapack.core.IntegerDataHandler;
import org.dawnoftime.onceuponatown.datapack.core.StringDataHandler;
import org.dawnoftime.onceuponatown.datapack.core.StringEnumDataHandler;
import org.dawnoftime.onceuponatown.entity.ai.work.WorkAi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class ProfessionDataHandler extends DataHandler {
    public final StringDataHandler id;
    public final StringDataHandler schedule;
    public final StringEnumDataHandler<WorkAi> workAi;
    public final ArrayList<ProfessionLevelHandler> levels = new ArrayList<>();

    public ProfessionDataHandler(@NotNull JsonObject rootJson) {
        id = new StringDataHandler(rootJson, "id");
        schedule = new StringDataHandler(rootJson, "schedule");
        workAi = new StringEnumDataHandler<>(rootJson,  "work_ai", WorkAi.class);
        this.getJsonArrayObjects(rootJson, "levels")
            .forEach(obj -> levels.add(new ProfessionLevelHandler(obj)));
    }

    @Override
    public JsonObject toJson(@NotNull JsonObject rootJson) {
        id.toJson(rootJson);
        schedule.toJson(rootJson);
        workAi.toJson(rootJson);
        JsonArray levelsJson = new JsonArray();
        levels.forEach(level -> levelsJson.add(level.toJson(new JsonObject())));
        rootJson.add("levels", levelsJson);
        return rootJson;
    }

    @Override
    public @NotNull ArrayList<String> getErrors() {
        ArrayList<String> errors = new ArrayList<>();
        errors.addAll(id.getErrors());
        errors.addAll(schedule.getErrors());
        errors.addAll(workAi.getErrors());
        levels.forEach(level -> errors.addAll(level.getErrors()));
        return errors;
    }

    public static class ProfessionLevelHandler extends DataHandler {
        public final ArrayList<ProductionDataHandler> production = new ArrayList<>();
        public final ArrayList<ProfessionCraftHandler> crafts = new ArrayList<>();
        public final ArrayList<ProfessionTradeHandler> buy = new ArrayList<>();
        public final ArrayList<ProfessionTradeHandler> sell = new ArrayList<>();

        public ProfessionLevelHandler(@NotNull JsonObject rootJson) {
            this.getJsonArrayObjects(rootJson, "production")
                    .forEach(production -> this.production.add(new ProductionDataHandler(production)));
            this.getJsonArrayObjects(rootJson, "crafts")
                    .forEach(craft -> crafts.add(new ProfessionCraftHandler(craft)));
            this.getJsonArrayObjects(rootJson, "buy")
                    .forEach(trade -> buy.add(new ProfessionTradeHandler(trade)));
            this.getJsonArrayObjects(rootJson, "sell")
                .forEach(trade -> sell.add(new ProfessionTradeHandler(trade)));
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            JsonArray productionJson = new JsonArray();
            production.forEach(production -> productionJson.add(production.toJson(new JsonObject())));
            rootJson.add("production", productionJson);
            JsonArray craftsJson = new JsonArray();
            crafts.forEach(craft -> craftsJson.add(craft.toJson(new JsonObject())));
            rootJson.add("crafts", craftsJson);
            JsonArray buyTradesJson = new JsonArray();
            buy.forEach(trade -> buyTradesJson.add(trade.toJson(new JsonObject())));
            rootJson.add("buy", buyTradesJson);
            JsonArray sellTradesJson = new JsonArray();
            sell.forEach(trade -> sellTradesJson.add(trade.toJson(new JsonObject())));
            rootJson.add("sell", sellTradesJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            ArrayList<String> errors = new ArrayList<>();
            production.forEach(production -> errors.addAll(production.getErrors()));
            crafts.forEach(craft -> errors.addAll(craft.getErrors()));
            buy.forEach(trade -> errors.addAll(trade.getErrors()));
            sell.forEach(trade -> errors.addAll(trade.getErrors()));
            return errors;
        }
    }

    public static class ProfessionCraftHandler extends DataHandler {
        // TODO Need to define proper data structure.
        public final StringDataHandler item;

        public ProfessionCraftHandler(@NotNull JsonObject rootJson) {
            this.item = new StringDataHandler(rootJson, "id");
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            item.toJson(rootJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            return item.getErrors();
        }
    }

    public static class ProfessionTradeHandler extends DataHandler {
        public final ItemQuantityDataHandler costA;
        public final ItemQuantityDataHandler costB;
        public final ItemQuantityDataHandler result;

        public ProfessionTradeHandler(@NotNull JsonObject rootJson) {
            this.costA = new ItemQuantityDataHandler(this.getJsonObject(rootJson, "cost_a"), "cost_a");
            this.costB = new ItemQuantityDataHandler(this.getJsonObject(rootJson, "cost_b"), "cost_b", "nothing");
            this.result = new ItemQuantityDataHandler(this.getJsonObject(rootJson, "result"), "result");
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            costA.toJson(rootJson);
            costB.toJson(rootJson);
            result.toJson(rootJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            ArrayList<String> errors = costA.getErrors();
            errors.addAll(costB.getErrors());
            errors.addAll(result.getErrors());
            return errors;
        }
    }

    public static class ItemQuantityDataHandler extends DataHandler {
        private final @Nullable String key;
        public final StringDataHandler item;
        public final IntegerDataHandler amount;

        public ItemQuantityDataHandler(@NotNull JsonObject rootJson, @Nullable String key) {
            this.key = key;
            this.item = new StringDataHandler(rootJson, "id");
            this.amount = new IntegerDataHandler(rootJson, "amount", 1, Integer.MAX_VALUE, 1);
        }

        public ItemQuantityDataHandler(@NotNull JsonObject rootJson, @Nullable String key, String fallback) {
            this.key = key;
            this.item = new StringDataHandler(rootJson, "id", fallback);
            this.amount = new IntegerDataHandler(rootJson, "amount", 1, Integer.MAX_VALUE, 1);
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

    public static class ProductionDataHandler extends DataHandler {
        public final StringDataHandler item;
        public final IntegerDataHandler amount;
        public final IntegerDataHandler maxStock;

        public ProductionDataHandler(@NotNull JsonObject rootJson) {
            this.item = new StringDataHandler(rootJson, "id");
            this.amount = new IntegerDataHandler(rootJson, "amount", 1, Integer.MAX_VALUE);
            this.maxStock = new IntegerDataHandler(rootJson, "max_stock", 1, Integer.MAX_VALUE);
        }

        @Override
        public JsonObject toJson(@NotNull JsonObject rootJson) {
            item.toJson(rootJson);
            amount.toJson(rootJson);
            maxStock.toJson(rootJson);
            return rootJson;
        }

        @Override
        public @NotNull ArrayList<String> getErrors() {
            ArrayList<String> errors = item.getErrors();
            errors.addAll(amount.getErrors());
            errors.addAll(maxStock.getErrors());
            return errors;
        }
    }
}
