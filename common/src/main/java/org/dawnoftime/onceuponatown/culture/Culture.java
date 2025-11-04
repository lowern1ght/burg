package org.dawnoftime.onceuponatown.culture;

import com.google.gson.JsonObject;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FastColor;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.Utils;
import org.dawnoftime.onceuponatown.building.schematic.BuildSchematic;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.building.type.BuildType;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import org.dawnoftime.onceuponatown.building.type.RoadType;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.datapack.CultureDataHandler;
import org.dawnoftime.onceuponatown.datapack.ProfessionDataHandler;
import org.dawnoftime.onceuponatown.datapack.RoadDataHandler;
import org.dawnoftime.onceuponatown.datapack.core.DataHandler;
import org.dawnoftime.onceuponatown.registry.ScheduleRegistry;
import org.dawnoftime.onceuponatown.trade.NpcOffer;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Pair;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.*;

public class Culture {
    public static final Culture CORRUPTED_CULTURE = new Culture("default_culture", new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), List.of(new Era(1, 0, Integer.MAX_VALUE)));
    private final String id;
    private final HashMap<String, Specialization> specializations;
    private final List<Era> eras;
    private final HashMap<String, Profession> professions;
    private final HashMap<String, BuildType> buildTypes;
    private final HashMap<String, Pair<Integer, Integer>> starterPack;
    //private final List<Item> foods; TODO load foods

    private Culture(String id, HashMap<String, Specialization> specializations, HashMap<String, Profession> professions, HashMap<String, BuildType> buildTypes, HashMap<String, Pair<Integer, Integer>> starterPack, List<Era> eras) {
        this.id = id;
        this.specializations = specializations;
        this.professions = professions;
        this.buildTypes = buildTypes;
        this.starterPack = starterPack;
        this.eras = eras;
        //this.foods = foods;
    }

    public static @Nullable Culture load(String detectedId, Resource cultureJsonResource, ResourceManager resourceManager) {
        Ouat.info("Loading culture '" + detectedId + "'");

        try (Reader cultureJsonReader = cultureJsonResource.openAsReader()) {
            JsonObject cultureJson = GsonHelper.parse(cultureJsonReader);
            CultureDataHandler cultureData = new CultureDataHandler(cultureJson);

            /* Culture json validation */
            if (!cultureData.isValid()) {
                throw new CorruptedCultureException(detectedId, "Errors in %s : %s".formatted(CULTURE_JSON_FILE_NAME, cultureData.getErrors()));
            }
            if (!detectedId.equals(cultureData.id.get())) {
                throw new CorruptedCultureException(detectedId, "Error in %s : culture's id should match the name of its folder".formatted(CULTURE_JSON_FILE_NAME));
            }

            /* Specializations */
            HashMap<String,Specialization> specializations = new HashMap<>();
            cultureData.specializations.forEach(specializationData -> {
                String id = specializationData.id.get();
                if (specializations.containsKey(id)) {
                    throw new CorruptedCultureException(detectedId, "Error in %s : Duplicated specialization id".formatted(CULTURE_JSON_FILE_NAME));
                }
                int color = FastColor.ARGB32.color(specializationData.colorRed.get(), specializationData.colorGreen.get(), specializationData.colorBlue.get(), 100);
                specializations.put(id, new Specialization(id, color));
            });
            if (specializations.isEmpty()) {
                throw new CorruptedCultureException(detectedId, "Error in %s : No specializations. Each culture should have at least one specialization".formatted(CULTURE_JSON_FILE_NAME));
            }

            /* Eras */
            List<Era> eras = new ArrayList<>();
            int i = 1;
            for (CultureDataHandler.EraDataHandler eraData : cultureData.eras) {
                int requiredExperience = eraData.requiredExperience.get();
                int maxBuildingsWeight = eraData.requiredExperience.get();
                if (i != 1) {
                    if (eras.get(i - 2).requiredExperience() >= requiredExperience) {
                        throw new CorruptedCultureException(detectedId, "Error in %s : Eras required experience are not ascending".formatted(CULTURE_JSON_FILE_NAME));
                    }
                    if (eras.get(i - 2).maxBuildingsWeight() >= maxBuildingsWeight) {
                        throw new CorruptedCultureException(detectedId, "Error in %s : Eras max buildings weight are not ascending".formatted(CULTURE_JSON_FILE_NAME));
                    }
                }
                eras.add(new Era(i++, requiredExperience, maxBuildingsWeight));
            }
            if (eras.isEmpty()) {
                throw new CorruptedCultureException(detectedId, "Error in %s : No eras. Each culture should have at least one era".formatted(CULTURE_JSON_FILE_NAME));
            }

            /* Professions */
            HashMap<String, Profession> professionMap = new HashMap<>();
            loadProfessions(detectedId, professionMap, resourceManager);

            /* BuildTypes */
            HashMap<String, BuildType> buildTypeMap = new HashMap<>();
            /* Buildings */
            loadBuildings(detectedId, buildTypeMap, professionMap, resourceManager);
            /* Roads */
            loadRoads(detectedId,buildTypeMap, resourceManager);

            /* Starter pack */
            HashMap<String, Pair<Integer, Integer>> starterPack = new HashMap<>();
            for (CultureDataHandler.StarterPackDataHandler starterBuildingData : cultureData.starterPack) {
                String id = starterBuildingData.id.get();
                if (starterPack.containsKey(id)) {
                    throw new CorruptedCultureException(detectedId, "Duplicated building type '" + id + "' in starterpack");
                }
                if (!buildTypeMap.containsKey(id)) {
                    throw new CorruptedCultureException(detectedId, "Unknown building type '" + id + "' in starterpack");
                } else if (!(buildTypeMap.get(id) instanceof BuildingType)) {
                    throw new CorruptedCultureException(detectedId, "Wrong building type '" + id + "' in starterpack, it must be a building (no roads, etc)");
                }
                starterPack.put(id, new Pair<>(starterBuildingData.min.get(), starterBuildingData.max.get()));
            }
            if (starterPack.isEmpty()) {
                throw new CorruptedCultureException(detectedId, "Error in %s : No starterpack. Each culture should have at least one building in its starterpack".formatted(CULTURE_JSON_FILE_NAME));
            }
            return new Culture(detectedId, specializations, professionMap, buildTypeMap, starterPack, eras);
        } catch (IOException ioException) {
            throw new CorruptedCultureException(detectedId, "An error occurred while trying to read culture's files");
        } catch (CorruptedCultureException cce) {
            Ouat.error(cce.getMessage());
            // For debug throw cce;
            return null;
        }
    }

    private static void loadProfessions(String detectedId, HashMap<String, Profession> professionMap, ResourceManager resourceManager) throws IOException {
        var professionsRls = resourceManager.listResources(CULTURES_FOLDER_NAME + "/" + detectedId + "/" + PROFESSIONS_FOLDER_NAME, (rl) -> rl.getPath().endsWith(".json")).keySet();
        for (ResourceLocation professionRl : professionsRls) {
            String rlPath = professionRl.getPath();
            String professionId = rlPath.substring(rlPath.lastIndexOf('/') + 1, rlPath.lastIndexOf('.'));
            if (professionMap.containsKey(professionId)) {
                throw new CorruptedCultureException(detectedId, "Duplicated profession '" + professionId + "'.");
            }
            try (Reader professionReader = resourceManager.getResource(professionRl).orElseThrow().openAsReader()) {
                JsonObject professionJson = GsonHelper.parse(professionReader);
                ProfessionDataHandler professionData = new ProfessionDataHandler(professionJson);
                if (!professionData.isValid()) {
                    throw new CorruptedCultureException(detectedId, "Errors in %s : %s".formatted(rlPath, professionData.getErrors()));
                }
                if (!professionId.equals(professionData.id.get())) {
                    throw new CorruptedCultureException(detectedId, "Error in %s : profession's id should match the name of its file".formatted(rlPath));
                }

                /* Profession's levels */
                List<Profession.Level> levels = new ArrayList<>();
                for (ProfessionDataHandler.ProfessionLevelHandler levelData : professionData.levels) {
                    /* Production */
                    HashMap<Item, Pair<Integer, Integer>> production = new HashMap<>();
                    for (ProfessionDataHandler.ProductionDataHandler productionData : levelData.production) {
                        // TODO For now, valid items are vanilla only, change to support modded
                        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(productionData.item.asString()));
                        if (item == Items.AIR) {
                            throw new CorruptedCultureException(detectedId, "Error in %s : Invalid item in production list".formatted(rlPath));
                        }
                        if (production.containsKey(item)) {
                            throw new CorruptedCultureException(detectedId, "Error in %s : Duplicated item in production list".formatted(rlPath));
                        }
                        production.put(item, new Pair<>(productionData.amount.get(), productionData.maxStock.get()));
                    }
                    /* Crafts */
                    Set<Item> crafts = new HashSet<>();
                    for (ProfessionDataHandler.ProfessionCraftHandler craftData : levelData.crafts) {
                        // TODO For now, valid items are vanilla only, change to support modded
                        Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(craftData.item.asString()));
                        if (item == Items.AIR) {
                            throw new CorruptedCultureException(detectedId, "Error in %s : Invalid item in crafts list".formatted(rlPath));
                        }
                        if (crafts.contains(item)) {
                            throw new CorruptedCultureException(detectedId, "Error in %s : Duplicated item in crafts list".formatted(rlPath));
                        }
                        crafts.add(item);
                    }
                    /* Trades */
                    List<NpcOffer> buyTrades = new ArrayList<>();
                    for (ProfessionDataHandler.ProfessionTradeHandler buyData : levelData.buy) {
                        // TODO For now, valid items are vanilla only, change to support modded
                        Item costA = BuiltInRegistries.ITEM.get(new ResourceLocation(buyData.costA.item.get()));
                        if (costA == Items.AIR) {
                            throw new CorruptedCultureException(detectedId, "Error in %s : Invalid item in trades list".formatted(rlPath));
                        }
                        Item result = BuiltInRegistries.ITEM.get(new ResourceLocation(buyData.result.item.get()));
                        if (result == Items.AIR) {
                            throw new CorruptedCultureException(detectedId, "Error in %s : Invalid item in trades list".formatted(rlPath));
                        }
                        NpcOffer.Builder builder = NpcOffer.Builder.buyOffer(
                            new ItemStack(costA, buyData.costA.amount.get()),
                            new ItemStack(result, buyData.result.amount.get()));
                        String costBId = buyData.costB.item.get();
                        if (costBId != null && !costBId.equals("nothing")) {
                            Item costB = BuiltInRegistries.ITEM.get(new ResourceLocation(costBId));
                            if (costB == Items.AIR) {
                                throw new CorruptedCultureException(detectedId, "Error in %s : Invalid item in trades list".formatted(rlPath));
                            }
                            builder.inputB(new ItemStack(costB, buyData.costB.amount.get()));
                        }
                        buyTrades.add(builder.build());
                    }
                    List<NpcOffer> sellTrades = new ArrayList<>();
                    for (ProfessionDataHandler.ProfessionTradeHandler sellData : levelData.sell) {
                        // TODO For now, valid items are vanilla only, change to support modded
                        Item costA = BuiltInRegistries.ITEM.get(new ResourceLocation(sellData.costA.item.get()));
                        if (costA == Items.AIR) {
                            throw new CorruptedCultureException(detectedId, "Error in %s : Invalid item in trades list".formatted(rlPath));
                        }
                        Item result = BuiltInRegistries.ITEM.get(new ResourceLocation(sellData.result.item.get()));
                        if (result == Items.AIR) {
                            throw new CorruptedCultureException(detectedId, "Error in %s : Invalid item in trades list".formatted(rlPath));
                        }
                        NpcOffer.Builder builder = NpcOffer.Builder.sellOffer(
                            new ItemStack(costA, sellData.costA.amount.get()),
                            new ItemStack(result, sellData.result.amount.get()));
                        String costBId = sellData.costB.item.get();
                        if (costBId != null && !costBId.equals("nothing")) {
                            Item costB = BuiltInRegistries.ITEM.get(new ResourceLocation(costBId));
                            if (costB == Items.AIR) {
                                throw new CorruptedCultureException(detectedId, "Error in %s : Invalid item in trades list".formatted(rlPath));
                            }
                            builder.inputB(new ItemStack(costB, sellData.costB.amount.get()));
                        }
                        sellTrades.add(builder.build());
                    }
                    buyTrades.addAll(sellTrades);
                    levels.add(new Profession.Level(production, crafts.stream().toList(), buyTrades));
                }
                professionMap.put(professionId, new Profession(professionId, ScheduleRegistry.REGISTRY.DEFAULT_WORKER.get(), professionData.workAi.getEnum(), levels));
            }
        }
    }

    private static void loadBuildings(String detectedId, HashMap<String, BuildType> buildTypeMap, HashMap<String, Profession> professionMap, ResourceManager resourceManager) throws IOException {
        var buildingsRls = resourceManager.listResources(CULTURES_FOLDER_NAME + "/" + detectedId + "/" + BUILDINGS_FOLDER_NAME, (rl) -> rl.getPath().endsWith(".json")).keySet();
        for (ResourceLocation buildingRl : buildingsRls) {
            String rlPath = buildingRl.getPath();
            String typeId = rlPath.substring(rlPath.lastIndexOf('/') + 1, rlPath.lastIndexOf('.'));
            if (buildTypeMap.containsKey(typeId)) {
                throw new CorruptedCultureException(detectedId, "Duplicated building type '" + typeId + "'.");
            }
            try (Reader buildingReader = resourceManager.getResource(buildingRl).orElseThrow().openAsReader()) {
                JsonObject buildingJson = GsonHelper.parse(buildingReader);
                BuildingDataHandler buildingData = new BuildingDataHandler(buildingJson);
                if (!buildingData.isValid()) {
                    throw new CorruptedCultureException(detectedId, "Errors in %s : %s".formatted(rlPath, buildingData.getErrors()));
                }
                if (!typeId.equals(buildingData.id.get())) {
                    throw new CorruptedCultureException(detectedId, "Error in %s : building's id should match the name of its file".formatted(rlPath));
                }
                /* Building's levels */
                List<BuildType.BuildLevel> levels = new ArrayList<>();
                int level = 1;
                for (BuildingDataHandler.BuildingLevelHandler levelData : buildingData.levels) {
                    /* Working slots */
                    HashMap<Profession, Integer> workingSlots = new HashMap<>();
                    Set<String> professionsIds = new HashSet<>();
                    for (BuildingDataHandler.WorkingSlotHandler workingSlotData : levelData.workingSlots) {
                        String id = workingSlotData.profession.get();
                        if (professionsIds.contains(id)) {
                            throw new CorruptedCultureException(detectedId, "Error in %s : duplicated professions in working slots".formatted(rlPath));
                        }
                        if (!professionMap.containsKey(id)) {
                            throw new CorruptedCultureException(detectedId, "Error in %s : unknown profession".formatted(rlPath));
                        }
                        workingSlots.put(professionMap.get(id), workingSlotData.slots.get());
                        professionsIds.add(id);
                    }
                    levels.add(new BuildType.BuildLevel(level, levelData.requiredEra.get(), 0, workingSlots, levelData.dwellingSlots.get()));
                    ++level;
                }
                if (levels.isEmpty()) {
                    throw new CorruptedCultureException(detectedId, "Error in %s : No levels. Each building should have at least one level".formatted(rlPath));
                }
                /* Building's variants */
                HashMap<String, BuildVariant> variants = new HashMap<>();
                for (BuildingDataHandler.BuildingVariantHandler variantData : buildingData.variants) {
                    String id = variantData.name.get();
                    Vec3i size = new Vec3i(variantData.sizeX.get(), variantData.sizeY.get(), variantData.sizeZ.get());

                    TreeMap<Integer, BuildSchematic> schematics = new TreeMap<>();
                    int variantLevel = 1;
                    for (BuildingDataHandler.BuildingVariantLevelHandler variantLevelData : variantData.levels) {
                        ResourceLocation schematicRl = Ouat.modResource(DataHandler.CULTURES_FOLDER_NAME + "/%s/schematics/%s.nbt".formatted(detectedId, variantLevelData.schematic.get()));
                        Vec3i schematicDimensions = Utils.getSchematicDimensions(detectedId, id, schematicRl, resourceManager);
                        if (!schematicDimensions.equals(size)) {
                            throw new CorruptedCultureException(detectedId, "Error in %s : A variant has wrong schematic dimensions".formatted(rlPath));
                        }
                        // TODO Read waypoints
                        BuildSchematic schematic = new BuildSchematic(schematicRl, null);
                        schematics.put(variantLevel, schematic);
                        ++variantLevel;
                    }
                    if (variants.containsKey(id)) {
                        throw new CorruptedCultureException(detectedId, "Error in %s : Duplicated variants ids".formatted(rlPath));
                    }
                    variants.put(id, new BuildVariant(id, size, schematics));
                }
                if (variants.isEmpty()) {
                    throw new CorruptedCultureException(detectedId, "Error in %s : No variants. Each building should have at least one variant".formatted(rlPath));
                }
                Item iconItem = BuiltInRegistries.ITEM.getOptional(new ResourceLocation(buildingData.item.asString())).orElse(Items.OAK_PLANKS);
                buildTypeMap.put(typeId, new BuildingType(typeId, buildingData.weight.get(), levels, buildingData.category.getEnum(), iconItem, variants.values().stream().toList(), detectedId));
            }
        }
    }

    private static void loadRoads(String detectedId, HashMap<String, BuildType> buildTypeMap, ResourceManager resourceManager) throws IOException {
        var roadsRls = resourceManager.listResources(CULTURES_FOLDER_NAME + "/" + detectedId + "/" + ROADS_FOLDER_NAME, (rl) -> rl.getPath().endsWith(".json")).keySet();
        for (ResourceLocation roadRl : roadsRls) {
            String rlPath = roadRl.getPath();
            String typeId = rlPath.substring(rlPath.lastIndexOf('/') + 1, rlPath.lastIndexOf('.'));
            if (buildTypeMap.containsKey(typeId)) {
                throw new CorruptedCultureException(detectedId, "Duplicated road type '" + typeId + "'.");
            }
            try (Reader roadReader = resourceManager.getResource(roadRl).orElseThrow().openAsReader()) {
                JsonObject roadJson = GsonHelper.parse(roadReader);
                RoadDataHandler roadData = new RoadDataHandler(roadJson);
                if (!roadData.isValid()) {
                    throw new CorruptedCultureException(detectedId, "Errors in %s : %s".formatted(rlPath, roadData.getErrors()));
                }
                if (!typeId.equals(roadData.id.get())) {
                    throw new CorruptedCultureException(detectedId, "Error in %s : road's id should match the name of its file".formatted(rlPath));
                }
                /* Road's levels */
                List<BuildType.BuildLevel> levels = new ArrayList<>();
                int level = 1;
                for (RoadDataHandler.RoadLevelsHandler levelData : roadData.levels) {
                    levels.add(new BuildType.BuildLevel(level, levelData.requiredEra.get(), 0, new HashMap<>(), 0));
                    ++level;
                }
                if (levels.isEmpty()) {
                    throw new CorruptedCultureException(detectedId, "Error in %s : No levels. Each road should have at least one level".formatted(rlPath));
                }
                /* Road's variants */
                HashMap<String, Pair<BuildVariant, String>> variants = new HashMap<>();
                for (RoadDataHandler.RoadVariantHandler variantData : roadData.variants) {
                    String id = variantData.name.get();
                    Vec3i size = new Vec3i(variantData.sizeX.get(), variantData.sizeY.get(), variantData.sizeZ.get());
                    String shape = variantData.shape.get();
                    TreeMap<Integer, BuildSchematic> schematics = new TreeMap<>();
                    int variantLevel = 1;
                    for (RoadDataHandler.RoadVariantLevelHandler variantLevelData : variantData.levels) {
                        ResourceLocation schematicRl = Ouat.modResource(DataHandler.CULTURES_FOLDER_NAME + "/%s/schematics/%s.nbt".formatted(detectedId, variantLevelData.schematic.get()));
                        Vec3i schematicDimensions = Utils.getSchematicDimensions(detectedId, id, schematicRl, resourceManager);
                        if (!schematicDimensions.equals(size)) {
                            throw new CorruptedCultureException(detectedId, "Error in %s : A variant has wrong schematic dimensions".formatted(rlPath));
                        }
                        // TODO Read waypoints
                        BuildSchematic schematic = new BuildSchematic(schematicRl, null);
                        schematics.put(variantLevel, schematic);
                        ++variantLevel;
                    }
                    if (variants.containsKey(id)) {
                        throw new CorruptedCultureException(detectedId, "Error in %s : Duplicated variants ids".formatted(rlPath));
                    }

                    variants.put(id, new Pair<>(new BuildVariant(id, size, schematics), shape));
                }
                if (variants.isEmpty()) {
                    throw new CorruptedCultureException(detectedId, "Error in %s : No variants. Each road should have at least one variant".formatted(rlPath));
                }
                buildTypeMap.put(typeId, new RoadType(typeId, 0, levels, variants, detectedId));
            }
        }
    }

    public BuildType getBuildType(String typeId) {
        return buildTypes.getOrDefault(typeId, BuildType.DEFAULT);
    }

    public Profession getProfessionOrDefault(String professionId) {
        return professions.getOrDefault(professionId, Profession.UNEMPLOYED);
    }

    @Nullable
    public Profession getProfessionOrNull(String professionId) {
        return professions.getOrDefault(professionId, null);
    }

    public List<Profession> getProfessions() {
        return professions.values().stream().toList();
    }

    /**
     * Returns a random list of buildings that should spawn in a naturally generated hamlet.
     *
     * @param rand RandomSource used to roll the number of each BuildingType.
     * @return The list of BuildingTypes to build.
     */
    public List<BuildingType> getRandomStarterPack(RandomSource rand) {
        List<BuildingType> types = new ArrayList<>();
        for (String typeId : starterPack.keySet()) {
            Pair<Integer, Integer> minMax = starterPack.get(typeId);
            BuildType buildType = buildTypes.get(typeId);
            if (buildType instanceof BuildingType buildingType) {
                int times = rand.nextIntBetweenInclusive(minMax.getA(), minMax.getB());
                for (int i = 0; i < times; ++i) {
                    types.add(buildingType);
                }
            }
        }
        Collections.shuffle(types); // So that the builds do not always spawn in the same sequence
        return types;
    }

    public List<Era> getEras() {
        return eras;
    }

    public List<Specialization> getSpecializations() {
        return specializations.values().stream().toList();
    }

    public HashMap<String, Pair<Integer, Integer>> getStarterPack() {
        return starterPack;
    }

    public String getId() {
        return id;
    }

    public List<BuildType> getBuildTypes() {
        return buildTypes.values().stream().toList();
    }

    public List<BuildingType> getBuildingTypes() {
        List<BuildingType> types = new ArrayList<>();
        for (BuildType buildType : getBuildTypes()) {
            if (buildType instanceof BuildingType buildingType) {
                types.add(buildingType);
            }
        }
        return types;
    }
}
