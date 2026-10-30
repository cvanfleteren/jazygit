package net.vanfleteren.jazygit.feature.diff;

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
     * (ADR 55). It runs in the background, as it talks to GitHub.
     */
    record LoadReviewRemarks(int number) implements CommitFilesCmd {

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
                    .fold(this::failedWith, remarks -> new CommitFilesMsg.RemarksLoaded(number, remarks));
        }

        @Override
        public Msg failed(Failure failure) {
            return new CommitFilesMsg.RemarksFailed(number, failure);
        }
    }
}
