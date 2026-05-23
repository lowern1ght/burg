package org.dawnoftime.onceuponatown.tick;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.ItemCost;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.PlacedBuilding;
import org.dawnoftime.onceuponatown.town.ProductionEntry;
import org.dawnoftime.onceuponatown.town.Town;
import org.dawnoftime.onceuponatown.town.TownInventory;
import org.dawnoftime.onceuponatown.town.TransformationRecipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TickScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TickScheduler.class);

    // Called from OuatForge via TickEvent.ServerTickEvent (Phase.END)
    public static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            LevelTowns levelTowns = LevelTowns.get(level);
            long gameTime = level.getGameTime();
            boolean anyChange = false;

            for (Town town : levelTowns.getAllTowns()) {
                TownInventory inv = town.getTownInventory();

                // Sum additive production bonus from all buildings in this town (e.g. gardens: +3% each).
                double bonusMultiplier = 1.0 + town.getBuildings().stream()
                        .mapToDouble(b -> BuildingDataHandler.get(b.getDefId())
                                .map(d -> d.productionBonus).orElse(0.0))
                        .sum();

                for (PlacedBuilding building : town.getBuildings()) {
                    BuildingDef def = BuildingDataHandler.get(building.getDefId()).orElse(null);
                    if (def == null) continue;
                    for (ProductionEntry entry : def.production) {
                        if (entry.everyTicks() <= 0 || gameTime % entry.everyTicks() != 0) continue;
                        int current = inv.getStock(entry.item());
                        int max = inv.getMaxStock(entry.item());
                        if (current < max) {
                            // Village-wide bonus (from gardens etc.) * per-instance orientation bonus.
                            double totalMultiplier = bonusMultiplier * building.getInstanceProductionMultiplier();
                            int boostedAmount = (int) Math.round(entry.amount() * totalMultiplier);
                            building.forceAdd(entry.item(), Math.min(boostedAmount, max - current));
                            anyChange = true;

                        }
                    }

                    if (def.isTransformer() && def.transformEveryTicks > 0
                            && gameTime % def.transformEveryTicks == 0) {
                        anyChange |= tickTransformer(building, def, inv);
                    }
                }
            }

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

                    // DEBUG: log the state of the builder check once per minute (1200t) to avoid spam
                    net.minecraft.world.entity.Entity existingEntity = builderId != null ? level.getEntity(builderId) : null;
                    if (gameTime % 1200 == 0) {
                        LOGGER.info("[OUAT-DEBUG] Town='{}' anchor={} builderId={} entityFound={} playerNearby=true",
                                town.getName(), anchorPos, builderId, existingEntity != null);
                    }

                    // Skip if builder entity is already alive in the world
                    if (existingEntity != null) continue;

                    // DEBUG: warn explicitly when a respawn is triggered despite a stored builderId
                    if (builderId != null) {
                        LOGGER.warn("[OUAT-DEBUG] RESPAWN TRIGGERED for town='{}' -- stored builderId={} not found in level (chunk may have just loaded or entity was lost)",
                                town.getName(), builderId);
                    }

                    Npc builder = EntityRegistry.NPC.create(level);
                    if (builder == null) continue;

                    // Prevent Minecraft from naturally despawning the builder when players move away
                    builder.setPersistenceRequired();

                    int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            anchorPos.getX(), anchorPos.getZ());
                    builder.moveTo(anchorPos.getX() + 0.5, surfaceY + 1.0, anchorPos.getZ() + 0.5);

                    if (level.addFreshEntity(builder)) {
                        LOGGER.warn("[OUAT-DEBUG] NEW NPC SPAWNED uuid={} for town='{}' (replaced old builderId={})",
                                builder.getUUID(), town.getName(), builderId);
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
        // Snapshot budget: 10% of town stock per distinct input item across all recipes
        Map<net.minecraft.world.item.Item, Integer> budget = new HashMap<>();
        for (TransformationRecipe recipe : def.transformations) {
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
