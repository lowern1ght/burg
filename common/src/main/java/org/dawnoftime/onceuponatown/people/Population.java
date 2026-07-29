package org.dawnoftime.onceuponatown.people;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everyone a town has ever had, living and dead.
 *
 * <p>The dead are kept, and that is a decision rather than an oversight. A settlement whose
 * history can be read — who starved in the second winter, whose family this is — is the whole
 * point of giving people names; a roll that forgets is a counter with extra steps. They cost
 * almost nothing: a person is a handful of fields, and the town prunes only when the record
 * count would grow unbounded.
 *
 * <p>Insertion-ordered so that iteration is stable, because an unstable order makes a simulation
 * unreproducible and therefore untestable — the same seed and the same inputs must give the same
 * day. That is also why nothing in this package touches a random source it was not handed.
 *
 * <p>No Minecraft import, by rule. See {@link Person}.
 */
public final class Population {

    private final Map<UUID, Person> people = new LinkedHashMap<>();

    /** Days since the town was founded. The simulation's clock, and the only time it knows. */
    private int day = 0;

    public int day() { return day; }

    public void setDay(int day) { this.day = Math.max(0, day); }

    /** @return the person added, for chaining. Replaces any existing record with the same id. */
    public Person add(Person person) {
        people.put(person.id(), person);
        return person;
    }

    public Person get(UUID id) { return people.get(id); }

    public boolean contains(UUID id) { return people.containsKey(id); }

    /**
     * Removes a record entirely — emigration, not death.
     *
     * <p>Death is {@link Person#die}, which keeps the record. Use this only when somebody is
     * genuinely gone from the history as well as from the town.
     */
    public Person forget(UUID id) { return people.remove(id); }

    /** Everyone, dead included, in the order they joined. */
    public Collection<Person> all() { return Collections.unmodifiableCollection(people.values()); }

    public List<Person> living() {
        List<Person> out = new ArrayList<>();
        for (Person p : people.values()) if (p.alive()) out.add(p);
        return out;
    }

    public List<Person> adults() {
        List<Person> out = new ArrayList<>();
        for (Person p : people.values()) if (p.canWork()) out.add(p);
        return out;
    }

    public List<Person> children() {
        List<Person> out = new ArrayList<>();
        for (Person p : people.values()) if (p.alive() && p.isChild()) out.add(p);
        return out;
    }

    /** The employable who hold no trade. What a new workplace draws from. */
    public List<Person> idle() {
        List<Person> out = new ArrayList<>();
        for (Person p : people.values()) if (p.canWork() && !p.isEmployed()) out.add(p);
        return out;
    }

    public List<Person> withTrade(String trade) {
        List<Person> out = new ArrayList<>();
        for (Person p : people.values()) {
            if (p.alive() && trade.equals(p.trade())) out.add(p);
        }
        return out;
    }

    public int livingCount() {
        int n = 0;
        for (Person p : people.values()) if (p.alive()) n++;
        return n;
    }

    public int deadCount() { return people.size() - livingCount(); }

    /** Mouths to feed. Children eat too, which is the point of counting them separately. */
    public int mouths() { return livingCount(); }

    /**
     * How unhappy the town is, as the share of its living adults who are past the given line.
     *
     * <p>Not a mean. A revolution is not an average — twenty content people and ten furious ones
     * is a very different town from thirty mildly annoyed ones, and the mean cannot tell them
     * apart. Returns 0 for a town with nobody in it, so an empty settlement never reads as
     * seething.
     */
    public double unrest(int line) {
        int adults = 0, angry = 0;
        for (Person p : people.values()) {
            if (!p.canWork()) continue;
            adults++;
            if (p.discontent() >= line) angry++;
        }
        return adults == 0 ? 0.0 : (double) angry / adults;
    }

    /** Everything the town owns between them, in the smallest coin. */
    public long purseTotal() {
        long total = 0;
        for (Person p : people.values()) if (p.alive()) total += p.purse();
        return total;
    }

    /** How many living people read as each tier. Index is {@link Wealth#tier()}. */
    public int[] wealthHistogram() {
        int[] bins = new int[Wealth.values().length];
        for (Person p : people.values()) {
            if (p.alive()) bins[p.wealth().tier()]++;
        }
        return bins;
    }

    /**
     * Living people with nowhere to sleep.
     *
     * <p>Distinct from being homeless in the crowding sense: a town can have spare beds and still
     * have somebody unassigned, because assignment happens on a tick and arrivals do not wait for
     * it. This is the list that tick works through.
     */
    public List<Person> needingHomes() {
        List<Person> out = new ArrayList<>();
        for (Person p : people.values()) if (p.alive() && !p.hasHome()) out.add(p);
        return out;
    }

    /** How many living people are assigned to this home. */
    public int occupants(long homeKey) {
        int n = 0;
        for (Person p : people.values()) if (p.alive() && p.homeKey() == homeKey) n++;
        return n;
    }

    /** Living women old enough to bear a child. Births need one; that is the whole reason. */
    public List<Person> mothersAvailable() {
        List<Person> out = new ArrayList<>();
        for (Person p : people.values()) {
            if (p.canWork() && p.sex().isWoman() && p.ageDays() < Person.OLD_AT_DAYS) out.add(p);
        }
        return out;
    }

    public List<Person> fathersAvailable() {
        List<Person> out = new ArrayList<>();
        for (Person p : people.values()) {
            if (p.canWork() && !p.sex().isWoman() && p.ageDays() < Person.OLD_AT_DAYS) out.add(p);
        }
        return out;
    }

    @Override
    public String toString() {
        return "Population[day " + day + ", " + livingCount() + " living, "
            + deadCount() + " dead]";
    }
}
