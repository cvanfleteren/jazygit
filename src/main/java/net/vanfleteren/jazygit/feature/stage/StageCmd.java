package net.vanfleteren.jazygit.feature.stage;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Commands that change what is staged.
 */
public sealed interface StageCmd extends Cmd {

    record Stage(List<String> paths) implements StageCmd {

        @Override
        public Option<String> logTitleKey() {
            return Option.of("log.title.stage");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return git.stage(paths).fold(this::failedWith, executed -> new StageMsg.Done(StageMsg.Action.STAGE, executed));
        }

        @Override
        public Msg failed(String message) {
            return new StageMsg.Failed(message);
        }
    }

    /**
     * Takes paths out of the index: the {@code added} ones (not in HEAD) become untracked again, the
     * {@code others} are reset to HEAD.
     */
    record Unstage(List<String> added, List<String> others) implements StageCmd {

        @Override
        public Option<String> logTitleKey() {
            return Option.of("log.title.unstage");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return GitFailure.<ProcessError>sequence(() -> git.unstageNew(added), () -> git.unstage(others))
                    .fold(this::failedWith, executed -> new StageMsg.Done(StageMsg.Action.UNSTAGE, executed));
        }

        @Override
        public Msg failed(String message) {
            return new StageMsg.Failed(message);
        }
    }
}
