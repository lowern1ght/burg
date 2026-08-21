package org.lowern1ght.burg.town;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public class QuestManager {

    // Builds and returns a Quest instance for the given def. Always succeeds.
    public static Quest buildFromDef(QuestDef def) {
        Quest q = new Quest();
        q.questId = UUID.randomUUID().toString().substring(0, 8);
        q.defId = def.id();
        q.questType = def.type() != null ? def.type() : "TASK";
        for (QuestDef.ConditionTemplate ct : def.conditions()) {
            Quest.Condition c = new Quest.Condition();
            c.type = ct.type();
            if (ct.item() != null) c.item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(ct.item()));
            c.required = ct.required();
            c.sendToStock = ct.sendToStock();
            q.conditions.add(c);
        }
        if (def.reward() != null) {
            Quest.Reward r = new Quest.Reward();
            r.type = def.reward().type();
            if (def.reward().item() != null) r.item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(def.reward().item()));
            r.amount = def.reward().amount();
            q.reward = r;
        }
        return q;
    }

    // ADR-0029 — engine tick reads through {@link Town#findQuestDef} instead
    // of scanning the legacy {@code getActiveQuests()} list. The engine
    // primary key is defId, so the defId-keyed port returns the same answer
    // as the old linear scan in O(1) and the call site no longer needs to
    // thread the active-quest list through.
    public static boolean isAlreadyActive(Town town, String defId) {
        return town.findQuestDef(defId).isPresent();
    }
}
