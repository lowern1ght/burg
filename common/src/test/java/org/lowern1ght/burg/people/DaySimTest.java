package org.lowern1ght.burg.people;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The town, simulated without a Minecraft.
 *
 * <p>Every assertion here is a rule that this project has so far been able to check only by
 * loading a world and looking, which is why several of them were wrong for a while and nobody
 * knew. A thousand days over two thousand people runs in under a second, so these are cheap
 * enough to run on every build.
 *
 * <p>The seed is fixed and the {@link Random} is handed to the simulation rather than made by it,
 * so a failure is reproducible.
 */
class DaySimTest {

    private static Random seeded() {
        return new Random(20260729L);
    }

    private static Population town(int adults, int beds) {
        Population pop = new Population();
        for (int i = 0; i < adults; i++) {
            pop.add(new Person(new UUID(1, i), i % 2 == 0 ? Sex.MAN : Sex.WOMAN,
                Person.ADULT_AT_DAYS + 20));
        }
        return pop;
    }

    private static TownSnapshot fedTown(int beds) {
        return new TownSnapshot().housingCapacity(beds).foodUnits(10_000);
    }

    // --- food -------------------------------------------------------------------------------

    @Test
    @DisplayName("a fed town with room to spare grows, and buries nobody")
    void fedTownGrowsAndBuriesNobody() {
        // Written first as "loses nobody", which stopped being true the moment people could
        // leave: a fed town BREEDS, so with only just enough beds it outgrows them and then
        // sheds the surplus. That is the intended behaviour, so the test says what it means --
        // no deaths, and growth -- and gives the town room to grow into.
        Population pop = town(10, 40);
        TownSnapshot snap = fedTown(40);
        Random rng = seeded();
        int starved = 0, aged = 0;
        for (int d = 0; d < 200; d++) {
            DaySim.Outcome out = DaySim.tickDay(pop, snap, rng);
            starved += out.starved.size();
            aged += out.diedOfAge.size();
        }
        final int s1 = starved, a1 = aged;
        assertAll(
            () -> assertEquals(0, s1, "nobody starves with a full store"),
            () -> assertEquals(0, a1, "and nobody is old enough to die yet"),
            () -> assertTrue(pop.livingCount() > 10,
                "ten fed adults with thirty spare beds have to become more than ten")
        );
    }

    @Test
    @DisplayName("starving takes days, so a town can be rescued")
    void starvingTakesDays() {
        Population pop = town(4, 4);
        TownSnapshot snap = new TownSnapshot().housingCapacity(4).foodUnits(0).starveAfterDays(3);
        Random rng = seeded();

        DaySim.Outcome first = DaySim.tickDay(pop, snap, rng);
        assertAll(
            () -> assertEquals(4, first.wentHungry, "everyone goes short on day one"),
            () -> assertTrue(first.starved.isEmpty(), "nobody dies on the first hungry day")
        );

        DaySim.tickDay(pop, snap, rng);
        assertEquals(0, pop.deadCount(), "nor on the second");

        DaySim.Outcome third = DaySim.tickDay(pop, snap, rng);
        assertEquals(4, third.starved.size(), "the third hungry day is fatal");
    }

    @Test
    @DisplayName("food arriving resets the clock on starving")
    void foodResetsTheStarveClock() {
        Population pop = town(2, 2);
        TownSnapshot empty = new TownSnapshot().housingCapacity(2).foodUnits(0).starveAfterDays(3);
        TownSnapshot full = fedTown(2);
        Random rng = seeded();

        DaySim.tickDay(pop, empty, rng);
        DaySim.tickDay(pop, empty, rng);
        DaySim.tickDay(pop, full, rng);      // the player brings bread
        DaySim.tickDay(pop, empty, rng);
        DaySim.tickDay(pop, empty, rng);
        assertEquals(0, pop.deadCount(),
            "two hungry days, a meal, then two more must not kill: the counter has to reset");
    }

    @Test
    @DisplayName("a starving town feeds its workers before its idle")
    void workersEatFirst() {
        Population pop = new Population();
        Person worker = pop.add(new Person(new UUID(2, 1), Sex.MAN, 40));
        Person loafer = pop.add(new Person(new UUID(2, 2), Sex.MAN, 40));
        // Only one ration in the store, and one job to hold.
        TownSnapshot snap = new TownSnapshot().housingCapacity(4).foodUnits(1)
            .trade("smith", 1, 4);
        DaySim.tickDay(pop, snap, seeded());

        Person employed = worker.isEmployed() ? worker : loafer;
        Person unemployed = worker.isEmployed() ? loafer : worker;
        assertAll(
            () -> assertEquals(0, employed.hungryDays(), "the one holding the trade ate"),
            () -> assertEquals(1, unemployed.hungryDays(), "the idle one went short")
        );
    }

    // --- work and wealth --------------------------------------------------------------------

    @Test
    @DisplayName("a trade is taken, and released when its workplace goes")
    void tradesAreTakenAndReleased() {
        Population pop = town(3, 4);
        TownSnapshot withJobs = fedTown(4).trade("baker", 2, 3);
        DaySim.Outcome hired = DaySim.tickDay(pop, withJobs, seeded());
        assertEquals(2, hired.tookTrade.size(), "two slots, two takers");
        assertEquals(2, pop.withTrade("baker").size());

        // The bakery burns down.
        DaySim.Outcome lost = DaySim.tickDay(pop, fedTown(4), seeded());
        assertAll(
            () -> assertEquals(2, lost.lostTrade.size(), "a trade with no workplace is released"),
            () -> assertEquals(0, pop.withTrade("baker").size()),
            () -> assertEquals(3, pop.idle().size(), "and they are available again")
        );
    }

    @Test
    @DisplayName("skill rises by working and stops at the cap")
    void skillRisesAndCaps() {
        Population pop = town(1, 2);
        TownSnapshot snap = fedTown(2).trade("mason", 1, 2).maxSkill(5).skillChancePercent(100);
        Random rng = seeded();
        for (int d = 0; d < 50; d++) DaySim.tickDay(pop, snap, rng);
        assertEquals(5, pop.living().get(0).skill(), "fifty days at 100% must cap, not overflow");
    }

    @Test
    @DisplayName("a master out-earns a novice, so wealth stratifies without a rule saying so")
    void skillRaisesTheWage() {
        TownSnapshot snap = new TownSnapshot().trade("smith", 1, 10).skillWageShare(0.2);
        assertAll(
            () -> assertEquals(10, snap.wageFor("smith", 0)),
            () -> assertEquals(20, snap.wageFor("smith", 5)),
            () -> assertEquals(0, snap.wageFor("nobody", 3), "an unknown trade pays nothing")
        );
    }

    @Test
    @DisplayName("wages move a person up the visible wealth tiers")
    void wagesReachTheClothes() {
        Population pop = town(1, 2);
        TownSnapshot snap = fedTown(2).trade("smith", 1, 8);
        Person p = pop.living().get(0);
        assertEquals(Wealth.DESTITUTE, p.wealth(), "starts with nothing");

        Random rng = seeded();
        for (int d = 0; d < 200; d++) DaySim.tickDay(pop, snap, rng);
        assertTrue(p.wealth().tier() > Wealth.DESTITUTE.tier(),
            "two hundred days of paid work has to show on his clothes, or the axis is a lie");
    }

    // --- births -----------------------------------------------------------------------------

    @Test
    @DisplayName("a birth needs a woman, a man and a spare bed")
    void birthNeedsBothSexesAndABed() {
        Random rng = seeded();

        Population menOnly = new Population();
        for (int i = 0; i < 4; i++) menOnly.add(new Person(new UUID(3, i), Sex.MAN, 40));
        TownSnapshot roomy = fedTown(20).birthChancePerMille(1000);
        for (int d = 0; d < 30; d++) DaySim.tickDay(menOnly, roomy, rng);
        assertEquals(4, menOnly.livingCount(), "a town of men has no second generation");

        Population mixed = new Population();
        mixed.add(new Person(new UUID(4, 1), Sex.MAN, 40));
        mixed.add(new Person(new UUID(4, 2), Sex.WOMAN, 40));
        DaySim.tickDay(mixed, roomy, rng);
        assertEquals(3, mixed.livingCount(), "a man, a woman and a spare bed is a child");
    }

    @Test
    @DisplayName("crowding chokes births, so homelessness cannot run away")
    void crowdingChokesBirths() {
        TownSnapshot t = new TownSnapshot().housingCapacity(100).birthChancePerMille(100)
            .crowdTolerance(1.25);
        assertAll(
            () -> assertEquals(100, t.effectiveBirthPerMille(50), "room to spare: full rate"),
            () -> assertEquals(100, t.effectiveBirthPerMille(100), "exactly full: still full rate"),
            () -> assertEquals(60, t.effectiveBirthPerMille(110), "two fifths over: three fifths rate"),
            () -> assertEquals(20, t.effectiveBirthPerMille(120), "four fifths over: a fifth left"),
            () -> assertEquals(0, t.effectiveBirthPerMille(125), "at tolerance: no births"),
            () -> assertEquals(0, t.effectiveBirthPerMille(400), "and none beyond it"),
            () -> assertEquals(0, new TownSnapshot().housingCapacity(0).effectiveBirthPerMille(0),
                "a town with no beds at all has no children")
        );
    }

    @Test
    @DisplayName("HOMELESSNESS DOES NOT GROW WITHOUT BOUND — the owner's requirement, asserted")
    void homelessnessIsSelfLimiting() {
        // A town with far too few beds and every incentive to breed. If the controls work, it
        // settles somewhere just over capacity instead of climbing for a thousand days.
        Population pop = new Population();
        for (int i = 0; i < 40; i++) {
            pop.add(new Person(new UUID(11, i), i % 2 == 0 ? Sex.MAN : Sex.WOMAN,
                Person.ADULT_AT_DAYS + (i % 120)));
        }
        TownSnapshot cramped = fedTown(30).birthChancePerMille(200).crowdTolerance(1.25);

        Random rng = seeded();
        int worstHomeless = 0, leftTotal = 0;
        for (int d = 0; d < 1000; d++) {
            DaySim.Outcome out = DaySim.tickDay(pop, cramped, rng);
            worstHomeless = Math.max(worstHomeless, out.homeless);
            leftTotal += out.left.size();
        }

        final int capacity = cramped.housingCapacity();
        final int worst = worstHomeless;
        final int leavers = leftTotal;
        assertAll(
            () -> assertTrue(pop.livingCount() <= Math.ceil(capacity * cramped.crowdTolerance()),
                "living " + pop.livingCount() + " must settle at or under the crowd tolerance "
                + "of " + Math.ceil(capacity * cramped.crowdTolerance()) + ", not climb forever"),
            () -> assertTrue(worst <= capacity,
                "worst homelessness was " + worst + "; it must never exceed the town's "
                + "own size, or the control has failed"),
            () -> assertTrue(leavers > 0,
                "somebody has to have walked out, or the pressure never released and the cap is "
                + "doing the work instead of the feedback")
        );
        System.out.println("cramped town: " + pop + ", worst homeless " + worstHomeless
            + ", left " + leftTotal);
    }

    @Test
    @DisplayName("a miserable person leaves, and only after being miserable a while")
    void miseryMakesPeopleLeave() {
        Population pop = new Population();
        Person p = pop.add(new Person(new UUID(12, 1), Sex.MAN, 40));
        p.setDiscontent(95);
        TownSnapshot snap = fedTown(4).leaveAtDiscontent(80).leaveAfterDays(5)
            .discontentFedRelief(0);
        Random rng = seeded();

        for (int d = 0; d < 4; d++) DaySim.tickDay(pop, snap, rng);
        assertTrue(p.alive(), "four days of misery is not yet a decision");

        DaySim.Outcome out = DaySim.tickDay(pop, snap, rng);
        assertAll(
            () -> assertFalse(p.alive()),
            () -> assertEquals(Departure.LEFT, p.departure(),
                "and it is recorded as leaving, not as a death"),
            () -> assertEquals(1, out.left.size())
        );
    }

    @Test
    @DisplayName("cheering someone up resets their notice period")
    void reliefResetsTheLeavingClock() {
        Population pop = new Population();
        Person p = pop.add(new Person(new UUID(13, 1), Sex.MAN, 40));
        p.setDiscontent(95);
        TownSnapshot grim = fedTown(4).leaveAtDiscontent(80).leaveAfterDays(5)
            .discontentFedRelief(0);
        TownSnapshot kind = fedTown(4).leaveAtDiscontent(80).leaveAfterDays(5)
            .discontentFedRelief(40);
        Random rng = seeded();

        for (int d = 0; d < 4; d++) DaySim.tickDay(pop, grim, rng);
        DaySim.tickDay(pop, kind, rng);            // fed properly; discontent drops below the line
        for (int d = 0; d < 4; d++) DaySim.tickDay(pop, grim, rng);
        assertTrue(p.alive(),
            "four bad days, one good one, four more: the clock has to have reset, or a town can "
            + "never recover somebody it nearly lost");
    }

    @Test
    @DisplayName("a hungry town does not have children")
    void hungerStopsBirths() {
        Population pop = new Population();
        pop.add(new Person(new UUID(6, 1), Sex.MAN, 40));
        pop.add(new Person(new UUID(6, 2), Sex.WOMAN, 40));
        TownSnapshot hungry = new TownSnapshot().housingCapacity(20).foodUnits(1)
            .birthChancePerMille(1000).starveAfterDays(99);
        DaySim.Outcome out = DaySim.tickDay(pop, hungry, seeded());
        assertAll(
            () -> assertTrue(out.wentHungry > 0, "somebody went short"),
            () -> assertTrue(out.born.isEmpty(), "and so nobody was born")
        );
    }

    @Test
    @DisplayName("a child cannot work and is not employable")
    void childrenDoNotWork() {
        Population pop = new Population();
        Person child = pop.add(new Person(new UUID(7, 1), Sex.WOMAN, 0));
        TownSnapshot snap = fedTown(4).trade("baker", 3, 5);
        DaySim.tickDay(pop, snap, seeded());
        assertAll(
            () -> assertTrue(child.isChild()),
            () -> assertFalse(child.isEmployed(), "a newborn is not a baker"),
            () -> assertEquals(0, pop.adults().size())
        );
    }

    // --- discontent -------------------------------------------------------------------------

    @Test
    @DisplayName("unrest is the share of the angry, not an average")
    void unrestIsAShareNotAMean() {
        Population pop = new Population();
        for (int i = 0; i < 10; i++) {
            Person p = pop.add(new Person(new UUID(8, i), Sex.MAN, 40));
            p.setDiscontent(i < 3 ? 90 : 10);
        }
        assertAll(
            () -> assertEquals(0.3, pop.unrest(75), 1e-9,
                "three furious out of ten is 30% unrest, not a mild mean of 34"),
            () -> assertEquals(0.0, new Population().unrest(50), 1e-9,
                "an empty town is not seething")
        );
    }

    @Test
    @DisplayName("hunger raises discontent and being fed lowers it")
    void hungerAndReliefMoveDiscontent() {
        Population pop = town(1, 4);
        Person p = pop.living().get(0);
        DaySim.tickDay(pop, new TownSnapshot().housingCapacity(4).foodUnits(0)
            .starveAfterDays(99).discontentPerHungryDay(12), seeded());
        int afterHunger = p.discontent();
        assertTrue(afterHunger > 0, "going short has to be felt");

        for (int d = 0; d < 5; d++) {
            DaySim.tickDay(pop, fedTown(4).discontentFedRelief(3), seeded());
        }
        assertTrue(p.discontent() < afterHunger, "and being fed has to settle it again");
    }

    @Test
    @DisplayName("overcrowding is felt by everyone in the town")
    void overcrowdingIsFelt() {
        Population pop = town(6, 2);   // six people, two beds
        Person p = pop.living().get(0);
        DaySim.tickDay(pop, fedTown(2).discontentPerCrowding(4).discontentFedRelief(0), seeded());
        assertTrue(p.discontent() >= 4, "six into two beds must show");
    }

    // --- scale and invariants ---------------------------------------------------------------

    @Test
    @DisplayName("two thousand people, a thousand days, and the arithmetic still agrees")
    void twoThousandPeopleAThousandDays() {
        Population pop = new Population();
        for (int i = 0; i < 2000; i++) {
            pop.add(new Person(new UUID(9, i), i % 2 == 0 ? Sex.MAN : Sex.WOMAN,
                Person.ADULT_AT_DAYS + (i % 200)));
        }
        TownSnapshot snap = fedTown(2200)
            .trade("farmer", 400, 2)
            .trade("mason", 120, 3)
            .trade("smith", 60, 5);

        long started = System.nanoTime();
        Random rng = seeded();
        for (int d = 0; d < 1000; d++) DaySim.tickDay(pop, snap, rng);
        long ms = (System.nanoTime() - started) / 1_000_000;

        assertAll(
            () -> assertEquals(pop.all().size(), pop.livingCount() + pop.deadCount(),
                "living plus dead must be everyone; if this drifts, a death was counted twice"),
            () -> assertTrue(pop.livingCount() <= snap.housingCapacity(),
                "the population may never exceed its beds"),
            () -> assertTrue(pop.withTrade("smith").size() <= 60,
                "a trade may never hold more people than it has slots"),
            () -> assertTrue(ms < 20_000,
                "a thousand days over two thousand people took " + ms + "ms; the whole point of "
                + "records over entities is that this is cheap")
        );
        System.out.println("2000 people x 1000 days in " + ms + "ms -> " + pop
            + ", wealth " + java.util.Arrays.toString(pop.wealthHistogram()));
    }

    @Test
    @DisplayName("nobody holds two trades, ever")
    void nobodyHoldsTwoTrades() {
        Population pop = town(50, 60);
        TownSnapshot snap = fedTown(60)
            .trade("a", 20, 1).trade("b", 20, 1).trade("c", 20, 1);
        Random rng = seeded();
        for (int d = 0; d < 200; d++) DaySim.tickDay(pop, snap, rng);
        int a = pop.withTrade("a").size(), b = pop.withTrade("b").size(), c = pop.withTrade("c").size();
        assertEquals(pop.adults().size() - pop.idle().size(), a + b + c,
            "the sum over trades must equal the employed, or somebody is counted twice");
    }

    @Test
    @DisplayName("the same seed gives the same town")
    void simulationIsReproducible() {
        java.util.function.Supplier<String> run = () -> {
            Population pop = town(20, 30);
            TownSnapshot snap = fedTown(30).trade("farmer", 8, 2);
            Random rng = seeded();
            for (int d = 0; d < 300; d++) DaySim.tickDay(pop, snap, rng);
            return pop.livingCount() + "/" + pop.deadCount() + "/" + pop.purseTotal();
        };
        assertEquals(run.get(), run.get(),
            "an unreproducible simulation cannot be debugged, which is why the rng is a parameter");
    }

    // --- wealth tiers -----------------------------------------------------------------------

    @Test
    @DisplayName("wealth tiers are contiguous and survive a round trip")
    void wealthTiersAreSound() {
        assertAll(
            () -> assertEquals(Wealth.DESTITUTE, Wealth.of(0)),
            () -> assertEquals(Wealth.DESTITUTE, Wealth.of(15)),
            () -> assertEquals(Wealth.POOR, Wealth.of(16)),
            () -> assertEquals(Wealth.COMFORTABLE, Wealth.of(96)),
            () -> assertEquals(Wealth.RICH, Wealth.of(100_000)),
            () -> assertEquals(Wealth.RICH, Wealth.byTier(Wealth.RICH.tier())),
            () -> assertEquals(Wealth.DESTITUTE, Wealth.byTier(-3), "a bad tier clamps"),
            () -> assertEquals(Wealth.RICH, Wealth.byTier(99), "and so does a future one")
        );
    }

    @Test
    @DisplayName("a death counts once, however many things kill you")
    void dyingIsIdempotent() {
        Person p = new Person(new UUID(10, 1), Sex.MAN, 40);
        p.die(7);
        p.die(9);
        assertAll(
            () -> assertFalse(p.alive()),
            () -> assertEquals(7, p.diedOnDay(), "the first death is the one that happened")
        );
    }
}
