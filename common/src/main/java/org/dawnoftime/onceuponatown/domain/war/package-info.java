/**
 * War bounded context — domain layer (ADR-0008).
 *
 * <p>Realm-vs-realm NPC combat at the scale of ADR-0004/ADR-0005: the
 * player commands and supplies, never fights (war-scale is always
 * NPC-vs-NPC). Pure model — no Minecraft or NeoForge types.</p>
 *
 * <p>Empty skeleton: the battle state machine (phase 8) still lives in
 * {@code behavior/war/} and moves when the strangler reaches it.</p>
 */
package org.dawnoftime.onceuponatown.domain.war;
