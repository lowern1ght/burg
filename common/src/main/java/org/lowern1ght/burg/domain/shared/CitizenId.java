package org.lowern1ght.burg.domain.shared;

import java.util.Objects;
import java.util.UUID;

/**
 * Canonical identity of a citizen (player or NPC) inside the Settlement
 * bounded context.
 *
 * @param value the canonical (UUID {@link UUID#toString()} form) identity string; never null
 *
 * <p>Minecraft delivers player and entity identities as {@link UUID}, but the
 * domain layer is Minecraft-free (ADR-0008). {@code CitizenId} wraps the
 * canonical string form of the UUID; {@link #of(UUID)} and {@link #toUuid()}
 * are the boundary converters used at the Town facade edge.
 *
 * <p>Instances are immutable. Two {@code CitizenId} values are equal iff their
 * canonical string forms are equal — the {@link UUID#equals} contract is
 * preserved transitively because every well-formed UUID has exactly one
 * canonical string form.
 *
 * <p>The canonical form is the one {@link UUID#toString()} emits; it is
 * stable across JVMs and is the same form used by Minecraft's
 * {@code UUID.fromString} and by the {@code ChatSubscribers} NBT list in
 * {@code Town} (which already persists UUIDs as strings).
 */
public record CitizenId(String value) {

    /** The empty / unset id is permitted only for places where the code says so explicitly. */
    public static final CitizenId EMPTY = new CitizenId("00000000-0000-0000-0000-000000000000");

    public CitizenId {
        Objects.requireNonNull(value, "CitizenId.value");
        // We do NOT call UUID.fromString here on purpose: the empty / blank
        // sentinel must remain a valid handle, and we want construction to
        // be allocation-free in the additive NBT-load path (the contract
        // already filters malformed entries at the Town facade edge).
    }

    /** Wraps a Minecraft / JDK UUID. The canonical string form is what gets stored. */
    public static CitizenId of(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        return new CitizenId(uuid.toString());
    }

    /** Parses a canonical-form string. Throws if the string is not a valid UUID. */
    public static CitizenId parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        return new CitizenId(UUID.fromString(raw).toString());
    }

    /** Parses leniently — bad strings become the EMPTY id rather than throwing. */
    public static CitizenId parseOrEmpty(String raw) {
        if (raw == null || raw.isEmpty()) return EMPTY;
        try {
            return parse(raw);
        } catch (IllegalArgumentException e) {
            return EMPTY;
        }
    }

    /** Returns the wrapped UUID. The boundary caller is responsible for parsing. */
    public UUID toUuid() {
        return UUID.fromString(value);
    }
}