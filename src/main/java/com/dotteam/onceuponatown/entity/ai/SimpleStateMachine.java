package com.dotteam.onceuponatown.entity.ai;

import java.util.ArrayList;
import java.util.List;

public class SimpleStateMachine {
    private List<State> states = new ArrayList<>();
    private List<Transition> transitions = new ArrayList<>();
    private State initialState;
    private State currentState;

    public SimpleStateMachine(State... states) {
        for (State state : states) {
            if (!this.states.contains(state)) {
                this.states.add(state);
            }
        }
        currentState = initialState = states[0];
    }

    public SimpleStateMachine setInitialState(State initialState) {
        this.initialState = initialState;
        return this;
    }

    public SimpleStateMachine addTransition(State origin, State destination, Condition condition) {
        this.transitions.add(new Transition(origin, destination, condition));
        return this;
    }

    public void tick() {
        updateState();
        if (currentState != null) {
            currentState.tick();
        }
    }

    public void updateState() {
        List<Transition> toConsider = new ArrayList<>();
        for (Transition transition : transitions) {
            if (transition.getOrigin() == currentState) {
                toConsider.add(transition);
            }
        }
        for (Transition transition : toConsider) {
            if (transition.test()) {
                currentState.stop();
                currentState = transition.getDestination();
                currentState.start();
                return;
            }
        }
    }

    public void reset() {
        currentState = initialState;
    }

    public static class Transition {
        private State origin;
        private State destination;
        private Condition condition;

        public Transition(State origin, State destination, Condition condition) {
            this.origin = origin;
            this.destination = destination;
            this.condition = condition;
        }

        private boolean test() {
            return condition.test();
        }

        private State getOrigin() {
            return origin;
        }

        private State getDestination() {
            return destination;
        }
    }

    public static class State {
        private Action tickCode;
        private Action startCode;
        private Action stopCode;

        public State(Action tickCode) {
            this.tickCode = tickCode;
        }

        public State onStart(Action startCode) {
            this.startCode = startCode;
            return this;
        }

        public State onStop(Action stopCode) {
            this.stopCode = stopCode;
            return this;
        }

        private void tick() {
            if (tickCode != null) {
                tickCode.execute();
            }
        }

        private void start() {
            if (startCode != null) {
                startCode.execute();
            }
        }

        private void stop() {
            if (stopCode != null) {
                stopCode.execute();
            }
        }
    }

    @FunctionalInterface
    public interface Condition {
        boolean test();
    }

    @FunctionalInterface
    public interface Action {
        void execute();
    }
}
