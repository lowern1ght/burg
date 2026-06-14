package org.dawnoftime.onceuponatown.tick;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import net.minecraft.world.level.levelgen.Heightmap;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Quest;
import org.dawnoftime.onceuponatown.town.QuestManager;
import org.dawnoftime.onceuponatown.town.Town;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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

            if (gameTime % 1200 == 0) {
                LOGGER.info("[OUAT-TICK] Scheduler alive. Towns in level {}: {}",
                    level.dimension().location(), levelTowns.getAllTowns().size());
            }

            for (Map.Entry<Long, Town> townEntry : levelTowns.getAllTownEntries()) {
                Town town = townEntry.getValue();
                long anchorKey = townEntry.getKey();

                ProductionManager.tick(town, level, gameTime, anchorKey);
                FoodManager.tick(town, level, gameTime, anchorKey);
                tickQuests(town, level, gameTime, anchorKey);
                EraManager.tick(town, level, gameTime, anchorKey);
            }

            // Spawn builders for each slot up to targetBuilderCount, once a player is nearby.
            if (gameTime % 20 == 0) {
                for (Map.Entry<Long, Town> entry : levelTowns.getAllTownEntries()) {
                    Town town = entry.getValue();
                    BlockPos anchorPos = BlockPos.of(entry.getKey());

                    boolean playerNearby = level.players().stream().anyMatch(p ->
                        p.distanceToSqr(anchorPos.getX() + 0.5, anchorPos.getY(), anchorPos.getZ() + 0.5) < 128.0 * 128.0);
                    if (!playerNearby) continue;

                    boolean dirty = false;
                    List<UUID> ids = town.getBuilderNpcIds();
                    for (int slot = 0; slot < town.getTargetBuilderCount(); slot++) {
                        UUID slotId = slot < ids.size() ? ids.get(slot) : null;
                        net.minecraft.world.entity.Entity existing = slotId != null ? level.getEntity(slotId) : null;
                        if (existing != null) continue;

                        Npc builder = EntityRegistry.NPC.create(level);
                        if (builder == null) continue;

                        builder.setPersistenceRequired();
                        builder.setTownAnchorPos(anchorPos);

                        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            anchorPos.getX(), anchorPos.getZ());
                        builder.moveTo(anchorPos.getX() + 0.5, surfaceY + 1.0, anchorPos.getZ() + 0.5);

                        if (level.addFreshEntity(builder)) {
                            town.setBuilderNpcIdAtSlot(slot, builder.getUUID());
                            dirty = true;
                        }
                    }
                    if (dirty) levelTowns.markDirty();
                }
            }
        }
    }

    private static void tickQuests(Town town, ServerLevel level, long gameTime, long anchorKey) {
        BlockPos anchorPos = BlockPos.of(anchorKey);
        boolean changed = false;

        // Quest generation: one attempt every 1200t (1 min)
        if (gameTime % 1200 == 0) {
            Quest generated = QuestManager.tryGenerate(town.getActiveQuests(), town.getDismissedNoteIds(), gameTime);
            if (generated != null) {
                town.addQuest(generated);
                changed = true;
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
                changed = true;
            }
        }

        if (changed) {
            LevelTowns.get(level).markDirty();
            NetworkHelper.pushQuestUpdateToWatchers(level, town, anchorPos);
        }
    }
}
