/**
 * Infrastructure layer — NeoForge adapters (ADR-0008).
 *
 * <p>Implements application ports against the NeoForge runtime: event
 * bus subscriptions, packet payloads, registry wiring, tick hooks.
 * Depends on domain + application; nothing depends on it from the
 * inside. Keeps the loader-facing surface thin so the {@code common/}
 * module stays loader-agnostic.</p>
 *
 * <p>Empty skeleton: the current {@code neoforge/} Gradle module keeps
 * its role; this package is where common-side adapters land.</p>
 */
package org.dawnoftime.onceuponatown.infrastructure.neoforge;
