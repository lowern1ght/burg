package org.lowern1ght.burg.domain.settlement;

import java.util.Objects;

/**
 * A reference to a quest in a town's quest log — the Minecraft-free
 * projection of {@code town.Quest} that the domain layer can reason about
 * without a {@code net.minecraft} import on the classpath.
 *
 * <p>Two strings identify the quest on the wire:
 * <ul>
 *   <li>{@code defId} — the quest definition id (the {@code id} field of
 *       the datapack-loaded {@code QuestDef}); the stable identity that
 *       crosses reload boundaries and survives a quest completion.</li>
 *   <li>{@code type} — the quest kind, drawn from the shipped {@code
 *       QuestDef.type} field. Two kinds appear in the corpus today:
 *       {@code "NOTE"} (a side-note / lore, no progress bar) and
 *       {@code "TASK"} (a deliverable with {@code conditions} and
 *       possibly a reward). The type is preserved as a string here
 *       rather than turned into an enum because the engine treats it
 *       opaquely and the domain layer only needs to read it back.</li>
 *   <li>{@code status} — optional, may be {@code null}. A {@code NOTE}
 *       has no status; a {@code TASK} is either {@code "ACTIVE"} (it
 *       sits in {@code Town.activeQuests}) or {@code "COMPLETED"} (it
 *       is no longer active but its last-completion tick is recorded
 *       in the {@code lastCompleted} map). The status is data, not
 *       logic: the engine decides which strings are valid; the domain
 *       only stores what the engine handed it.</li>
 * </ul>
 *
 * <p>The record is immutable; equality is structural on the three
 * components. Two refs for the same {@code defId} with different types
 * or statuses are different refs — the {@code QuestLog} keys everything
 * off {@code defId}, so consumers that key off {@code defId} alone
 * should remember the roll is not a multi-map.
 *
 * <p>No Minecraft imports. Conversion to and from the legacy
 * {@code town.Quest} happens at the {@code Town} facade edge in
 * {@code Town.questLog()}.
 */
public record QuestRef(String defId, String type, String status) {

    /** The quest type used by shipped TASK definitions. */
    public static final String TYPE_TASK = "TASK";

    /** The quest type used by shipped NOTE definitions (side-note / lore). */
    public static final String TYPE_NOTE = "NOTE";

    /** Status reserved for a TASK currently in {@code Town.activeQuests}. */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /** Status reserved for a TASK whose defId has a {@code lastCompleted} tick. */
    public static final String STATUS_COMPLETED = "COMPLETED";

    public QuestRef {
        Objects.requireNonNull(defId, "defId");
        Objects.requireNonNull(type, "type");
        // status is intentionally nullable — a NOTE has no status.
    }

    /** Builds a ref with a status (a TASK in {@code ACTIVE} or {@code COMPLETED}). */
    public static QuestRef of(String defId, String type, String status) {
        return new QuestRef(defId, type, status);
    }

    /** Builds a ref without a status (e.g. a NOTE). */
    public static QuestRef ofUnstatused(String defId, String type) {
        return new QuestRef(defId, type, null);
    }

    /** True iff the ref carries a status string. */
    public boolean hasStatus() {
        return status != null;
    }

    /** True iff the quest type is {@code "TASK"} (the only type that tracks completion). */
    public boolean isTask() {
        return TYPE_TASK.equals(type);
    }

    /** True iff the quest type is {@code "NOTE"} (side-note / lore). */
    public boolean isNote() {
        return TYPE_NOTE.equals(type);
    }
}
