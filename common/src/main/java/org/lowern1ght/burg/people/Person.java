package org.lowern1ght.burg.people;

import java.util.UUID;

/**
 * A person, as data. Not an entity.
 *
 * <p><b>This is the split that makes two thousand residents possible and the logic testable at
 * all.</b> Until now a resident WAS an {@code Npc} in the world, so the population could never
 * exceed what the server can pathfind — a vanilla village of thirty already strains modest
 * hardware, and our {@code Npc} carries its own node evaluator. Two thousand entities is not a
 * tuning problem, it is a wall.
 *
 * <p>The way past it is the one Frostpunk itself uses: its citizens are mostly numbers. You see a
 * few dozen figures; the thousands are a population with needs. So here the person is a record
 * that lives in the town's saved data, and a body is a temporary puppet lent to a record while a
 * player is close enough to see it. Two thousand records are a few hundred kilobytes of NBT.
 *
 * <p><b>And it is the only way the core gets under unit test.</b> An entity cannot be tested
 * without a running Minecraft, ever. This package has no Minecraft import in it, on purpose and
 * as a rule — so a thousand simulated days over two thousand people runs in a second in a plain
 * JVM, and the class of bug that this project has so far caught only by looking at the screen
 * becomes an assertion. Serialisation therefore lives OUTSIDE this package: an adapter in the
 * {@code town} package translates to and from {@code CompoundTag}, because importing it here
 * would cost the tests.
 *
 * <p>Mutable, deliberately. A record class would be tidier to read and would force a fresh object
 * per person per simulated day; at two thousand people over a long game that is a great deal of
 * garbage for no gain, since a person has exactly one owner — the {@link Population} holding it.
 */
public final class Person {

    /**
     * Identity, and the anchor of everything visible.
     *
     * <p>Name, face, build and clothing all derive from this id and not from the body's entity
     * UUID — which is the load-bearing part. A body is disposable; if the look were keyed to the
     * body, a person who walked out of range and back would come back as somebody else.
     */
    private final UUID id;

    private final Sex sex;

    /** Age in whole days lived. A newborn is 0. */
    private int ageDays;

    /** Trade id, or {@code null} for the unemployed — who are people too, and idle on purpose. */
    private String trade;

    /**
     * Where they sleep, as a packed block position, or 0 for nobody's tenant yet.
     *
     * <p>A {@code long} and not a {@code BlockPos}, and that is not squeamishness: this package
     * has no Minecraft import in it, which is the only reason a thousand simulated days can be a
     * unit test. The Minecraft side packs and unpacks with {@code BlockPos.asLong}; here it is
     * an opaque handle the simulation only ever compares for equality.
     */
    private long homeKey;

    /** 0 up to the trade config's cap. Earned by working, never by waiting. */
    private int skill;

    /**
     * What they own, in the smallest coin.
     *
     * <p>Nuggets, not ingots, because a single unit makes everything cheap cost the same. Visible:
     * {@link Wealth} maps it to how their clothes read, so the difference between a struggling
     * town and a prosperous one can be seen from a distance without a number on a screen.
     */
    private int purse;

    /**
     * How unhappy they are, 0 to 100.
     *
     * <p>Per person rather than one town-wide meter, because a revolution is not an average — it
     * starts with the people who have the most reason. Hunger, overwork and overcrowding raise
     * it; food, rest and a decent home lower it.
     */
    private int discontent;

    /**
     * Consecutive days with nothing to eat.
     *
     * <p>A counter rather than a single hungry flag, because starving has to take time. One bad
     * day should frighten a town, not empty it — and a settlement the player can rescue by
     * bringing food is only possible if there is a window between going short and dying.
     */
    private int hungryDays;

    /** True until they die. A dead person stays in the population so history can be read. */
    private boolean alive = true;

    /** Day they stopped being present, or {@code -1}. */
    private int diedOnDay = -1;

    /**
     * Why they are no longer here, or {@code null} while they still are.
     *
     * <p>Recorded rather than inferred so a town's history reads as a history: "twelve left in
     * the third winter" is a sentence, "population fell by twelve" is a number.
     */
    private Departure departure;

    public Person(UUID id, Sex sex, int ageDays) {
        if (id == null) throw new IllegalArgumentException("a person needs an id");
        if (sex == null) throw new IllegalArgumentException("a person needs a sex");
        if (ageDays < 0) throw new IllegalArgumentException("ageDays cannot be negative");
        this.id = id;
        this.sex = sex;
        this.ageDays = ageDays;
    }

    public UUID id()             { return id; }
    public Sex sex()             { return sex; }
    public int ageDays()         { return ageDays; }
    public String trade()        { return trade; }
    public int skill()           { return skill; }
    public int purse()           { return purse; }
    public int discontent()      { return discontent; }
    public boolean alive()       { return alive; }
    public int diedOnDay()       { return diedOnDay; }
    public Departure departure() { return departure; }

    /** How their clothes read. Derived, never stored, so it can never disagree with the purse. */
    public Wealth wealth()       { return Wealth.of(purse); }

    /** A child cannot work and is not counted as a mouth that earns. */
    public boolean isChild()     { return ageDays < ADULT_AT_DAYS; }

    /** Old enough to work, young enough to be worth employing. */
    public boolean canWork()     { return alive && !isChild(); }

    public boolean isEmployed()  { return trade != null; }

    public void setAgeDays(int days)   { this.ageDays = Math.max(0, days); }
    public void setTrade(String trade) { this.trade = trade; }
    public void setSkill(int skill)    { this.skill = Math.max(0, skill); }

    public void setPurse(int purse)    { this.purse = Math.max(0, purse); }
    public void earn(int coins)        { setPurse(purse + Math.max(0, coins)); }

    /** @return what was actually taken, which may be less than asked for. */
    public int spend(int coins) {
        int taken = Math.min(purse, Math.max(0, coins));
        purse -= taken;
        return taken;
    }

    public void setDiscontent(int value) {
        this.discontent = Math.min(100, Math.max(0, value));
    }

    public void addDiscontent(int delta) { setDiscontent(discontent + delta); }

    /** Consecutive days at or over the leaving threshold. The emigration clock. */
    private int miserableDays;

    public int miserableDays() { return miserableDays; }

    public void setMiserableDays(int days) { this.miserableDays = Math.max(0, days); }

    public long homeKey() { return homeKey; }

    public void setHomeKey(long key) { this.homeKey = key; }

    public boolean hasHome() { return homeKey != 0L; }

    public int hungryDays() { return hungryDays; }

    public void setHungryDays(int days) { this.hungryDays = Math.max(0, days); }

    /**
     * Dies, once.
     *
     * <p>Idempotent on purpose: two causes landing on the same person in the same day — starved
     * and killed — must not double-count the death, or the population's own arithmetic drifts
     * from the roll it is counting.
     */
    public void die(int onDay) {
        depart(onDay, Departure.UNRECORDED);
    }

    /**
     * Leaves the town, for the recorded reason. Once.
     *
     * <p>Idempotent on purpose: two causes landing on the same person on the same day — starved
     * and killed — must not count twice, or the population's arithmetic drifts from the roll it
     * is counting. The first cause is the one that happened.
     */
    public void depart(int onDay, Departure why) {
        if (!alive) return;
        alive = false;
        diedOnDay = onDay;
        departure = why == null ? Departure.UNRECORDED : why;
        trade = null;
    }

    /** Days lived before a person counts as an adult who can hold a trade. */
    public static final int ADULT_AT_DAYS = 16;

    /** Days lived after which a person may die of age; there is no hard ceiling. */
    public static final int OLD_AT_DAYS = 300;

    @Override
    public String toString() {
        return "Person[" + id + " " + sex + " " + ageDays + "d"
            + (trade == null ? " idle" : " " + trade + " s" + skill)
            + " " + purse + "c " + wealth() + " d" + discontent
            + (alive ? "" : " DEAD@" + diedOnDay) + "]";
    }
}
