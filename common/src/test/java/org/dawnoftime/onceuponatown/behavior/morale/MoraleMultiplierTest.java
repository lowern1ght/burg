package org.dawnoftime.onceuponatown.behavior.morale;

import org.dawnoftime.onceuponatown.behavior.task.CitizenTask;
import org.dawnoftime.onceuponatown.behavior.task.IdleTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoraleMultiplierTest {

    @Test
    @DisplayName("a task without an assignee ignores morale")
    void missingAssigneeReturnsOne() {
        CitizenTask task = new IdleTask(null);

        float multiplier = task.moraleMultiplier(new MoraleState(), null);

        assertEquals(1.0f, multiplier);
    }
}
