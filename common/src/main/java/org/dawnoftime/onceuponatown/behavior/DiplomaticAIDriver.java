package org.dawnoftime.onceuponatown.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.dawnoftime.onceuponatown.behavior.diplomacy.DiplomaticAI;
import org.dawnoftime.onceuponatown.behavior.diplomacy.DiplomaticRegistry;
import org.dawnoftime.onceuponatown.behavior.diplomacy.DiplomaticStatus;
import org.dawnoftime.onceuponatown.behavior.morale.MoraleState;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Runs periodic diplomatic decisions for every pair of towns in a level. */
public final class DiplomaticAIDriver {

    private static final int DIPLOMACY_TICK_INTERVAL = 600;

    private final DiplomaticRegistry registry;
    private final DiplomaticAI ai;
    private final MoraleState morale;
    private int ticksSinceLastCheck = 0;

    public DiplomaticAIDriver(DiplomaticRegistry registry, DiplomaticAI ai, MoraleState morale) {
        this.registry = registry;
        this.ai = ai;
        this.morale = morale;
    }

    public void onServerTick(ServerLevel level) {
        ticksSinceLastCheck++;
        if (ticksSinceLastCheck < DIPLOMACY_TICK_INTERVAL) return;
        ticksSinceLastCheck = 0;

        List<Town> towns = new ArrayList<>(LevelTowns.get(level).getAllTowns());
        for (int i = 0; i < towns.size(); i++) {
            for (int j = i + 1; j < towns.size(); j++) {
                Town first = towns.get(i);
                Town second = towns.get(j);
                if (registry.between(first, second).status() == DiplomaticStatus.AT_WAR) continue;

                List<Npc> firstCitizens = citizensFor(level, first);
                List<Npc> secondCitizens = citizensFor(level, second);
                if (ai.shouldDeclareWar(morale, firstCitizens, secondCitizens)) {
                    registry.declareWar(first, second);
                } else if (ai.shouldDeclareWar(morale, secondCitizens, firstCitizens)) {
                    registry.declareWar(second, first);
                } else if (registry.between(first, second).status() == DiplomaticStatus.NEUTRAL
                    && ai.shouldProposeAlliance(morale, firstCitizens, secondCitizens)) {
                    registry.proposeAlliance(first, second);
                }
            }
        }
    }

    private static List<Npc> citizensFor(ServerLevel level, Town town) {
        Set<UUID> citizenIds = new LinkedHashSet<>(town.getResidentNpcIds());
        citizenIds.addAll(town.getBuilderNpcIds());
        List<Npc> citizens = new ArrayList<>();
        for (UUID citizenId : citizenIds) {
            if (citizenId == null) continue;
            Entity entity = level.getEntity(citizenId);
            if (entity instanceof Npc npc) citizens.add(npc);
        }
        return citizens;
    }
}
