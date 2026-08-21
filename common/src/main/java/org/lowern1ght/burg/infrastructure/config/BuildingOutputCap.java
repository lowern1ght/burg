package org.lowern1ght.burg.infrastructure.config;

import java.util.Objects;

/**
 * Per-instance output cap for a {@link org.lowern1ght.burg.town.PlacedBuilding},
 * expressed as a value object. Mirrors {@link org.lowern1ght.burg.people.RaidConfig}:
 * a single {@code current} slot the infrastructure pushes into on mod-bus init
 * and on every config reload, and a default the bare-JVM tests use until
 * something wires an override.
 *
 * <p>The cap is the maximum number of distinct {@link
 * org.lowern1ght.burg.domain.shared.ItemId} entries a building's
 * {@link org.lowern1ght.burg.domain.settlement.StockLedger} may carry; when
 * a write would push the ledger past the cap, the oldest entries are
 * FIFO-dropped at the edge until size == cap. The clamp to {@code [16, 4096]}
 * is the domain invariant; the spec's {@code defineInRange} mirrors it so a
 * bad TOML value is caught at load time as well.
 *
 * <p>Why the value-object indirection (and not a direct read of the spec
 * field). The spec's {@code IntValue.set(int)} requires a loaded
 * {@code IConfigSpec} context — without it, the bare-JVM test target
 * (which has the spec but no registered mod config) cannot override the
 * cap to exercise the FIFO discipline. The value-object slot is the
 * mutation surface tests use; the spec value is pushed into it at
 * mod-bus init and on every config reload by
 * {@link BurgConfig#refreshBuildingOutputCap()}.
 *
 * <p>No Minecraft import, by rule. See {@link org.lowern1ght.burg.people.RaidConfig}.
 */
public final class BuildingOutputCap {

    /** Hard floor on the value object. Two is the smallest cap that still leaves meaningful FIFO
     *  semantics — the bare-JVM test exercises {@code cap=2, three adds} as the smallest case
     *  that drains. The user-facing Cloth knob enforces a stricter floor (16) on top of this. */
    public static final int MIN_ITEMS = 2;

    /** Hard ceiling. Above this and a misconfigured world never drains regardless of turnover. */
    public static final int MAX_ITEMS = 4096;

    /** The neutral value — 256 distinct items per building, comfortably above any sane rule mix. */
    public static final int DEFAULT_ITEMS = 256;

    /** A pre-built instance for the default, so callers do not allocate a fresh one. */
    public static final BuildingOutputCap DEFAULT = new BuildingOutputCap(DEFAULT_ITEMS);

    /**
     * The currently-active cap. Infrastructure sets it once at mod-bus
     * init; tests read/write it freely. Kept volatile because the config
     * screen may rebuild the value on a different thread.
     */
    private static volatile BuildingOutputCap current = DEFAULT;

    private final int items;

    public BuildingOutputCap(int items) {
        this.items = Math.max(MIN_ITEMS, Math.min(MAX_ITEMS, items));
    }

    /** The configured cap, in distinct items. The value is range-clamped on construction. */
    public int items() { return items; }

    /** The cap currently in effect at the wire site. */
    public static BuildingOutputCap current() { return current; }

    /**
     * Replace the active cap. Called from infrastructure (Cloth Config) at
     * mod-bus init and on config reload; the test suite uses it to exercise
     * a non-default value without touching the GUI.
     */
    public static void setCurrent(BuildingOutputCap cap) {
        Objects.requireNonNull(cap, "cap");
        current = cap;
    }

    /** Reset to {@link #DEFAULT}. Used by tests so a {@code setCurrent} in one test does not leak. */
    public static void resetCurrent() { current = DEFAULT; }

    @Override
    public String toString() { return "BuildingOutputCap[" + items + " items]"; }

    @Override
    public boolean equals(Object o) {
        return o instanceof BuildingOutputCap other && items == other.items;
    }

    @Override
    public int hashCode() { return Integer.hashCode(items); }
}
