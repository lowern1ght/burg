/**
 * Settlement bounded context — application layer (ADR-0008).
 *
 * <p>Use cases and orchestration for one town's life (advance
 * construction, run production tick, evaluate standing). Depends on
 * {@code domain.settlement} only; talks to the outside world through
 * ports (interfaces) implemented in {@code infrastructure}.</p>
 *
 * <p>Empty skeleton: {@code tick/} managers migrate here one carve per
 * change.</p>
 */
package org.dawnoftime.onceuponatown.application.settlement;
