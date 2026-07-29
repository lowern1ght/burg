package org.dawnoftime.onceuponatown.tick;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.dawnoftime.onceuponatown.building.schematic.BuildSchematic;
import org.dawnoftime.onceuponatown.entity.CitizenNames;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.people.Person;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lends bodies to people, and takes them back.
 *
 * <p>A town's population lives as records that tick whether or not anybody is watching. This is
 * the window onto them: while a player is in the settlement, some of those records get an {@link
 * Npc} to walk around in. Two thousand people are therefore possible without two thousand
 * pathfinding mobs — the wall that made the old one-resident-one-entity model top out somewhere
 * around a hundred.
 *
 * <p><b>Three rules, and each of them is a bug that would otherwise be invisible.</b>
 *
 * <ol>
 *   <li><b>Never embody in view.</b> A body appearing twenty blocks in front of the player reads
 *       as a spawn, which is exactly what the walking-in arrival was built to avoid. So the
 *       window has hysteresis: bodies appear only outside {@link #EMBODY_MIN} and are taken back
 *       only beyond {@link #RELEASE_AT}, which is further out than they are lent.</li>
 *   <li><b>Taking a body back is not a death.</b> {@code Npc.remove} strikes a person off only on
 *       {@code KILLED}; every release here uses {@code DISCARDED}. Get this wrong and a town
 *       quietly empties as the player walks away from it.</li>
 *   <li><b>One body per person.</b> Two bodies for one record is two of the same human being in
 *       one street, and the second one's work would be credited twice.</li>
 * </ol>
 */
public final class Embodiment {

    private static final Logger LOGGER = LoggerFactory.getLogger(Embodiment.class);

    /** How many bodies a town may have out at once, whatever its population. */
    public static final int MAX_BODIES = 60;

    /** Nothing is embodied closer to a player than this, so nobody sees one appear. */
    public static final int EMBODY_MIN = 40;

    /** Nor further out than this, since there would be no point. */
    public static final int EMBODY_MAX = 96;

    /** Bodies are taken back beyond this. Deliberately past {@link #EMBODY_MAX}: the gap is the
     *  hysteresis that stops a body flickering in and out as the player paces a boundary. */
    public static final int RELEASE_AT = 128;

    private static final int EVERY_TICKS = 40;

    private Embodiment() {
    }

    public static void tick(ServerLevel level, Town town, BlockPos anchor, long gameTime) {
        if (gameTime % EVERY_TICKS != 0) return;

        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        // Everything of ours currently standing in the neighbourhood, in one query.
        List<Npc> bodies = level.getEntitiesOfClass(Npc.class,
            new AABB(anchor).inflate(RELEASE_AT + 32),
            n -> n.getRole() == Npc.Role.SETTLER);

        Set<UUID> embodied = new HashSet<>();
        for (Npc body : bodies) {
            UUID pid = body.getPersonId().orElse(null);

            // A body with nobody in it, or with somebody who has died or left, is litter.
            Person person = pid == null ? null : town.people().get(pid);
            if (person == null || !person.alive()) {
                release(body);
                continue;
            }

            // One body per person. A duplicate is two of the same human being in one street, and
            // its work would be credited to the record twice.
            if (!embodied.add(pid)) {
                release(body);
                continue;
            }

            // Push what the record knows and the body only displays. The record is the truth;
            // this is the one direction data flows.
            body.setWealthTier(person.wealth().tier());

            if (nearestPlayerDistSq(players, body) > (double) RELEASE_AT * RELEASE_AT) {
                release(body);
                embodied.remove(pid);
            }
        }

        int room = MAX_BODIES - embodied.size();
        if (room <= 0) return;

        // Candidates: living people who have no body yet. Oldest records first, which is stable
        // and therefore reproducible — a shuffled order would embody a different crowd every
        // time the player crossed the same street.
        List<Person> waiting = new ArrayList<>();
        for (Person p : town.people().living()) {
            if (!embodied.contains(p.id())) waiting.add(p);
        }
        if (waiting.isEmpty()) return;

        for (Person person : waiting) {
            if (room <= 0) break;
            BlockPos where = placeOutOfSight(level, players, anchor);
            if (where == null) continue;
            if (embody(level, town, anchor, person, where)) room--;
        }
    }

    /**
     * Somewhere in the settlement that no player is close to.
     *
     * <p>Tried a handful of times rather than solved: a settlement is not convex, the ground moves,
     * and a perfect answer is not worth a search when the cost of failing is that one person waits
     * forty ticks for a body.
     */
    private static BlockPos placeOutOfSight(ServerLevel level, List<ServerPlayer> players,
                                            BlockPos anchor) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
            int radius = EMBODY_MIN + level.getRandom().nextInt(EMBODY_MAX - EMBODY_MIN);
            int x = anchor.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * radius);

            boolean tooClose = false;
            for (ServerPlayer p : players) {
                double dx = p.getX() - x, dz = p.getZ() - z;
                if (dx * dx + dz * dz < (double) EMBODY_MIN * EMBODY_MIN) { tooClose = true; break; }
            }
            if (tooClose) continue;

            // Only where the world is actually loaded, or the body lands in a void and falls.
            if (!level.isLoaded(new BlockPos(x, anchor.getY(), z))) continue;
            int y = BuildSchematic.groundY(level, x, z);
            if (y == BuildSchematic.NO_GROUND) continue;
            return new BlockPos(x, y + 1, z);
        }
        return null;
    }

    private static boolean embody(ServerLevel level, Town town, BlockPos anchor,
                                  Person person, BlockPos where) {
        Npc body = EntityRegistry.NPC.create(level);
        if (body == null) return false;
        body.setRole(Npc.Role.SETTLER);
        body.setTownAnchorPos(anchor);
        body.setPersonId(person.id());
        body.setWealthTier(person.wealth().tier());
        body.setBaby(person.isChild());
        body.moveTo(where.getX() + 0.5, where.getY(), where.getZ() + 0.5,
            level.getRandom().nextFloat() * 360.0f, 0.0f);

        if (!level.addFreshEntity(body)) return false;
        LOGGER.debug("[OUAT-BODY] {} took a body at {}", CitizenNames.of(person.id()), where);
        return true;
    }

    /**
     * Hands the body back.
     *
     * <p>{@code DISCARDED} and never {@code KILLED}. {@code Npc.remove} strikes a person off the
     * roll on a kill, so releasing with the wrong reason would depopulate a town every time the
     * player walked out of it — the sort of fault that looks like a save bug days later.
     */
    private static void release(Npc body) {
        body.remove(Entity.RemovalReason.DISCARDED);
    }

    private static double nearestPlayerDistSq(List<ServerPlayer> players, Npc body) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : players) {
            best = Math.min(best, p.distanceToSqr(body));
        }
        return best;
    }
}
