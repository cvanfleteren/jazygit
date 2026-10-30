package net.vanfleteren.jazygit.feature.diff;

import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;

/**
 * What the files of a pull request read besides them.
 */
public sealed interface CommitFilesCmd extends Cmd {

    /**
     * Reads the review threads of pull request {@code number}, with the commits its changes are diffed between
     * (ADR 55). It runs in the background, as it talks to GitHub. It is read for the threads {@code settled} too:
     * those something was sent for, which it is read after (ADR 60).
     */
    record LoadReviewRemarks(int number, Set<String> settled) implements CommitFilesCmd {

        public LoadReviewRemarks(int number) {
            this(number, HashSet.empty());
        }

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
                    .fold(this::failedWith, remarks -> new CommitFilesMsg.RemarksLoaded(number, remarks, settled));
        }

        @Override
        public Msg failed(Failure failure) {
            return new CommitFilesMsg.RemarksFailed(number, failure, settled);
        }
    }
}
