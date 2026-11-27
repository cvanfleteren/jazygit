package net.vanfleteren.jazygit.features.github.remarks;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Try;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.core.repo.PrNumber;
import net.vanfleteren.jazygit.core.repo.RepoSlug;
import net.vanfleteren.jazygit.kernel.state.Env;

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
        public Map<String, Boolean> load(RepoSlug repository, PrNumber number) {
            return HashMap.empty();
        }

        @Override
        public Try<Map<String, Boolean>> save(
                RepoSlug repository, PrNumber number, Map<String, Boolean> flipped, Instant at) {
            return Try.success(flipped);
        }
    };

    /**
     * Where the review threads and comments collapsed or opened are kept (ADR 63, 169): nowhere until one is given.
     */
    Env.Service<CollapsedStore> SERVICE = Env.Service.of("github.collapsed", NONE);

    /**
     * The ids of the threads and the comments of pull request {@code number} of {@code repository}, as
     * {@code owner/name}, flipped against their default, each with whether that default was collapsed; none when none
     * were kept or they cannot be read.
     */
    Map<String, Boolean> load(RepoSlug repository, PrNumber number);

    /**
     * Keeps {@code flipped} as the ids flipped of pull request {@code number} of {@code repository}, flipped at
     * {@code at}, and forgets those of the pull requests none was flipped of for {@link #KEPT} before it.
     */
    Try<Map<String, Boolean>> save(RepoSlug repository, PrNumber number, Map<String, Boolean> flipped, Instant at);
}
