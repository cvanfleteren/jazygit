package net.vanfleteren.jazygit.process;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * How ssh may ask the user something, run by a command that talks to a remote (ADR 150): through jazygit, for as long
 * as the command runs, or not at all.
 */
public interface SshAsking {

    /**
     * Runs {@code talk}, a command in {@code directory} that talks to a remote, with the environment variables it is
     * to set for ssh: those that let ssh ask the user through jazygit while it runs, or those that keep ssh from asking
     * anything where it cannot.
     *
     * @return what {@code talk} gave, and the prompt the user cancelled meanwhile, if any
     */
    <T> Asked<T> asking(Path directory, Function<Map<String, String>, T> talk);

    /**
     * Sets nothing, so ssh runs as the environment of the command has it, and asks nothing through jazygit.
     */
    enum Unchanged implements SshAsking {
        INSTANCE;

        @Override
        public <T> Asked<T> asking(Path directory, Function<Map<String, String>, T> talk) {
            return Asked.of(talk.apply(HashMap.empty()));
        }
    }

    /**
     * What a command that talks to a remote gave, and the prompt of ssh the user cancelled while it ran, if any.
     */
    record Asked<T>(T result, Option<Cancelled> cancelled) {

        /**
         * {@code result}, of a command the user cancelled nothing of.
         */
        public static <T> Asked<T> of(T result) {
            return new Asked<>(result, Option.none());
        }
    }

    /**
     * The prompt of ssh the user cancelled, after which ssh was refused anything else it asked.
     *
     * @param prompt what ssh asked, as it asked it
     */
    record Cancelled(String prompt) {

        /**
         * Whether ssh asked for the passphrase of a key, which the ssh agent could hold instead.
         */
        public boolean passphrase() {
            return prompt.startsWith("Enter passphrase");
        }
    }
}
