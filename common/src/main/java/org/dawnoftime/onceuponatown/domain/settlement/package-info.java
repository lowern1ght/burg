/**
 * Settlement bounded context — domain layer (ADR-0008).
 *
 * <p>One town's life: buildings, construction queue, production, standing,
 * quests. {@code Town} is the aggregate root of this context. The domain
 * layer is pure model: no {@code net.minecraft}, no NeoForge, no I/O —
 * Minecraft-native concepts cross the boundary only as value-object
 * wrappers ({@code TownId}, {@code BlockCoord}, {@code CitizenId},
 * {@code ItemId}) converted at the infrastructure edge.</p>
 *
 * <p>Empty skeleton: {@code Town.java} still lives in {@code town/} and is
 * migrated here incrementally (strangler, one carve per change).</p>
 */
package org.dawnoftime.onceuponatown.domain.settlement;
