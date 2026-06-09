package org.dawnoftime.onceuponatown.tick;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import net.minecraft.world.level.levelgen.Heightmap;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.ItemCost;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.PlacedBuilding;
import org.dawnoftime.onceuponatown.town.ProductionEntry;
import org.dawnoftime.onceuponatown.town.Quest;
import org.dawnoftime.onceuponatown.town.QuestManager;
import org.dawnoftime.onceuponatown.town.Town;
import org.dawnoftime.onceuponatown.town.TownInventory;
import org.dawnoftime.onceuponatown.town.TransformationRecipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dawnoftime.onceuponatown.town.FoodRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TickScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TickScheduler.class);
    // DEBUG: log production snapshot every 1200 ticks (1 real-time minute at 20 TPS)

    // Minimum ticks between UI pushes per town to avoid flooding watchers during production bursts.
    private static final int UI_PUSH_COOLDOWN = 60;
    private static final Map<Long, Long> lastUiPushTick = new HashMap<>();


    // Called from OuatForge via TickEvent.ServerTickEvent (Phase.END)
    public static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            LevelTowns levelTowns = LevelTowns.get(level);
            long gameTime = level.getGameTime();
            boolean anyChange = false;

            if (gameTime % 1200 == 0) {
                LOGGER.info("[OUAT-TICK] Scheduler alive. Towns in level {}: {}",
                    level.dimension().location(), levelTowns.getAllTowns().size());
            }

            for (Map.Entry<Long, Town> townEntry : levelTowns.getAllTownEntries()) {
                Town town = townEntry.getValue();
                BlockPos anchorPos = BlockPos.of(townEntry.getKey());
                TownInventory inv = town.getTownInventory();
                boolean townChanged = false;

                // Sum additive production bonus from staffed gardens only.
                // A garden is staffed when activeResidents >= its requiredResidents threshold (same gate as construction).
                int currentActiveResidents = town.getActiveResidents();
                double bonusMultiplier = 1.0 + town.getBuildings().stream()
                        .mapToDouble(b -> {
                            BuildingDef bDef = BuildingDataHandler.get(b.getDefId()).orElse(null);
                            if (bDef == null) return 0.0;
                            double resolved = b.resolvedProductionBonus(bDef);
                            if (resolved == 0.0) return 0.0;
                            if (bDef.requiredResidents > 0 && currentActiveResidents < bDef.requiredResidents) return 0.0;
                            return resolved;
                        })
                        .sum();

                for (PlacedBuilding building : town.getBuildings()) {
                    BuildingDef def = BuildingDataHandler.get(building.getDefId()).orElse(null);
                    if (def == null) continue;
                    BuildingDef.ResolvedBuildingStats stats = def.resolveAtLevel(building.getUpgradeLevel());
                    for (ProductionEntry entry : stats.production()) {
                        if (entry.everyTicks() <= 0) continue;
                        // Apply cadence multiplier: higher multiplier = shorter interval = faster production.
                        int effectiveTicks = stats.totalCadenceMultiplier() > 0
                            ? (int) Math.max(1, Math.round(entry.everyTicks() / (1.0 + stats.totalCadenceMultiplier())))
                            : entry.everyTicks();
                        if (gameTime % effectiveTicks != 0) continue;
                        int current = inv.getStock(entry.item());
                        int max = inv.getMaxStock(entry.item());
                        if (current < max) {
                            // Village-wide bonus (from gardens etc.) * per-instance orientation bonus.
                            double totalMultiplier = bonusMultiplier * building.getInstanceProductionMultiplier();
                            int boostedAmount = (int) Math.round(entry.amount() * totalMultiplier);
                            building.forceAdd(entry.item(), Math.min(boostedAmount, max - current));
                            townChanged = true;
                        }
                    }

                    if (def.isTransformer() && def.transformEveryTicks > 0
                            && gameTime % def.transformEveryTicks == 0) {
                        townChanged |= tickTransformer(building, def, inv);
                    }
                }

                // Dawn food consumption tick: fires once per in-game day at sunrise (tick 6000).
                if (gameTime % 24000 == 6000) {
                    float totalFoodDemandFloat = 0f;
                    int totalResidentsCounted = 0;
                    for (PlacedBuilding building : town.getBuildings()) {
                        BuildingDef def = BuildingDataHandler.get(building.getDefId()).orElse(null);
                        if (def == null) continue;
                        BuildingDef.ResolvedBuildingStats stats = def.resolveAtLevel(building.getUpgradeLevel());
                        int res = stats.resolvedResidents();
                        if (res > 0) {
                            totalResidentsCounted += res;
                            totalFoodDemandFloat += res * stats.resolvedConsumptionPerResident();
                        }
                    }
                    int foodUnitsToDrain = (int) Math.ceil(totalFoodDemandFloat);

                    int availableFoodUnits = 0;
                    for (Map.Entry<net.minecraft.world.item.Item, Integer> fEntry : FoodRegistry.entriesInOrder()) {
                        availableFoodUnits += inv.getStock(fEntry.getKey()) * fEntry.getValue();
                    }

                    int newActiveResidents;
                    if (foodUnitsToDrain == 0) {
                        newActiveResidents = totalResidentsCounted;
                    } else if (availableFoodUnits >= foodUnitsToDrain) {
                        newActiveResidents = totalResidentsCounted;
                    } else {
                        newActiveResidents = (int) Math.floor((float) availableFoodUnits * totalResidentsCounted / totalFoodDemandFloat);
                    }
                    newActiveResidents = Math.max(0, Math.min(totalResidentsCounted, newActiveResidents));

                    int toDrainUnits = Math.min(foodUnitsToDrain, availableFoodUnits);
                    if (toDrainUnits > 0) {
                        List<ItemCost> drainCosts = new ArrayList<>();
                        int remaining = toDrainUnits;
                        for (Map.Entry<net.minecraft.world.item.Item, Integer> fEntry : FoodRegistry.entriesInOrder()) {
                            if (remaining <= 0) break;
                            net.minecraft.world.item.Item foodItem = fEntry.getKey();
                            int fuv = fEntry.getValue();
                            int stock = inv.getStock(foodItem);
                            if (stock == 0) continue;
                            int unitsFromThis = stock * fuv;
                            if (unitsFromThis >= remaining) {
                                drainCosts.add(new ItemCost(foodItem, (int) Math.ceil((double) remaining / fuv)));
                                remaining = 0;
                            } else {
                                drainCosts.add(new ItemCost(foodItem, stock));
                                remaining -= unitsFromThis;
                            }
                        }
                        if (!drainCosts.isEmpty()) inv.removeStock(drainCosts);
                    }

                    town.setActiveResidents(newActiveResidents);
                    townChanged = true;
                }

                if (townChanged) {
                    anyChange = true;
                    long lastPush = lastUiPushTick.getOrDefault(townEntry.getKey(), 0L);
                    if (gameTime - lastPush >= UI_PUSH_COOLDOWN) {
                        NetworkHelper.pushHubToWatchers(level, town, anchorPos);
                        lastUiPushTick.put(townEntry.getKey(), gameTime);
                    }
                }

                // Quest generation: one attempt every 1200t (1 min)
                if (gameTime % 1200 == 0) {
                    Quest generated = QuestManager.tryGenerate(town.getActiveQuests(), town.getDismissedNoteIds(), gameTime);
                    if (generated != null) {
                        town.addQuest(generated);
                        anyChange = true;
                        NetworkHelper.pushHubToWatchers(level, town, anchorPos);
                    }
                }

                // Quest expiry check: every 100t
                if (gameTime % 100 == 0) {
                    List<Quest> toExpire = new ArrayList<>();
                    for (Quest q : town.getActiveQuests()) {
                        if (gameTime > q.expiryTime) toExpire.add(q);
                    }
                    if (!toExpire.isEmpty()) {
                        for (Quest q : toExpire) town.removeQuest(q.questId);
                        anyChange = true;
                        NetworkHelper.pushHubToWatchers(level, town, anchorPos);
                    }
                }

            } // end for (Town town)

            // Spawn builder for towns that have none, once a player is nearby
            if (gameTime % 20 == 0) {
                for (Map.Entry<Long, Town> entry : levelTowns.getAllTownEntries()) {
                    Town town = entry.getValue();
                    UUID builderId = town.getBuilderNpcId();
                    BlockPos anchorPos = BlockPos.of(entry.getKey());

                    // Only act when a player is close enough that the town chunk is loaded.
                    // Without this, getEntity() returns null for unloaded chunks and incorrectly
                    // triggers a respawn while the real builder still exists in an unloaded chunk.
                    boolean playerNearby = level.players().stream().anyMatch(p ->
                            p.distanceToSqr(anchorPos.getX() + 0.5, anchorPos.getY(), anchorPos.getZ() + 0.5) < 128.0 * 128.0);
                    if (!playerNearby) continue;

                    net.minecraft.world.entity.Entity existingEntity = builderId != null ? level.getEntity(builderId) : null;

                    // Skip if builder entity is already alive in the world
                    if (existingEntity != null) continue;

                    Npc builder = EntityRegistry.NPC.create(level);
                    if (builder == null) continue;

                    // Prevent Minecraft from naturally despawning the builder when players move away
                    builder.setPersistenceRequired();
                    builder.setTownAnchorPos(anchorPos);

                    int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            anchorPos.getX(), anchorPos.getZ());
                    builder.moveTo(anchorPos.getX() + 0.5, surfaceY + 1.0, anchorPos.getZ() + 0.5);

                    if (level.addFreshEntity(builder)) {
                        town.setBuilderNpcId(builder.getUUID());
                        anyChange = true;
                    }
                }
            }

            if (anyChange) levelTowns.markDirty();
        }
    }

    // Budget snapshot: for each distinct input item, take transform_input_ratio of current town stock.
    // Then loop through the recipe list repeatedly, crafting 1 unit per recipe per iteration,
    // until a full pass through the list produces nothing (budget exhausted or all outputs full).
    private static boolean tickTransformer(PlacedBuilding building, BuildingDef def,
                                           TownInventory inv) {
        int buildingLevel = building.getUpgradeLevel();
        // Snapshot budget: 10% of town stock per distinct input item across all active recipes
        Map<net.minecraft.world.item.Item, Integer> budget = new HashMap<>();
        for (TransformationRecipe recipe : def.transformations) {
            if (!recipe.isActive(buildingLevel)) continue;
            for (ItemCost input : recipe.inputs()) {
                budget.computeIfAbsent(input.item(), item -> (int)(inv.getStock(item) * def.transformInputRatio));
            }
        }

        Map<net.minecraft.world.item.Item, Integer> consumed = new HashMap<>();
        boolean anyProduced = false;
        boolean passProduced;
        do {
            passProduced = false;
            for (TransformationRecipe recipe : def.transformations) {
                if (!recipe.isActive(buildingLevel)) continue;
                boolean canAfford = recipe.inputs().stream()
                        .allMatch(input -> budget.getOrDefault(input.item(), 0) >= input.amount());
                int currentOutput = building.getStock(recipe.outputItem());
                if (!canAfford || currentOutput + recipe.outputAmount() > recipe.outputCapacityItems()) continue;
                for (ItemCost input : recipe.inputs()) {
                    budget.merge(input.item(), -input.amount(), Integer::sum);
                    consumed.merge(input.item(), input.amount(), Integer::sum);
                }
                building.forceAdd(recipe.outputItem(), recipe.outputAmount());
                passProduced = true;
                anyProduced = true;
            }
        } while (passProduced);

        if (!consumed.isEmpty()) {
            List<ItemCost> costs = new ArrayList<>();
            consumed.forEach((item, amount) -> costs.add(new ItemCost(item, amount)));
            inv.removeStock(costs);
        }

        return anyProduced;
    }
}
