package net.vanfleteren.jazygit.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.time.Instant;
import java.util.function.UnaryOperator;

/**
 * Where how long the checks of each repository took is kept, from one session to the next (ADR 30). It is state
 * that jazygit keeps by itself rather than settings: failing to read it gives none, and failing to write it is not
 * worth telling.
 */
public interface CheckTimingsStore {

    /**
     * Keeps nothing: what was learned is only kept in memory.
     */
    CheckTimingsStore NONE = new CheckTimingsStore() {
        @Override
        public Option<List<Run>> load(String repository) {
            return Option.none();
        }

        @Override
        public Try<List<Run>> change(String repository, UnaryOperator<List<Run>> change) {
            return Try.success(change.apply(List.empty()));
        }
    };

    /**
     * The runs kept of {@code repository}, as {@code owner/name}; none when none were kept or they cannot be read.
     */
    Option<List<Run>> load(String repository);

    /**
     * Keeps what {@code change} makes of the runs kept of {@code repository}, as {@code owner/name}, as they are
     * kept now, so that what another jazygit kept meanwhile is not lost; what was kept then.
     */
    Try<List<Run>> change(String repository, UnaryOperator<List<Run>> change);

    /**
     * One run of a check that passed.
     *
     * @param check    the check, as its workflow and its name: {@code Build/verify}
     * @param id       the id of the check run
     * @param duration how long it ran, in seconds
     * @param finished how long after its workflow run was created it was done, in seconds
     * @param event    what started its workflow run
     * @param at       when it was done
     */
    record Run(String check, long id, long duration, long finished, String event, Instant at) {}
}
