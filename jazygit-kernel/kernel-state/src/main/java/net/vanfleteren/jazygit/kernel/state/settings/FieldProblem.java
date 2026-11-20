package net.vanfleteren.jazygit.kernel.state.settings;

import io.vavr.collection.List;

/**
 * What is wrong with a field of an entry the form edits, shown next to the field before it is saved (ADR 164). A
 * problem that blocks keeps the entry from being saved; a warning only says so.
 *
 * @param fieldKey   the message key of the label of the field it is about
 * @param messageKey the message key of what is wrong
 * @param args       what goes into that message
 * @param blocks     whether it keeps the entry from being saved
 */
public record FieldProblem(String fieldKey, String messageKey, List<String> args, boolean blocks) {

    /**
     * A problem of the field labelled {@code fieldKey} that keeps the entry from being saved.
     */
    public static FieldProblem error(String fieldKey, String messageKey, String... args) {
        return new FieldProblem(fieldKey, messageKey, List.of(args), true);
    }

    /**
     * A problem of the field labelled {@code fieldKey} that the entry is saved with.
     */
    public static FieldProblem warning(String fieldKey, String messageKey, String... args) {
        return new FieldProblem(fieldKey, messageKey, List.of(args), false);
    }
}
