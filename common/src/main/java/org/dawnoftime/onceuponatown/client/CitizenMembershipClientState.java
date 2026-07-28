package org.dawnoftime.onceuponatown.client;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which villagers the client has been told are ours.
 *
 * <p>This set exists because NeoForge 21.1.77 cannot sync a data attachment — there is no
 * {@code sync()} on {@code AttachmentType.Builder}, verified against the jar rather than
 * assumed. So the server publishes the one bit the client cannot work out for itself, and
 * everything else about a citizen (name, face, tint) the client derives from the UUID it
 * already has.
 *
 * <p><b>Keyed by UUID, not by entity id, on purpose.</b> Entity ids are per-level and get
 * reused, so a stale id would eventually name the wrong entity — a horse wearing a citizen's
 * face. A UUID is never reused, so a stale entry can only ever refer to the same citizen and
 * needs no teardown on dimension change or relog. That trade costs 16 bytes a packet and
 * removes a whole class of bug.
 */
public final class CitizenMembershipClientState {

    // Written on the client thread from the payload handler, read on the render thread.
    // Concurrent because those are the same thread today and I would rather not rely on it.
    private static final Set<UUID> MEMBERS = ConcurrentHashMap.newKeySet();

    private CitizenMembershipClientState() {
    }

    public static void set(UUID villager, boolean member) {
        if (member) MEMBERS.add(villager);
        else MEMBERS.remove(villager);
    }

    public static boolean isMember(UUID villager) {
        return MEMBERS.contains(villager);
    }

    /** On disconnect. Harmless to skip — see the class note — but it keeps the set honest. */
    public static void clear() {
        MEMBERS.clear();
    }
}
