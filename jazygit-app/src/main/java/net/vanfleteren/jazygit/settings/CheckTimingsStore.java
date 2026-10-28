package net.vanfleteren.jazygit.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;
import io.vavr.control.Try;
import java.time.Instant;
import java.util.function.UnaryOperator;
import lombok.With;

/**
 * Where how long the checks of each repository took is kept, from one session to the next (ADR 32). It is state
 * that jazygit keeps by itself rather than settings: failing to read it gives none, and failing to write it is not
 * worth telling.
 */
public interface CheckTimingsStore {

    /**
     * Keeps nothing: what was learned is only kept in memory.
     */
    CheckTimingsStore NONE = new CheckTimingsStore() {
        @Override
        public Option<Kept> load(String repository) {
            return Option.none();
        }

        @Override
        public Try<Kept> change(String repository, UnaryOperator<Kept> change) {
            return Try.success(change.apply(Kept.EMPTY));
        }
    };

    /**
     * What is kept of {@code repository}, as {@code owner/name}; none when nothing was kept or it cannot be read.
     */
    Option<Kept> load(String repository);

    /**
     * Keeps what {@code change} makes of what is kept of {@code repository}, as {@code owner/name}, as it is kept
     * now, so that what another jazygit kept meanwhile is not lost; what was kept then.
     */
    Try<Kept> change(String repository, UnaryOperator<Kept> change);

    /**
     * What is kept of one repository.
     *
     * @param runs     the runs of its checks
     * @param toppedUp when the checks that ran lately were last read from GitHub, or that was tried; none when never
     */
    record Kept(@With List<Run> runs, Option<Instant> toppedUp) {

        public static final Kept EMPTY = new Kept(List.empty(), Option.none());

        public Kept withToppedUp(Instant toppedUp) {
            return new Kept(runs, Option.of(toppedUp));
        }
    }

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
