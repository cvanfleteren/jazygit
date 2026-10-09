package net.vanfleteren.jazygit.feature.stage;

import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Msg;
import java.util.List;

/**
 * Commands that change what is staged.
 */
public sealed interface StageCmd extends Cmd {

    record Stage(List<String> paths) implements StageCmd {

        public Stage {
            paths = List.copyOf(paths);
        }

        @Override
        public Msg run(GitInfoProvider git) {
            git.stage(paths);
            return new StageMsg.Done();
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

        public Unstage {
            added = List.copyOf(added);
            others = List.copyOf(others);
        }

        @Override
        public Msg run(GitInfoProvider git) {
            git.unstageNew(added);
            git.unstage(others);
            return new StageMsg.Done();
        }

        @Override
        public Msg failed(String message) {
            return new StageMsg.Failed(message);
        }
    }
}
