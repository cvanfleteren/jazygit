package net.vanfleteren.jazygit.feature.diff;

import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;
import org.slf4j.LoggerFactory;

/**
 * What the files of a pull request read besides them.
 */
public sealed interface CommitFilesCmd extends Cmd {

    /**
     * Reads the review threads of pull request {@code number}, with the commits its changes are diffed between
     * (ADR 55). It runs in the background, as it talks to GitHub. It is read for the threads {@code settled} too:
     * those something was sent for, which it is read after (ADR 60). It is read at {@code version} of the threads,
     * which it gives back: one changed since, as by a comment deleted, is newer than what it read (ADR 64).
     */
    record LoadReviewRemarks(int number, Set<String> settled, int version) implements CommitFilesCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.reviewRemarks", String.valueOf(number)));
        }

        @Override
        public boolean background() {
            return true;
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub()
                    .reviewRemarks(number)
                    .fold(
                            this::failedWith,
                            remarks -> new CommitFilesMsg.RemarksLoaded(number, remarks, settled, version));
        }

        @Override
        public Msg failed(Failure failure) {
            return new CommitFilesMsg.RemarksFailed(number, failure, settled, version);
        }
    }

    /**
     * Reads the review threads and comments of pull request {@code number} of {@code repository}, as
     * {@code owner/name}, that were collapsed or opened against their default in an earlier session (ADR 63).
     */
    record LoadCollapsed(String repository, int number) implements CommitFilesCmd {

        @Override
        public Msg run(Env env) {
            return new CommitFilesMsg.CollapsedLoaded(number, env.collapsed().load(repository, number));
        }

        // Nothing kept is as nothing flipped.
        @Override
        public Msg failed(Failure failure) {
            return new CommitFilesMsg.CollapsedKept();
        }
    }

    /**
     * Keeps {@code flipped} as the review threads and comments of pull request {@code number} of {@code repository},
     * as {@code owner/name}, collapsed or opened against their default, flipped at {@code at}, for the next session
     * (ADR 63). That they could not be kept is only written to the debug log: they are only a convenience.
     */
    record SaveCollapsed(String repository, int number, Map<String, Boolean> flipped, Instant at)
            implements CommitFilesCmd {

        @Override
        public Msg run(Env env) {
            env.collapsed()
                    .save(repository, number, flipped, at)
                    .onFailure(failed -> LoggerFactory.getLogger(SaveCollapsed.class)
                            .debug("The review threads collapsed or opened could not be kept", failed));
            return new CommitFilesMsg.CollapsedKept();
        }

        @Override
        public Msg failed(Failure failure) {
            return new CommitFilesMsg.CollapsedKept();
        }
    }
}
