package org.dawnoftime.onceuponatown.people;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * One day in a town, as a function.
 *
 * <p>Takes a {@link Population}, a {@link TownSnapshot} of everything outside it that matters, and
 * a {@link Random} it is handed rather than one it makes. Returns an {@link Outcome} describing
 * what happened. Reads no world, touches no entity, imports nothing from Minecraft — which is
 * what lets a thousand days over two thousand people run in under a second in a plain JVM, and
 * what lets every rule below be an assertion instead of something you go and look at.
 *
 * <p><b>The random source is a parameter for the same reason.</b> Seed it and the same inputs give
 * the same day, every time, so a failing case is a failing case and not a mood.
 *
 * <p>Order within the day is deliberate and is the part most likely to be got wrong quietly:
 * <b>age, then work, then eat, then feel, then be born, then die.</b> Work before food, so that a
 * day's labour is paid for even by somebody who then starves — otherwise a famine erases the work
 * that might have ended it. Birth before death, so a mother who dies on the day she gives birth
 * leaves a living child rather than none.
 */
public final class DaySim {

    private DaySim() {
    }

    /** What one day did. Everything a caller might want to announce, log or react to. */
    public static final class Outcome {
        public final List<UUID> born = new ArrayList<>();
        public final List<UUID> starved = new ArrayList<>();
        public final List<UUID> diedOfAge = new ArrayList<>();
        public final List<UUID> tookTrade = new ArrayList<>();
        public final List<UUID> lostTrade = new ArrayList<>();
        /** Walked out because they had been miserable too long. The control on overcrowding. */
        public final List<UUID> left = new ArrayList<>();
        /** Living people with no bed at all. Zero in a town that keeps up with its own growth. */
        public int homeless;
        /** Food units actually eaten. Less than demand means somebody went short. */
        public int foodEaten;
        /** Mouths that got nothing at all. */
        public int wentHungry;
        /** Coins paid out in wages this day. */
        public int wagesPaid;

        public boolean anythingHappened() {
            return !born.isEmpty() || !starved.isEmpty() || !diedOfAge.isEmpty()
                || !tookTrade.isEmpty() || !lostTrade.isEmpty();
        }

        @Override
        public String toString() {
            return "Outcome[born " + born.size() + ", starved " + starved.size()
                + ", aged out " + diedOfAge.size() + ", left " + left.size()
                + ", hired " + tookTrade.size() + ", hungry " + wentHungry
                + ", homeless " + homeless + ", wages " + wagesPaid + "]";
        }
    }

    /**
     * Advance one day.
     *
     * @param pop      the town's people; mutated in place
     * @param town     everything outside the population that the day depends on
     * @param rng      handed in, never created here, so a run is reproducible
     */
    public static Outcome tickDay(Population pop, TownSnapshot town, Random rng) {
        Outcome out = new Outcome();
        pop.setDay(pop.day() + 1);
        int today = pop.day();

        // --- age ----------------------------------------------------------------------------
        for (Person p : pop.living()) {
            p.setAgeDays(p.ageDays() + 1);
        }

        // --- work ---------------------------------------------------------------------------
        // Before food on purpose: a day's labour is paid even by somebody who then starves, or a
        // famine erases the very work that could have ended it.
        assignTrades(pop, town, out);
        for (Person p : pop.living()) {
            if (!p.isEmployed()) continue;
            int wage = town.wageFor(p.trade(), p.skill());
            if (wage > 0) {
                p.earn(wage);
                out.wagesPaid += wage;
            }
            if (p.skill() < town.maxSkill() && rng.nextInt(100) < town.skillChancePercent()) {
                p.setSkill(p.skill() + 1);
            }
        }

        // --- eat ----------------------------------------------------------------------------
        // The employed eat first, then children, then the idle. Not a moral claim: a town that
        // starves its workers first loses the ability to feed anyone, and this is the ordering
        // that makes recovery possible at all.
        List<Person> queue = new ArrayList<>();
        for (Person p : pop.living()) if (p.isEmployed()) queue.add(p);
        for (Person p : pop.living()) if (p.isChild()) queue.add(p);
        for (Person p : pop.living()) if (!p.isEmployed() && !p.isChild()) queue.add(p);

        int food = town.foodUnits();
        for (Person p : queue) {
            int need = p.isChild() ? town.foodPerChild() : town.foodPerAdult();
            if (food >= need) {
                food -= need;
                out.foodEaten += need;
                p.addDiscontent(-town.discontentFedRelief());
                p.setHungryDays(0);
            } else {
                out.wentHungry++;
                p.addDiscontent(town.discontentPerHungryDay());
                p.setHungryDays(p.hungryDays() + 1);
                if (p.hungryDays() >= town.starveAfterDays()) {
                    p.die(today);
                    out.starved.add(p.id());
                }
            }
        }

        // --- feel ---------------------------------------------------------------------------
        int overcrowd = Math.max(0, pop.livingCount() - town.housingCapacity());
        out.homeless = overcrowd;
        List<Person> here = pop.living();
        for (int i = 0; i < here.size(); i++) {
            Person p = here.get(i);
            if (overcrowd > 0) p.addDiscontent(town.discontentPerCrowding());
            // The last arrivals are the ones with no bed. Worse than merely crowded, and it is
            // the pressure that makes them leave, which is what stops homelessness growing.
            if (i >= town.housingCapacity()) p.addDiscontent(town.discontentPerHomelessDay());
            // Idleness grates on an adult who wants work and cannot get it. A child idling is a
            // child.
            if (p.canWork() && !p.isEmployed()) p.addDiscontent(town.discontentPerIdleDay());
        }

        // --- leave --------------------------------------------------------------------------
        // The self-limiting control, and the honest one. A hard "no bed, no child" cap means
        // homelessness can never happen and therefore crowding is never felt; no cap at all
        // means misery accumulates forever. So: crowding chokes the birth rate (below), and
        // anybody miserable for long enough walks out. The player sees "people are leaving",
        // which names its own fix, instead of births silently refusing for no visible reason.
        for (Person p : pop.living()) {
            if (p.isChild()) continue;         // a child does not emigrate alone
            if (p.discontent() >= town.leaveAtDiscontent()) {
                p.setMiserableDays(p.miserableDays() + 1);
                if (p.miserableDays() >= town.leaveAfterDays()) {
                    p.depart(today, Departure.LEFT);
                    out.left.add(p.id());
                }
            } else {
                p.setMiserableDays(0);
            }
        }

        // --- be born ------------------------------------------------------------------------
        // Needs a woman and a man, both grown, and a bed going spare. Not food thrown at two
        // villagers: that was vanilla's rule and the owner ruled it out explicitly.
        List<Person> mothers = pop.mothersAvailable();
        List<Person> fathers = pop.fathersAvailable();
        int rate = town.effectiveBirthPerMille(pop.livingCount());
        if (!mothers.isEmpty() && !fathers.isEmpty() && rate > 0 && out.wentHungry == 0) {
            // Per COUPLE per day, not per town per day. The first version rolled once for the
            // whole settlement and stopped at one birth, which is the right shape for a hamlet
            // and dimensionally wrong for a town: a city of two thousand then grew no faster
            // than a farmstead of six. The scale test caught it in 178ms — 2000 people fell to
            // 341 over a thousand days, because replacing a population that dies of age around
            // day 300 needs about SEVEN births a day and the cap allowed one.
            int chances = Math.min(mothers.size(), fathers.size());
            for (int i = 0; i < chances; i++) {
                if (rng.nextInt(1000) >= rate) continue;
                Person child = new Person(
                    new UUID(rng.nextLong(), rng.nextLong()),
                    rng.nextBoolean() ? Sex.WOMAN : Sex.MAN,
                    0);
                pop.add(child);
                out.born.add(child.id());
            }
        }

        // --- die of age ---------------------------------------------------------------------
        // After birth, so a mother who dies the day she bears leaves a living child.
        for (Person p : pop.living()) {
            if (p.ageDays() < Person.OLD_AT_DAYS) continue;
            // A fifth of a per-mille per day past old age, not a whole one. The first version
            // added one, which reaches 10% a day by day 400 and means nobody is ever seen old:
            // the founding cohort then dies as a WAVE and the town oscillates on it forever.
            int over = (p.ageDays() - Person.OLD_AT_DAYS) / 5;
            if (rng.nextInt(1000) < Math.min(1000, town.ageDeathPerMille() + over)) {
                p.die(today);
                out.diedOfAge.add(p.id());
            }
        }

        return out;
    }

    /**
     * Fill vacant trades from the idle, nearest thing first.
     *
     * <p>Also releases a trade whose workplace has gone, which is the other half and the one that
     * would rot silently: a person holding a job at a building that burned down is a job nobody
     * else can take.
     */
    private static void assignTrades(Population pop, TownSnapshot town, Outcome out) {
        for (Person p : pop.living()) {
            if (p.isEmployed() && !town.tradeExists(p.trade())) {
                p.setTrade(null);
                out.lostTrade.add(p.id());
            }
        }
        for (String trade : town.trades()) {
            int slots = town.slotsFor(trade);
            int held = pop.withTrade(trade).size();
            for (int i = held; i < slots; i++) {
                List<Person> idle = pop.idle();
                if (idle.isEmpty()) return;
                Person taker = idle.get(0);
                taker.setTrade(trade);
                out.tookTrade.add(taker.id());
            }
        }
    }
}
