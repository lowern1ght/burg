package org.dawnoftime.onceuponatown.integration.xaero;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.dawnoftime.onceuponatown.integration.ExternalModPresence;
import org.dawnoftime.onceuponatown.town.Town;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Soft-dependency wrapper for Xaero's Minimap. The mod is purely a client-side HUD
 * mod; waypoints are stored in per-player JSON files on the player's machine. This
 * class therefore has two completely different runtime shapes:
 *
 * <ul>
 *   <li>When Xaero is not loaded (the common case) every method is a no-op. Reflection
 *       is never invoked; there is no per-tick cost.</li>
 *   <li>When Xaero IS loaded but the JVM is the dedicated server, the call sites
 *       ({@link #onTownRegistered}, {@link #onTownRemoved},
 *       {@link #onWarStarted}, {@link #onWarEnded}) cannot reach a client waypoint
 *       manager from the server thread. The very first call logs a single warning
 *       explaining the limitation, then every subsequent call is a no-op. Real
 *       client-side waypoint rendering requires a follow-up phase that ships an
 *       S2C packet and a client listener on the Xaero client API; see
 *       {@code docs/02-roadmap/ROADMAP.md} (Phase BEHAVIOR-10+).</li>
 *   <li>When Xaero IS loaded and the JVM is the physical client, the methods below
 *       resolve the Xaero API once via cached reflection and forward waypoint add /
 *       remove calls to {@code xaero.common.XaeroMinimapSession} ->
 *       {@code xaero.common.minimap.waypoints.WaypointsManager}. The reflection
 *       path is exercised exactly once per JVM and the resolved {@link Method}
 *       handles are cached in static fields.</li>
 * </ul>
 *
 * <h2>Reflection paths</h2>
 *
 * Xaero's published waypoint API on 1.21.x lives in
 * {@code xaero.minimap:xaerominimap-common-1.21.1} (currently 26.4.2). The classes
 * and methods that matter for this integration:
 *
 * <ul>
 *   <li>{@code xaero.common.XaeroMinimapSession.getCurrentSession()} returns the
 *       current per-client session singleton, or {@code null} outside a world.</li>
 *   <li>{@code xaero.common.minimap.waypoints.Waypoint} has a public constructor
 *       {@code (int x, int y, int z, String name, String initials, int color,
 *       int type, boolean global, boolean temporary)}. {@code type} is a small int
 *       constant (0 = normal, 1 = death, 2 = local).</li>
 *   <li>{@code xaero.common.minimap.waypoints.WaypointsManager.addWaypoint(Waypoint)}
 *       and {@code removeWaypoint(String name)} are the public mutators; the
 *       manager is obtained via
 *       {@code xaero.common.XaeroMinimapSession.getWaypointsManager()}.</li>
 * </ul>
 *
 * The {@code common} jar's package layout was preserved across 26.x: the
 * {@code Waypoint} class is in {@code xaero.common.minimap.waypoints} (NOT
 * {@code xaero.hud.minimap.waypoints} as in some 21.x Forge builds). When the
 * package layout changes in a future version the reflection lookups here will
 * fail and we will log + disable; that is the explicit failure mode and the
 * reason {@link #XAERO_WAYPOINT_CLASS} is a constant and not a wildcard.
 */
public final class XaeroIntegration {

    /** Mod id registered by Xaero's Minimap. Sourced from Xaero's own mods.toml. */
    public static final String MODID = "xaerominimap";

    private static final Logger LOGGER = LoggerFactory.getLogger(XaeroIntegration.class);

    // --- Reflection targets. Documented for the next maintainer; see class javadoc. ---
    /** {@code xaero.common.XaeroMinimapSession} — entry point. */
    private static final String SESSION_CLASS = "xaero.common.XaeroMinimapSession";
    /** {@code xaero.common.minimap.waypoints.Waypoint} — waypoint POJO. */
    private static final String XAERO_WAYPOINT_CLASS = "xaero.common.minimap.waypoints.Waypoint";
    /** {@code xaero.common.minimap.waypoints.WaypointsManager} — manager we call into. */
    private static final String MANAGER_CLASS = "xaero.common.minimap.waypoints.WaypointsManager";

    /** Waypoint type 0 = normal. The Xaero API exposes 0/1/2 (normal/death/local). */
    private static final int WAYPOINT_TYPE_NORMAL = 0;

    // Cached reflection handles. Initialised lazily on the first call when Xaero is
    // loaded AND the JVM is the client. All fields are package-private for test
    // access; production code never touches them.
    private static Method cachedGetCurrentSession;
    private static Method cachedGetWaypointsManager;
    private static Method cachedAddWaypoint;
    private static Method cachedRemoveWaypoint;
    private static Constructor<?> cachedWaypointCtor;
    /** Set to true once reflection has resolved successfully. */
    private static boolean reflectionResolved;
    /** Set to true if reflection has failed at least once; we won't try again. */
    private static boolean reflectionFailed;

    /** One-shot warning that waypoint injection is impossible on a dedicated server. */
    private static volatile boolean serverLimitationWarned;

    private XaeroIntegration() {}

    /** @return {@code true} when Xaero's Minimap is in the loaded mod list. */
    public static boolean isAvailable() {
        return ExternalModPresence.isLoaded(MODID);
    }

    /**
     * Called after a town has been registered in {@code LevelTowns}. No-op when
     * Xaero is not loaded; logs a single one-shot warning on the server JVM when
     * it is. On the client JVM a waypoint named {@code "town:<anchorLong>"} is
     * added at the town's anchor position.
     */
    public static void onTownRegistered(Town town) {
        if (!isAvailable() || town == null) return;
        BlockPos anchor = town.getAnchorPos();
        if (anchor == null || anchor.equals(BlockPos.ZERO)) return;
        onPhysicalClient(() -> addWaypointInternal(anchor, "town:" + anchor.asLong(), "T", -1));
    }

    /**
     * Called before a town is removed from {@code LevelTowns}. No-op when
     * Xaero is not loaded; on the client JVM removes the waypoint previously
     * added by {@link #onTownRegistered(Town)}.
     */
    public static void onTownRemoved(Town town) {
        if (!isAvailable() || town == null) return;
        BlockPos anchor = town.getAnchorPos();
        if (anchor == null || anchor.equals(BlockPos.ZERO)) return;
        onPhysicalClient(() -> removeWaypointInternal("town:" + anchor.asLong()));
    }

    /**
     * Called the first tick a {@code Relation} is observed as {@code AT_WAR}. No-op
     * when Xaero is not loaded. On the client JVM adds a "battle" waypoint at the
     * midpoint of the two town anchors.
     */
    public static void onWarStarted(Town attacker, Town defender) {
        if (!isAvailable() || attacker == null || defender == null) return;
        BlockPos a = attacker.getAnchorPos();
        BlockPos b = defender.getAnchorPos();
        if (a == null || b == null || a.equals(BlockPos.ZERO) || b.equals(BlockPos.ZERO)) return;
        BlockPos midpoint = midpoint(a, b);
        onPhysicalClient(() -> addWaypointInternal(midpoint, battleName(a, b), "B", -1));
    }

    /**
     * Called the first tick a previously-AT_WAR {@code Relation} is no longer
     * {@code AT_WAR}. Removes the waypoint previously added by
     * {@link #onWarStarted(Town, Town)}.
     */
    public static void onWarEnded(Town attacker, Town defender) {
        if (!isAvailable() || attacker == null || defender == null) return;
        BlockPos a = attacker.getAnchorPos();
        BlockPos b = defender.getAnchorPos();
        if (a == null || b == null || a.equals(BlockPos.ZERO) || b.equals(BlockPos.ZERO)) return;
        onPhysicalClient(() -> removeWaypointInternal(battleName(a, b)));
    }

    // -----------------------------------------------------------------------------------
    // internals
    // -----------------------------------------------------------------------------------

    /**
     * Runs {@code action} only on the physical client. On a server JVM it logs a
     * one-shot warning the first time and skips. The action is wrapped so callers
     * can pass a no-arg lambda and ignore the reflection plumbing.
     */
    private static void onPhysicalClient(Runnable action) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            if (!serverLimitationWarned) {
                serverLimitationWarned = true;
                LOGGER.warn("[OUAT-XAERO] Xaero's Minimap is loaded, but it is a"
                    + " client-side-only mod and waypoints cannot be injected from the"
                    + " server thread. Town and battle waypoints will not appear on"
                    + " players' minimaps. A follow-up phase is required: an S2C"
                    + " packet that the client listener forwards to the Xaero"
                    + " WaypointsManager. See Phase BEHAVIOR-10 in the roadmap.");
            }
            return;
        }
        action.run();
    }

    private static BlockPos midpoint(BlockPos a, BlockPos b) {
        return new BlockPos(
            (a.getX() + b.getX()) / 2,
            (a.getY() + b.getY()) / 2,
            (a.getZ() + b.getZ()) / 2);
    }

    private static String battleName(BlockPos a, BlockPos b) {
        // Stable id: both endpoints matter, but they are interchangeable, so sort the
        // longs. This keeps "war A vs B" and "war B vs A" collapsing into one
        // waypoint name regardless of which side declared.
        long la = a.asLong();
        long lb = b.asLong();
        long lo = Math.min(la, lb);
        long hi = Math.max(la, lb);
        return "battle:" + lo + ":" + hi;
    }

    // --- reflection-backed Xaero call site ------------------------------------------------

    private static void addWaypointInternal(BlockPos pos, String name, String initials, int color) {
        try {
            Object session = currentSession();
            if (session == null) return;
            Object manager = cachedGetWaypointsManager.invoke(session);
            if (manager == null) return;
            Object wp = cachedWaypointCtor.newInstance(
                pos.getX(), pos.getY(), pos.getZ(),
                name, initials, color, WAYPOINT_TYPE_NORMAL, true, false);
            cachedAddWaypoint.invoke(manager, wp);
        } catch (InvocationTargetException ex) {
            // Don't disable on a per-call failure — Xaero might be initialising.
            LOGGER.warn("[OUAT-XAERO] addWaypoint({}) failed: {}", name, ex.getTargetException().toString());
        } catch (ReflectiveOperationException ex) {
            if (!reflectionFailed) {
                reflectionFailed = true;
                LOGGER.warn("[OUAT-XAERO] reflection failed resolving the Xaero waypoint API;"
                    + " subsequent calls will be no-ops until restart. Cause: {}", ex.toString());
            }
        }
    }

    private static void removeWaypointInternal(String name) {
        try {
            Object session = currentSession();
            if (session == null) return;
            Object manager = cachedGetWaypointsManager.invoke(session);
            if (manager == null) return;
            cachedRemoveWaypoint.invoke(manager, name);
        } catch (InvocationTargetException ex) {
            LOGGER.warn("[OUAT-XAERO] removeWaypoint({}) failed: {}", name, ex.getTargetException().toString());
        } catch (ReflectiveOperationException ex) {
            if (!reflectionFailed) {
                reflectionFailed = true;
                LOGGER.warn("[OUAT-XAERO] reflection failed resolving the Xaero waypoint API;"
                    + " subsequent calls will be no-ops until restart. Cause: {}", ex.toString());
            }
        }
    }

    private static Object currentSession() throws ReflectiveOperationException {
        ensureReflectionResolved();
        if (cachedGetCurrentSession == null) return null;
        return cachedGetCurrentSession.invoke(null);
    }

    /**
     * Resolve and cache the Xaero API entry points. Runs exactly once per JVM
     * (the {@link #reflectionFailed} guard prevents repeated failures from
     * re-attempting on every tick).
     */
    private static void ensureReflectionResolved() throws ReflectiveOperationException {
        if (reflectionResolved || reflectionFailed) return;
        Class<?> sessionClass = Class.forName(SESSION_CLASS);
        Class<?> waypointClass = Class.forName(XAERO_WAYPOINT_CLASS);
        Class<?> managerClass = Class.forName(MANAGER_CLASS);
        cachedGetCurrentSession = sessionClass.getMethod("getCurrentSession");
        cachedGetWaypointsManager = sessionClass.getMethod("getWaypointsManager");
        // Waypoint(int, int, int, String, String, int, int, boolean, boolean)
        cachedWaypointCtor = waypointClass.getConstructor(
            int.class, int.class, int.class,
            String.class, String.class, int.class, int.class,
            boolean.class, boolean.class);
        cachedAddWaypoint = managerClass.getMethod("addWaypoint", waypointClass);
        cachedRemoveWaypoint = managerClass.getMethod("removeWaypoint", String.class);
        reflectionResolved = true;
    }

    // --- test accessors ------------------------------------------------------------------

    /** Resets cached reflection state. Test-only. */
    public static void resetReflectionCacheForTests() {
        cachedGetCurrentSession = null;
        cachedGetWaypointsManager = null;
        cachedAddWaypoint = null;
        cachedRemoveWaypoint = null;
        cachedWaypointCtor = null;
        reflectionResolved = false;
        reflectionFailed = false;
        serverLimitationWarned = false;
    }

    /** Test-only: was the reflection resolution successful on a previous call? */
    public static boolean isReflectionResolved() { return reflectionResolved; }

    /** Test-only: did reflection fail at least once? */
    public static boolean isReflectionFailed() { return reflectionFailed; }
}
