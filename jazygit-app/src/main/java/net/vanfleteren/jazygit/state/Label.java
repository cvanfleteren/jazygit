package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;

/**
 * What a command that runs shows as (ADR 33): the message key of its text, with what it is given. An argument that
 * is a label is shown as its text, like the title of an operation.
 *
 * @param key  the message key
 * @param args what the message is given, in order: strings, or labels
 */
public record Label(String key, List<Object> args) {

    /**
     * What a command that has no label of its own shows.
     */
    public static final Label WORKING = of("work.working");

    public static Label of(String key, Object... args) {
        return new Label(key, List.of(args));
    }
}
