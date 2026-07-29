package org.dawnoftime.onceuponatown.people;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Everything outside the population that a simulated day depends on, as plain values.
 *
 * <p><b>The seam that keeps the simulation testable.</b> {@link DaySim} could have read the town
 * directly — food off {@code TownInventory}, capacity off {@code getTotalResidents}, jobs off the
 * building list — and would then have needed a world, a server and a loaded chunk to run at all,
 * which is to say it could never have been unit-tested. Instead the Minecraft side takes one
 * snapshot per day and hands it over. A test builds the same snapshot by hand in three lines.
 *
 * <p>A snapshot is also a statement about what a day is allowed to know. It cannot see block
 * positions, entities or the weather, so no rule below can quietly come to depend on them.
 *
 * <p>Mutable via its builder-ish setters rather than a record, because the caller assembles it
 * from several sources — inventory, buildings, config — and a twelve-argument constructor is a
 * line nobody can read or safely reorder.
 */
public final class TownSnapshot {

    // --- what the town has -----------------------------------------------------------------

    private int foodUnits;
    private int housingCapacity;

    /** trade id -> how many people that trade can employ. */
    private final Map<String, Integer> tradeSlots = new LinkedHashMap<>();

    /** trade id -> coins a day at skill 0. Skill adds a share of it; see {@link #wageFor}. */
    private final Map<String, Integer> tradeWage = new LinkedHashMap<>();

    // --- the dials, with defaults that make an unconfigured town survivable ----------------

    private int foodPerAdult = 1;
    private int foodPerChild = 1;
    private int starveAfterDays = 3;
    private int maxSkill = 5;
    private int skillChancePercent = 20;
    /**
     * Chance per COUPLE per day, in per mille.
     *
     * <p>Per couple and not per town, which the scale test proved the hard way: a single roll for
     * the whole settlement is the right shape for a hamlet and dimensionally wrong for a city,
     * because a town of two thousand then grew no faster than a farmstead of six. 7 per mille
     * against a lifespan of {@link Person#OLD_AT_DAYS} is roughly replacement for a large town
     * and a birth every few weeks in a village — the same number reading correctly at both ends,
     * which is what a rate should do and a cap cannot.
     */
    private int birthChancePerMille = 7;
    private int ageDeathPerMille = 5;
    private double skillWageShare = 0.2;

    private int discontentPerHungryDay = 12;
    private int discontentFedRelief = 3;
    private int discontentPerCrowding = 4;
    private int discontentPerIdleDay = 1;

    /**
     * How far past its beds a town may swell before births stop entirely, as a multiple.
     *
     * <p>The control on homelessness, and deliberately soft. A hard "no bed, no child" cap means
     * homelessness can never happen at all, which also means overcrowding is never felt and the
     * player never has to race to build housing. Letting it swell without limit is the other
     * failure. So crowding SUPPRESSES the birth rate, smoothly, reaching zero at this multiple —
     * negative feedback rather than a wall.
     */
    private double crowdTolerance = 1.25;

    /** Discontent at or above which a person begins counting the days until they walk out. */
    private int leaveAtDiscontent = 80;

    /** Consecutive days that miserable before they go. */
    private int leaveAfterDays = 10;

    /** Extra discontent a day for having no bed at all. Worse than merely crowded. */
    private int discontentPerHomelessDay = 6;

    // --- reads -----------------------------------------------------------------------------

    public int foodUnits()              { return foodUnits; }
    public int housingCapacity()        { return housingCapacity; }
    public Set<String> trades()         { return Collections.unmodifiableSet(tradeSlots.keySet()); }
    public boolean tradeExists(String t){ return t != null && tradeSlots.containsKey(t); }
    public int slotsFor(String trade)   { return tradeSlots.getOrDefault(trade, 0); }

    public int foodPerAdult()           { return foodPerAdult; }
    public int foodPerChild()           { return foodPerChild; }
    public int starveAfterDays()        { return starveAfterDays; }
    public int maxSkill()               { return maxSkill; }
    public int skillChancePercent()     { return skillChancePercent; }
    public int birthChancePerMille()    { return birthChancePerMille; }
    public int ageDeathPerMille()       { return ageDeathPerMille; }

    public int discontentPerHungryDay() { return discontentPerHungryDay; }
    public int discontentFedRelief()    { return discontentFedRelief; }
    public int discontentPerCrowding()  { return discontentPerCrowding; }
    public int discontentPerIdleDay()   { return discontentPerIdleDay; }
    public double crowdTolerance()      { return crowdTolerance; }
    public int leaveAtDiscontent()      { return leaveAtDiscontent; }
    public int leaveAfterDays()         { return leaveAfterDays; }
    public int discontentPerHomelessDay() { return discontentPerHomelessDay; }

    /**
     * The birth rate this town actually has, after crowding.
     *
     * <p>Full rate while there are beds to spare, falling to zero as occupancy reaches {@link
     * #crowdTolerance} times capacity. That curve IS the control on homelessness: a town can
     * outgrow its housing and feel it, but the crowding chokes off the growth that caused it.
     */
    public int effectiveBirthPerMille(int livingCount) {
        if (housingCapacity <= 0) return 0;
        double occupancy = (double) livingCount / housingCapacity;
        if (occupancy <= 1.0) return birthChancePerMille;
        double room = (crowdTolerance - occupancy) / Math.max(1e-9, crowdTolerance - 1.0);
        return (int) Math.round(birthChancePerMille * Math.max(0.0, Math.min(1.0, room)));
    }

    /**
     * A day's pay for this trade at this skill.
     *
     * <p>Skill raises the wage as well as the output, so wealth stratifies by itself over a long
     * game rather than needing a rule that says "some people are rich". A master at
     * {@code skillWageShare} 0.2 and cap 5 earns double a novice, which is enough to move him a
     * tier or two up {@link Wealth} and therefore enough to see on his clothes.
     */
    public int wageFor(String trade, int skill) {
        int base = tradeWage.getOrDefault(trade, 0);
        if (base <= 0) return 0;
        return (int) Math.round(base * (1.0 + skillWageShare * skill));
    }

    // --- writes ----------------------------------------------------------------------------

    public TownSnapshot foodUnits(int v)          { this.foodUnits = Math.max(0, v); return this; }
    public TownSnapshot housingCapacity(int v)    { this.housingCapacity = Math.max(0, v); return this; }

    public TownSnapshot trade(String id, int slots, int wagePerDay) {
        tradeSlots.put(id, Math.max(0, slots));
        tradeWage.put(id, Math.max(0, wagePerDay));
        return this;
    }

    public TownSnapshot foodPerAdult(int v)           { this.foodPerAdult = Math.max(0, v); return this; }
    public TownSnapshot foodPerChild(int v)           { this.foodPerChild = Math.max(0, v); return this; }
    public TownSnapshot starveAfterDays(int v)        { this.starveAfterDays = Math.max(1, v); return this; }
    public TownSnapshot maxSkill(int v)               { this.maxSkill = Math.max(0, v); return this; }
    public TownSnapshot skillChancePercent(int v)     { this.skillChancePercent = clampPercent(v); return this; }
    public TownSnapshot birthChancePerMille(int v)    { this.birthChancePerMille = Math.min(1000, Math.max(0, v)); return this; }
    public TownSnapshot ageDeathPerMille(int v)       { this.ageDeathPerMille = Math.max(0, v); return this; }
    public TownSnapshot skillWageShare(double v)      { this.skillWageShare = Math.max(0.0, v); return this; }

    public TownSnapshot discontentPerHungryDay(int v) { this.discontentPerHungryDay = v; return this; }
    public TownSnapshot discontentFedRelief(int v)    { this.discontentFedRelief = v; return this; }
    public TownSnapshot discontentPerCrowding(int v)  { this.discontentPerCrowding = v; return this; }
    public TownSnapshot discontentPerIdleDay(int v)   { this.discontentPerIdleDay = v; return this; }
    public TownSnapshot crowdTolerance(double v)      { this.crowdTolerance = Math.max(1.0, v); return this; }
    public TownSnapshot leaveAtDiscontent(int v)      { this.leaveAtDiscontent = v; return this; }
    public TownSnapshot leaveAfterDays(int v)         { this.leaveAfterDays = Math.max(1, v); return this; }
    public TownSnapshot discontentPerHomelessDay(int v) { this.discontentPerHomelessDay = v; return this; }

    private static int clampPercent(int v) { return Math.min(100, Math.max(0, v)); }

    @Override
    public String toString() {
        return "TownSnapshot[food " + foodUnits + ", beds " + housingCapacity
            + ", trades " + tradeSlots + "]";
    }
}
