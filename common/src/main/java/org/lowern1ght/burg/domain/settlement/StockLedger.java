package org.lowern1ght.burg.domain.settlement;

import org.lowern1ght.burg.domain.shared.ItemId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A town's reserve stock, expressed as an immutable map from {@link ItemId}
 * to a non-negative quantity. The ledger is the second value object in the
 * Settlement bounded context (after {@code StandingBook}) and is the
 * strangler-side analogue of {@code Town.reserveStock} — a Minecraft-free
 * shape the domain layer can reason about without a {@code net.minecraft}
 * import on the classpath.
 *
 * <p>The {@code Town} facade still owns {@code Map<Item, Integer>
 * reserveStock} for NBT round-tripping; {@link #stockLedger()} is the
 * read-only domain view rebuilt from that map at the edge. This is the
 * strangler pattern ADR-0008 / ADR-0009 set up: additive, no behavior
 * change, no rename of existing fields.
 *
 * <p>Mutators ({@link #add}, {@link #take}, {@link #merge}) return a new
 * ledger. Entries whose quantity falls back to zero are dropped at the
 * edge so the persisted NBT — once the reserveStock map is replaced by a
 * domain ledger in a future carve — stays sparse. Today the ledger is a
 * read view; the mutators exist for the future carve that promotes it to
 * the source of truth.
 *
 * <p>No Minecraft imports. {@link ItemId} is the value-object wrapper
 * (ADR-0008 §"Minecraft types leave the domain").
 */
public final class StockLedger {

    /**
     * Empty ledger — the additive default for worlds saved before this
     * carve. A {@code StockLedger} loaded from pre-carve NBT (or
     * constructed from an empty reserveStock) is the EMPTY sentinel.
     */
    public static final StockLedger EMPTY = new StockLedger(Map.of());

    private final Map<ItemId, Integer> entries;

    private StockLedger(Map<ItemId, Integer> entries) {
        this.entries = entries;
    }

    /**
     * Returns the quantity stored for {@code item}, or {@code 0} when the
     * item is not on the ledger. The ledger is sparse: an item never seen
     * reads as zero, never as "absent".
     */
    public int get(ItemId item) {
        Objects.requireNonNull(item, "item");
        Integer q = entries.get(item);
        return q != null ? q : 0;
    }

    /**
     * Returns a new ledger with {@code quantity} added to the running total
     * for {@code item}. Entries that fall back to zero are dropped at the
     * edge. {@code quantity} must be non-negative; negative amounts are
     * reserved for {@link #take} and rejected here.
     */
    public StockLedger add(ItemId item, int quantity) {
        Objects.requireNonNull(item, "item");
        if (quantity < 0) {
            throw new IllegalArgumentException(
                "StockLedger.add requires a non-negative quantity; use take() to drain");
        }
        if (quantity == 0) return this;
        int current = get(item);
        return setInternal(item, current + quantity);
    }

    /**
     * Returns a new ledger with {@code quantity} removed from the running
     * total for {@code item}. Throws {@link IllegalStateException} if the
     * ledger has less than {@code quantity} for {@code item}. Entries that
     * fall back to zero are dropped at the edge.
     */
    public StockLedger take(ItemId item, int quantity) {
        Objects.requireNonNull(item, "item");
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                "StockLedger.take requires a positive quantity");
        }
        int current = get(item);
        if (quantity > current) {
            throw new IllegalStateException(
                "StockLedger.take: insufficient stock for " + item.value()
                    + " (have " + current + ", need " + quantity + ")");
        }
        return setInternal(item, current - quantity);
    }

    /**
     * Returns a new ledger whose entries are the union of this ledger and
     * {@code other}, with quantities summed. Entries that fall back to
     * zero are dropped at the edge.
     */
    public StockLedger merge(StockLedger other) {
        Objects.requireNonNull(other, "other");
        if (other.entries.isEmpty()) return this;
        Map<ItemId, Integer> next = new LinkedHashMap<>(this.entries);
        for (Map.Entry<ItemId, Integer> e : other.entries.entrySet()) {
            int existing = next.getOrDefault(e.getKey(), 0);
            int summed = existing + e.getValue();
            if (summed == 0) {
                next.remove(e.getKey());
            } else {
                next.put(e.getKey(), summed);
            }
        }
        return next.isEmpty() ? EMPTY : new StockLedger(next);
    }

    /** True iff no item is held in stock. */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Number of distinct items on the ledger. */
    public int size() {
        return entries.size();
    }

    /** Read-only view of every entry. Order is insertion order. */
    public Map<ItemId, Integer> entries() {
        return Collections.unmodifiableMap(entries);
    }

    /**
     * Builds a ledger from an existing map. The map is defensively copied
     * and any zero-quantity entries are dropped at construction time so
     * the constructor is the last line of defence against sparse-book
     * drift.
     */
    public static StockLedger of(Map<ItemId, Integer> source) {
        Objects.requireNonNull(source, "source");
        if (source.isEmpty()) return EMPTY;
        Map<ItemId, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<ItemId, Integer> e : source.entrySet()) {
            Objects.requireNonNull(e.getKey(), "key");
            if (e.getValue() != null && e.getValue() > 0) {
                copy.put(e.getKey(), e.getValue());
            }
        }
        return copy.isEmpty() ? EMPTY : new StockLedger(copy);
    }

    private StockLedger setInternal(ItemId item, int quantity) {
        Map<ItemId, Integer> next = new LinkedHashMap<>(entries);
        if (quantity == 0) {
            next.remove(item);
        } else {
            next.put(item, quantity);
        }
        return next.isEmpty() ? EMPTY : new StockLedger(next);
    }
}