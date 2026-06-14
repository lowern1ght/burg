package org.dawnoftime.onceuponatown.town;

import java.util.List;

public record QuestDef(
    String id,
    String type,
    String icon,
    String titleKey,
    String descKey,
    List<ConditionTemplate> conditions,
    RewardTemplate reward,
    int spawnWeight,
    long durationTicks
) {
    public record ConditionTemplate(String type, String item, int required) {}
    public record RewardTemplate(String type, String item, int amount) {}
}
