/**
 * Settlement bounded context — vanilla-village conversion slice.
 *
 * <p>The vanilla-village-conversion change (proposal:
 * {@code openspec/changes/vanilla-village-conversion/proposal.md}, ADR-0020)
 * introduces a third entry point for a town: the Town Anchor placed at the
 * meeting point of an unregistered vanilla village. The pure decision
 * logic — given a set of vanilla house footprints and a candidate anchor
 * position, is this a valid conversion target — lives here as a
 * Minecraft-free value-object API.
 *
 * <p>The Minecraft-aware entry path (POI scans, NBT placement, villager
 * enlistment, blocked-zone registration) is the {@code Town.bindToVanillaVillage}
 * facade in {@code town.Town}; this package gives it a place to ask
 * "is this candidate inside a vanilla village?" without dragging a
 * {@code ServerLevel} into the domain layer.
 *
 * <p>JDK only, no {@code net.minecraft} imports (ADR-0008 §"Layers inside
 * each context" + the {@code DomainPurityTest} fence).
 */
package org.lowern1ght.burg.domain.settlement.vanilla;