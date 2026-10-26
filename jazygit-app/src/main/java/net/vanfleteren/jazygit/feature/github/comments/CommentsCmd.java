package net.vanfleteren.jazygit.feature.github.comments;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Reading the conversation on a pull request.
 */
public sealed interface CommentsCmd extends Cmd {

    /**
     * Reads the description, comments and reviews of pull request {@code number}.
     */
    record LoadComments(int number) implements CommentsCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("work.comments", String.valueOf(number)));
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub()
                    .pullRequestComments(number)
                    .fold(this::failedWith, comments -> new CommentsMsg.Loaded(number, comments));
        }

        @Override
        public Msg failed(Failure failure) {
            return new CommentsMsg.LoadFailed(number, failure);
        }
    }
}
