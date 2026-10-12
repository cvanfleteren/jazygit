package net.vanfleteren.jazygit.feature.stage;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitCommandException;
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
            return new StageMsg.Done(StageMsg.Action.STAGE, git.stage(paths));
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
            return new StageMsg.Done(StageMsg.Action.UNSTAGE,
                    GitCommandException.chain(() -> git.unstageNew(added), () -> git.unstage(others)));
        }

        @Override
        public Msg failed(String message) {
            return new StageMsg.Failed(message);
        }
    }
}
