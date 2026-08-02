package org.dawnoftime.onceuponatown.behavior.war;

/**
 * The deterministic state machine for one squad in one battle.
 *
 * <p><b>What this is.</b> A pure function {@code (squad, current, ctx) -> next}
 * that the {@link org.dawnoftime.onceuponatown.behavior.BattleDriver} calls
 * once per second per active squad. The replacement for vanilla mob AI at
 * war scale (per VISION.md "scale problem" — VISION §"the honest scale
 * problem"; ROADMAP.md §"Act 5 — The far end").
 *
 * <p><b>Transitions.</b>
 * <ul>
 *   <li>ADVANCING + within {@value #ENGAGE_RANGE} blocks of target →
 *       ENGAGING.</li>
 *   <li>ENGAGING + at target + health &gt; 0.5 → VICTORIOUS (terminal).</li>
 *   <li>Any non-terminal + health ≤ {@value #RETREAT_HEALTH} → RETREATING.</li>
 *   <li>Any non-terminal + health ≤ {@value #ROUT_HEALTH} → ROUTED (terminal).</li>
 *   <li>ROUTED, VICTORIOUS — terminal, no transition.</li>
 *   <li>RETREATING — stays put. Recovery is a separate concern handled by
 *       the driver (it would re-spawn a replacement squad in a future
 *       slice, not un-route the same one).</li>
 * </ul>
 *
 * <p><b>Health-vs-distance precedence.</b> Health wins. A squad that is
 * already engaging the target but has been routed retreats before it
 * can declare victory. This keeps the terminal states from being
 * reachable as a "win" by a broken squad.
 *
 * <p><b>Persistence.</b> NONE. The driver holds the per-squad state in
 * a {@code Map<UUID, BattleState>}. Persistence is intentionally not
 * built: wars are short-lived (a few minutes of NPC-vs-NPC combat at
 * most), the state machine is deterministic, and re-running the last
 * tick on a server reload costs nothing. This is a decision documented
 * for the next slice — if a war ever needs to survive a server restart,
 * the place to add it is the driver, not this class.
 *
 * <p>Threshold values are constants the gate and the debug logs both
 * reference. Change them only with a corresponding rule update.
 */
public final class BattleStateMachine {

    /** Squads within this many blocks of their target switch ADVANCING → ENGAGING. */
    private static final double ENGAGE_RANGE = 10.0;

    /** Squad health at or below this fraction routes the squad to RETREATING. */
    private static final float RETREAT_HEALTH = 0.30f;

    /** Squad health at or below this fraction routes the squad to ROUTED (terminal). */
    private static final float ROUT_HEALTH = 0.10f;

    /** Minimum health the squad must have to claim VICTORIOUS from ENGAGING. */
    private static final float VICTORY_HEALTH = 0.5f;

    /**
     * Tick the squad's battle state. Returns the new state. The caller
     * is responsible for remembering the per-squad state between ticks —
     * this method is pure. The current state is a parameter so the same
     * call-site can drive any squad regardless of where it currently is.
     */
    public BattleState tick(Squad squad, BattleState current, BattleContext ctx) {
        if (squad == null) throw new IllegalArgumentException("squad must be non-null");
        if (current == null) throw new IllegalArgumentException("current state must be non-null");
        if (ctx == null) throw new IllegalArgumentException("ctx must be non-null");

        float health = ctx.casualties().averageHealth(squad);
        double distance = squad.distanceToTarget();

        // Terminal states don't change.
        if (current == BattleState.VICTORIOUS || current == BattleState.ROUTED) {
            return current;
        }

        // Health-based retreat / rout. Health wins over progression — a
        // squad that was about to win and just lost half its members
        // must not transition to VICTORIOUS.
        if (health <= ROUT_HEALTH) return BattleState.ROUTED;
        if (health <= RETREAT_HEALTH) return BattleState.RETREATING;

        // Distance-based advance / engage.
        if (current == BattleState.ADVANCING && distance <= ENGAGE_RANGE) {
            return BattleState.ENGAGING;
        }

        // Engagement → victory: at the target, still healthy enough to claim it.
        if (current == BattleState.ENGAGING
            && distance <= ENGAGE_RANGE
            && health > VICTORY_HEALTH) {
            return BattleState.VICTORIOUS;
        }

        // Retreating stays put. Recovery is the driver's responsibility.
        return current;
    }

    /** Exposed for tests / debug logging. */
    public double engageRange() { return ENGAGE_RANGE; }
    public float retreatHealth() { return RETREAT_HEALTH; }
    public float routHealth() { return ROUT_HEALTH; }
}
