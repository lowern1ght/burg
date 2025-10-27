package org.dawnoftime.onceuponatown.culture;

import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.item.Item;
import org.dawnoftime.onceuponatown.entity.ai.work.WorkAi;
import org.dawnoftime.onceuponatown.registry.ScheduleRegistry;
import org.dawnoftime.onceuponatown.trade.NpcOffer;
import oshi.util.tuples.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Profession {
    public static final Profession UNEMPLOYED = new Profession("unemployed", ScheduleRegistry.REGISTRY.DEFAULT_UNEMPLOYED.get(), WorkAi.EMPTY, new ArrayList<>());
    private final String id;
    private final Schedule schedule;
    private final WorkAi workAi;
    private List<Level> levels;

    public Profession(String id, Schedule schedule, WorkAi workAi, List<Level> levels) {
        this.id = id;
        this.schedule = schedule;
        this.workAi = workAi;
        this.levels = levels;
    }

    public String getId() {
        return id;
    }

    public WorkAi getWorkAi() {
        return workAi;
    }

    public List<Level> getLevels() {
        return levels;
    }

    public record Level(
        HashMap<Item, Pair<Integer, Integer>> production,
        List<Item> crafts,
        List<NpcOffer> trades
    ) {}
}
