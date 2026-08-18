package org.lowern1ght.burg.town;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.lowern1ght.burg.people.Departure;
import org.lowern1ght.burg.people.Person;
import org.lowern1ght.burg.people.Population;
import org.lowern1ght.burg.people.Sex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Translates a {@link Population} to and from NBT.
 *
 * <p><b>It lives here and not beside the model on purpose.</b> The {@code people} package has no
 * Minecraft import in it, which is the only reason a thousand simulated days can be a unit test;
 * putting {@code CompoundTag} in {@code Person} would cost exactly that. So the model stays pure
 * and this is the one place that knows both halves.
 *
 * <p>Fields are written by short key because two thousand people is two thousand compounds, and a
 * long key is paid for once per person per save. Absent keys read as the field's default, so a
 * save from an older version loads rather than throwing — a town that will not load is worse than
 * a town that loads with everybody at skill zero.
 */
public final class PopulationNbt {

    private static final Logger LOGGER = LoggerFactory.getLogger(PopulationNbt.class);

    private PopulationNbt() {
    }

    public static CompoundTag save(Population pop) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Day", pop.day());
        ListTag list = new ListTag();
        for (Person p : pop.all()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", p.id());
            t.putInt("sx", p.sex().index());
            t.putInt("ag", p.ageDays());
            if (p.trade() != null) t.putString("tr", p.trade());
            if (p.hasHome()) t.putLong("hm", p.homeKey());
            if (p.skill() > 0) t.putInt("sk", p.skill());
            if (p.purse() > 0) t.putInt("pu", p.purse());
            if (p.discontent() > 0) t.putInt("dc", p.discontent());
            if (p.hungryDays() > 0) t.putInt("hd", p.hungryDays());
            if (p.miserableDays() > 0) t.putInt("md", p.miserableDays());
            if (!p.alive()) {
                t.putBoolean("dead", true);
                t.putInt("dd", p.diedOnDay());
                if (p.departure() != null) t.putString("dep", p.departure().name());
            }
            list.add(t);
        }
        tag.put("People", list);
        return tag;
    }

    public static Population load(CompoundTag tag) {
        Population pop = new Population();
        pop.setDay(tag.getInt("Day"));
        ListTag list = tag.getList("People", Tag.TAG_COMPOUND);
        for (Tag raw : list) {
            CompoundTag t = (CompoundTag) raw;
            if (!t.hasUUID("id")) continue;      // nothing can be done with a person with no id
            try {
                Person p = new Person(t.getUUID("id"), Sex.byIndex(t.getInt("sx")), t.getInt("ag"));
                if (t.contains("tr")) p.setTrade(t.getString("tr"));
                p.setHomeKey(t.getLong("hm"));
                p.setSkill(t.getInt("sk"));
                p.setPurse(t.getInt("pu"));
                p.setDiscontent(t.getInt("dc"));
                p.setHungryDays(t.getInt("hd"));
                p.setMiserableDays(t.getInt("md"));
                if (t.getBoolean("dead")) {
                    Departure why = Departure.UNRECORDED;
                    if (t.contains("dep")) {
                        try {
                            why = Departure.valueOf(t.getString("dep"));
                        } catch (IllegalArgumentException ignored) {
                            // A reason this version does not know. The death still happened.
                        }
                    }
                    p.depart(t.getInt("dd"), why);
                }
                pop.add(p);
            } catch (Exception e) {
                // One unreadable person costs one person. The alternative is the failure mode
                // this repo already paid for once, where a single bad entry took every town in
                // the world with it.
                LOGGER.error("[OUAT-PEOPLE] a person failed to load and was dropped: {}",
                    e.getMessage());
            }
        }
        return pop;
    }
}
