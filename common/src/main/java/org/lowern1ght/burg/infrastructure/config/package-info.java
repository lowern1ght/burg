/**
 * Bounded-context landing zone — see ADR-0008. Config classes live here so
 * the persistence / IO layer is the only one that knows about NeoForge
 * (domain and the bare-JVM people simulation stay free of it).
 */
package org.lowern1ght.burg.infrastructure.config;
