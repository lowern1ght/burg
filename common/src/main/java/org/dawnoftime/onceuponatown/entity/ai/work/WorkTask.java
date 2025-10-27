package org.dawnoftime.onceuponatown.entity.ai.work;

import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.entity.ai.controlflow.Task;
import org.dawnoftime.onceuponatown.entity.ai.controlflow.Tasks;

@FunctionalInterface
public interface WorkTask {
    WorkTask NOTHING = () -> Tasks.<Npc>tickingS("Lazy").assemble();
    Task<Npc> create();
}
