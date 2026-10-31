package net.vanfleteren.jazygit.settings;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Try;
import java.time.Duration;
import java.time.Instant;

/**
 * Where the review threads and comments collapsed or opened against their default are kept, per pull request, from
 * one session to the next (ADR 62, 63). It is state that jazygit keeps by itself rather than settings: failing to read
 * it gives none, and failing to write it is not worth telling.
 */
public interface CollapsedStore {

    /**
     * How long the flips of a pull request are kept after the last of them.
     */
    Duration KEPT = Duration.ofDays(30);

    /**
     * Keeps nothing: what was flipped is only kept in memory.
     */
    CollapsedStore NONE = new CollapsedStore() {
        @Override
        public Map<String, Boolean> load(String repository, int number) {
            return HashMap.empty();
        }

        @Override
        public Try<Map<String, Boolean>> save(String repository, int number, Map<String, Boolean> flipped, Instant at) {
            return Try.success(flipped);
        }
    };

    /**
     * The ids of the threads and the comments of pull request {@code number} of {@code repository}, as
     * {@code owner/name}, flipped against their default, each with whether that default was collapsed; none when none
     * were kept or they cannot be read.
     */
    Map<String, Boolean> load(String repository, int number);

    /**
     * Keeps {@code flipped} as the ids flipped of pull request {@code number} of {@code repository}, flipped at
     * {@code at}, and forgets those of the pull requests none was flipped of for {@link #KEPT} before it.
     */
    Try<Map<String, Boolean>> save(String repository, int number, Map<String, Boolean> flipped, Instant at);
}
