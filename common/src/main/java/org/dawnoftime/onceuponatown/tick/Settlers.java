package org.dawnoftime.onceuponatown.tick;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.dawnoftime.onceuponatown.building.schematic.BuildSchematic;
import org.dawnoftime.onceuponatown.entity.CitizenNames;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.town.ConnectionPoint;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.PlacedBuilding;
import org.dawnoftime.onceuponatown.town.Town;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;

/**
 * People move into a town. They are not spawned into it.
 *
 * <p><b>The distinction is the whole feature.</b> The obvious implementation — the town wants
 * {@code getTotalResidents()} people, so top it up to that number — was written and thrown away,
 * because it makes death meaningless. If a body is replaced the tick after it falls, then a
 * settler drowning in the lake or losing an argument with a wolf costs nothing, and the comment
 * on {@link CitizenNames} stops being true: a dead "resident" is a number going down, a dead
 * Hedda Ashcroft is a loss you can feel. So nothing here refills a place. Houses raise the
 * ceiling; arrivals and births are the only ways under it.
 *
 * <p>Three gates, and each is meant to be legible from inside the game without a number on a
 * screen:
 *
 * <ul>
 *   <li><b>A bed.</b> {@link Town#getVacancies()} — build a house and someone can come.</li>
 *   <li><b>Food to spare.</b> Not "the town is fed" but "the town could feed one more", which is
 *       the honest question a person arriving would ask. Reuses {@link FoodManager}'s own
 *       reckoning rather than inventing a second idea of what food is.</li>
 *   <li><b>Standing.</b> Nobody walks toward a village that is starving its own. Derived from
 *       whether the last feeding fed everyone, so it needs no storage, no UI and no new number —
 *       and it is diagnosable: they stopped coming, look at the granary.</li>
 * </ul>
 *
 * <p>And they <b>walk in</b>. Appearing at the campfire would be spawning with extra steps; a
 * settler is put down at the far end of the newest street and walks to the centre, which is the
 * one street the zoning work is already pushing outward.
 */
public final class Settlers {

    private static final Logger LOGGER = LoggerFactory.getLogger(Settlers.class);

    /** How often the question is asked. A minute, because the answer changes slowly. */
    private static final int CHECK_EVERY_TICKS = 1200;

    /**
     * At most one arrival a day, less a day per completed quest, floored at a quarter day.
     *
     * <p>This is where standing does more than gate: a town that has done work for people gets
     * word around faster. Cheap, derived from {@code questDefLastCompleted} which is already
     * persisted, and replaceable by a real reputation number later without moving anything else.
     */
    private static final long BASE_COOLDOWN_TICKS = 24000L;
    private static final long MIN_COOLDOWN_TICKS = 6000L;

    private Settlers() {
    }

    public static void tick(Town town, ServerLevel level, long gameTime, BlockPos anchor) {
        if (gameTime % CHECK_EVERY_TICKS != 0) return;
        if (town.getVacancies() <= 0) return;

        long cooldown = Math.max(MIN_COOLDOWN_TICKS,
            BASE_COOLDOWN_TICKS - (long) town.getQuestDefLastCompleted().size() * 2000L);
        if (gameTime - town.getLastSettlerArrival() < cooldown) return;

        if (!isFedWell(town)) return;
        if (!canFeedOneMore(town)) return;

        // Nobody arrives at a town nobody is looking at. Not an optimisation — a person who
        // walks in from the fields while the player is elsewhere has, from the player's side,
        // simply appeared, which is the thing this class exists to avoid.
        boolean playerNearby = level.players().stream().anyMatch(p ->
            p.distanceToSqr(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5) < 96.0 * 96.0);
        if (!playerNearby) return;

        BlockPos gate = frontier(town, anchor);
        int groundY = BuildSchematic.groundY(level, gate.getX(), gate.getZ());
        if (groundY == BuildSchematic.NO_GROUND) return;

        Npc settler = EntityRegistry.NPC.create(level);
        if (settler == null) return;
        settler.setRole(Npc.Role.SETTLER);
        settler.setTownAnchorPos(anchor);
        settler.moveTo(gate.getX() + 0.5, groundY + 1.0, gate.getZ() + 0.5,
            level.getRandom().nextFloat() * 360.0f, 0.0f);

        // On the roll BEFORE it enters the world: Npc.tick validates itself against the roll for
        // its role on its first tick and discards if it is not there. Add it afterwards and the
        // settler deletes itself before anyone sees it — which is exactly the failure the role
        // was introduced to stop, arriving from the other direction.
        town.addResident(settler.getUUID());

        if (!level.addFreshEntity(settler)) {
            town.removeResident(settler.getUUID());
            return;
        }

        town.setLastSettlerArrival(gameTime);
        LevelTowns.get(level).markDirty();

        // Walks in rather than standing at the town limit waiting for a job to exist.
        settler.getNavigation().moveTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, 0.6);

        LOGGER.info("[OUAT-SETTLER] {} arrived at '{}' from {} -- roll now {} of {} beds",
            CitizenNames.of(settler.getUUID()), town.getName(), gate,
            town.getResidentNpcIds().size(), town.getTotalResidents());
    }

    /**
     * Did the last feeding feed everybody?
     *
     * <p>Against capacity rather than against the roll, deliberately, because that is what
     * {@link FoodManager} charges for today: it sums {@code resolvedResidents()} over the
     * buildings, so it bills the town for its BEDS and not for its people. Comparing the roll
     * against a figure computed from capacity would read as "well fed" for a town that is
     * actually short. Whether feeding should move onto real bodies is a live question and a
     * change in balance — noted rather than smuggled in here.
     */
    private static boolean isFedWell(Town town) {
        return town.getActiveResidents() >= town.getTotalResidents();
    }

    /** Whether a day's food for one more mouth is on the shelf, over what the town already owes. */
    private static boolean canFeedOneMore(Town town) {
        float demand = FoodManager.residentFoodDemand(town);
        int available = FoodManager.availableResidentFoodUnits(town);
        // One more mouth eats about what the average one does; with no residents at all, a single
        // unit is enough to justify the first arrival.
        float perHead = town.getTotalResidents() > 0
            ? demand / town.getTotalResidents() : 1.0f;
        return available >= Math.ceil(demand + perHead);
    }

    /**
     * Where the road runs out — the point a stranger would appear from.
     *
     * <p>The newest free connection point, because {@code cpInsertionCounter} makes insertion
     * order an age and the newest connection IS the growing edge of the town. Falls back to the
     * building furthest from the centre, and finally to the centre itself, so a town with no
     * frontier still admits people rather than silently refusing them forever.
     */
    private static BlockPos frontier(Town town, BlockPos anchor) {
        ConnectionPoint newest = town.getAvailableConnectionPoints().stream()
            .max(Comparator.comparingLong(ConnectionPoint::insertionOrder))
            .orElse(null);
        if (newest != null) return newest.pos();

        PlacedBuilding furthest = town.getBuildings().stream()
            .max(Comparator.comparingDouble(b -> b.worldPos.distSqr(anchor)))
            .orElse(null);
        return furthest != null ? furthest.worldPos : anchor;
    }
}
