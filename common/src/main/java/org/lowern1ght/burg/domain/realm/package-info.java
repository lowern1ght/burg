/**
 * The Realm bounded context's domain layer — the layer above Town: a
 * metropolis, its expedition-founded colonies, and foreign holdings,
 * each held at an autonomy band. Owned by ADR-0017 (seeded from the
 * behavior/diplomacy + war packages and docs/03-design/realm); the
 * context itself was carved by ADR-0008. Minecraft-free by the
 * DomainPurityTest fence — conversion happens at the SavedData facade
 * edge, not here.
 */
package org.lowern1ght.burg.domain.realm;
