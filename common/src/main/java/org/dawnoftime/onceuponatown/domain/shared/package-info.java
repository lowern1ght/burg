/**
 * Content shared kernel — domain layer (ADR-0008).
 *
 * <p>Shared definitions consumed by every bounded context: building,
 * era, and quest definitions (the datapack contract behind the five
 * JSON handlers, see {@code docs/04-engineering/DATA-FORMATS.md}).
 * Pure model — no Minecraft or NeoForge types; item identity enters as
 * {@code ItemId}, not {@code Holder<Item>}.</p>
 *
 * <p>Empty skeleton: the handlers stay in {@code datapack/} until the
 * strangler reaches them.</p>
 */
package org.dawnoftime.onceuponatown.domain.shared;
