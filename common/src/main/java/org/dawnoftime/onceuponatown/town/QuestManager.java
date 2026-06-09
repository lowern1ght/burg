package org.dawnoftime.onceuponatown.town;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.datapack.QuestDataHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class QuestManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestManager.class);

    // Swap QUEST_TTL to QUEST_TTL_PRODUCTION for release builds
    public static final long QUEST_TTL_DEBUG      = 3600L;  // 3 minutes (debug)
    public static final long QUEST_TTL_PRODUCTION = 30000L; // ~25 minutes (production)
    public static final long QUEST_TTL = QUEST_TTL_DEBUG;

    public static final int MAX_ACTIVE_QUESTS = 3;

    // Tries to generate one new quest. Returns null if at cap or no eligible defs.
    public static Quest tryGenerate(List<Quest> activeQuests, Set<String> dismissedNoteIds, long gameTime) {
        if (activeQuests.size() >= MAX_ACTIVE_QUESTS) return null;
        List<QuestDef> allDefs = new ArrayList<>(QuestDataHandler.getAll());
        if (allDefs.isEmpty()) return null;

        Set<String> activeDefIds = new HashSet<>();
        for (Quest q : activeQuests) activeDefIds.add(q.defId);

        List<QuestDef> eligible = new ArrayList<>();
        for (QuestDef def : allDefs) {
            if (activeDefIds.contains(def.id())) continue;
            if ("NOTE".equals(def.type()) && dismissedNoteIds.contains(def.id())) continue;
            eligible.add(def);
        }
        if (eligible.isEmpty()) return null;

        int totalWeight = 0;
        for (QuestDef def : eligible) totalWeight += Math.max(1, def.spawnWeight());
        int roll = (int)(Math.random() * totalWeight);
        QuestDef chosen = eligible.get(eligible.size() - 1);
        for (QuestDef def : eligible) {
            roll -= Math.max(1, def.spawnWeight());
            if (roll < 0) { chosen = def; break; }
        }

        Quest q = buildFromDef(chosen, gameTime);
        LOGGER.info("[OUAT] Generated quest {} ({}) weight={}", q.questId, q.defId, chosen.spawnWeight());
        return q;
    }

    private static Quest buildFromDef(QuestDef def, long gameTime) {
        Quest q = new Quest();
        q.questId = UUID.randomUUID().toString().substring(0, 8);
        q.defId = def.id();
        q.questType = def.type() != null ? def.type() : "TASK";
        long ttl = def.ttlTicks() > 0 ? def.ttlTicks() : QUEST_TTL;
        q.expiryTime = gameTime + ttl;
        for (QuestDef.ConditionTemplate ct : def.conditions()) {
            Quest.Condition c = new Quest.Condition();
            c.type = ct.type();
            if (ct.item() != null) c.item = BuiltInRegistries.ITEM.get(new ResourceLocation(ct.item()));
            c.required = ct.required();
            c.received = 0;
            q.conditions.add(c);
        }
        if (def.reward() != null) {
            Quest.Reward r = new Quest.Reward();
            r.type = def.reward().type();
            if (def.reward().item() != null) r.item = BuiltInRegistries.ITEM.get(new ResourceLocation(def.reward().item()));
            r.amount = def.reward().amount();
            q.reward = r;
        }
        return q;
    }
}
