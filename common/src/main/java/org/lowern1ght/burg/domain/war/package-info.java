/**
 * The War bounded context's domain layer — realm-vs-realm combat at
 * war scale (ADR-0004/0005). Owned by ADR-0017 (thin seed; the battle
 * state machine in behavior.war is deliberately untouched); the
 * context itself was carved by ADR-0008. Minecraft-free by the
 * DomainPurityTest fence — the in-game engine adapts into these types
 * at the behavior edge.
 */
package org.lowern1ght.burg.domain.war;
