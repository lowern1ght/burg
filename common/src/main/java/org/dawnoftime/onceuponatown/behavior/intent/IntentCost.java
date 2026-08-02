package org.dawnoftime.onceuponatown.behavior.intent;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The resource cost of executing a {@link TownIntent}.
 *
 * <p>Stored as a list of {@link Entry} pairs — {@code (item id, amount)} — rather than as
 * {@code ItemStack[]}, deliberately. The reason is testability and decoupling: an intent is a
 * declaration, not a placed inventory, and treating its cost as raw data keeps it free of the
 * mutable NBT baggage that {@code ItemStack} carries. The mapping from an entry to a live
 * {@code Item} (and ultimately to a town inventory check) happens at the boundary where the
 * intent is resolved, not inside the cost object itself.
 *
 * <p>An intent with no cost returns the same value from {@link #empty()} every call. The empty
 * constant is a singleton so {@code ==} works on it, which scheduler-level tests can lean on.
 */
public record IntentCost(List<Entry> entries) {

    private static final IntentCost EMPTY = new IntentCost(List.of());

    public record Entry(ResourceLocation itemId, int amount) {
        public Entry {
            if (itemId == null) throw new IllegalArgumentException("itemId must not be null");
            if (amount < 0) throw new IllegalArgumentException("amount must be non-negative");
        }
    }

    public IntentCost {
        entries = List.copyOf(entries);
    }

    public static IntentCost empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int totalAmount() {
        return entries.stream().mapToInt(Entry::amount).sum();
    }
}
