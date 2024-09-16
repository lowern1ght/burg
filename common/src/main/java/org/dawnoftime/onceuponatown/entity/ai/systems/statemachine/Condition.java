package org.dawnoftime.onceuponatown.entity.ai.systems.statemachine;

@FunctionalInterface
public interface Condition {
    boolean validate();
}
