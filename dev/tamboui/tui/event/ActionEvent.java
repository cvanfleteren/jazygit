/*
 * Copyright TamboUI Contributors
 * SPDX-License-Identifier: MIT
 */
package dev.tamboui.tui.event;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a programmatically fired action, as opposed to a raw input event.
 * <p>
 * ActionEvent is created when an action is fired via
 * {@link dev.tamboui.tui.bindings.ActionHandler#fire(String)} without an
 * originating input event. It carries the action name that was fired.
 *
 * @param <T> the type of the context associated with this action, if any
 */
public final class ActionEvent<T> implements Event {

    private final String action;
    private final T context;

    /**
     * Creates an action event for the given action name.
     *
     * @param action the action name that was fired
     */
    private ActionEvent(String action, T context) {
        this.action = action;
        this.context = context;
    }

    /**
     * Creates an action event for the given action name and context.
     *
     * @param action the action name that was fired
     * @param context the context associated with the action
     * @param <T> the type of the context
     *
     * @return an action event containing the action name and context
     */
    public static <T> ActionEvent<T> of(String action, T context) {
        return new ActionEvent<>(action, context);
    }

    /**
     * Creates an action event for the given action name with no context.
     *
     * @param action the action name that was fired
     *
     * @return an action event for the action name
     */
    public static ActionEvent<Void> of(String action) {
        return new ActionEvent<>(action, null);
    }

    /**
     * Returns the action name that was fired.
     *
     * @return the action name
     */
    public String action() {
        return action;
    }

    /**
     * Returns the context associated with this action, if any.
     *
     * @return an optional containing the context, or empty if no context
     */
    public Optional<T> context() {
        return Optional.ofNullable(context);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ActionEvent)) {
            return false;
        }

        ActionEvent<?> that = (ActionEvent<?>) o;
        return action.equals(that.action) && Objects.equals(context, that.context);
    }

    @Override
    public int hashCode() {
        int result = action.hashCode();
        result = 31 * result + Objects.hashCode(context);
        return result;
    }

    @Override
    public String toString() {
        return "ActionEvent{" +
                "action='" + action + '\'' +
                ", context=" + context +
                '}';
    }
}