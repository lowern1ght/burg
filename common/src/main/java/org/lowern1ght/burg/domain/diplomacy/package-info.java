/**
 * The Diplomacy bounded context's domain layer — relations between
 * realms once more than one exists. Owned by ADR-0017 (seeded from
 * behavior/diplomacy and docs/03-design/diplomacy); the context itself
 * was carved by ADR-0008. The town-scale DiplomaticStatus engine in
 * {@code behavior.diplomacy} keeps running; realm-scale types land here
 * first and adapters bridge them later. Minecraft-free by the
 * DomainPurityTest fence.
 */
package org.lowern1ght.burg.domain.diplomacy;
