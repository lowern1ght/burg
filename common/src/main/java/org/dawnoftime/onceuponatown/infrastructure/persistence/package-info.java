/**
 * Infrastructure layer — persistence adapters (ADR-0008).
 *
 * <p>Implements the ports the application layers declare, on the
 * Minecraft side: the {@code ouat_towns} SavedData repository, the
 * NBT mapping for domain objects, and the value-object conversions
 * ({@code BlockPos} ↔ {@code BlockCoord} and friends) at the edge.
 * The NBT shape is frozen for the duration of the strangler migration
 * — old worlds must load unchanged.</p>
 *
 * <p>Empty skeleton: {@code town/LevelTowns} remains the live
 * persistence path until the strangler reaches it.</p>
 */
package org.dawnoftime.onceuponatown.infrastructure.persistence;
