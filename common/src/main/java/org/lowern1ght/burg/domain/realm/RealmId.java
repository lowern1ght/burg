package org.lowern1ght.burg.domain.realm;

import java.util.Objects;

/**
 * Canonical identity of a realm inside the Realm bounded context.
 *
 * <p>The realm layer sits above {@code Town} (VISION §"The immediate
 * architecture consequence"): a metropolis plus its colonies plus foreign
 * holdings. Its identity is a plain string today — the storage
 * representation is an open question (realm design README §"Open
 * questions" #1), so this type deliberately wraps only the canonical
 * string form and adds no UUID opinion. When the {@code LevelRealms}
 * SavedData lands, its converter lives at that facade edge, the same way
 * {@code CitizenId} converts at the {@code Town} edge.
 *
 * <p>Instances are immutable. Two {@code RealmId} values are equal iff
 * their canonical strings are equal. The canonical form is the trimmed
 * input; {@link #of(String)} is the strict factory and rejects blank
 * strings — there is deliberately <em>no</em> EMPTY sentinel, because how
 * a "player with no realm" is represented is an open design question and
 * a sentinel would silently answer it.
 */
public record RealmId(String value) {

    public RealmId {
        Objects.requireNonNull(value, "RealmId.value");
    }

    /**
     * Strict factory. Trims the input and rejects null / blank strings.
     * The boundary caller (the future realm SavedData facade) is
     * responsible for catching its own garbage.
     */
    public static RealmId of(String raw) {
        Objects.requireNonNull(raw, "raw");
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(
                "RealmId must be a non-blank string (got '" + raw + "')");
        }
        return new RealmId(trimmed);
    }
}
