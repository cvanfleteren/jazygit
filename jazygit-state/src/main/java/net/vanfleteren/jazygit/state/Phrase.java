package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;

/**
 * What the user is told, as data: the key of a message, and what goes into it. The view looks the message up in the
 * user's language; the state never makes text itself. An {@link AppError} says itself as one.
 */
public sealed interface Phrase {

    /**
     * The message {@code key}, which takes nothing.
     */
    static Phrase of(String key) {
        return new Keyed(key, List.empty());
    }

    /**
     * The message {@code key}, given words that are shown as they are, such as a branch name or a path.
     */
    static Phrase of(String key, String first, String... rest) {
        return new Keyed(key, List.of(rest).prepend(first).map(Verbatim::new));
    }

    /**
     * The message {@code key}, given {@code args}.
     */
    static Phrase of(String key, Phrase... args) {
        return new Keyed(key, List.of(args));
    }

    /**
     * {@code text}, shown as it is.
     */
    static Phrase verbatim(String text) {
        return new Verbatim(text);
    }

    /**
     * {@code count}, which the message formats as a number.
     */
    static Phrase count(int count) {
        return new Count(count);
    }

    /**
     * What {@code failure} says, as the view says failures.
     */
    static Phrase failure(Failure failure) {
        return new Failed(failure);
    }

    /**
     * Each of {@code parts}, one after the other, with {@code separator} between them.
     */
    static Phrase joined(String separator, List<Phrase> parts) {
        return new Joined(separator, parts);
    }

    /**
     * The message {@code key}, given {@code args}, each a phrase of its own.
     */
    record Keyed(String key, List<Phrase> args) implements Phrase {}

    /**
     * Words shown as they are: a name, a path, what git or GitHub said.
     */
    record Verbatim(String text) implements Phrase {}

    /**
     * A number, which the message formats.
     */
    record Count(int count) implements Phrase {}

    /**
     * What a failure says.
     */
    record Failed(Failure failure) implements Phrase {}

    /**
     * Phrases one after the other, {@code separator} between them.
     */
    record Joined(String separator, List<Phrase> parts) implements Phrase {}
}
