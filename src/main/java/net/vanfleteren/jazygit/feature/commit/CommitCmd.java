package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Msg;
import java.util.List;

/**
 * Commands that create or rewrite commits.
 */
public sealed interface CommitCmd extends Cmd {

    /**
     * Stages the paths so they can be committed; the commit dialog opens afterwards.
     */
    record StageForCommit(List<String> paths) implements CommitCmd {

        public StageForCommit {
            paths = List.copyOf(paths);
        }

        @Override
        public Msg run(GitInfoProvider git) {
            git.stage(paths);
            return new CommitMsg.StagedForCommit();
        }

        @Override
        public Msg failed(String message) {
            return new CommitMsg.StageForCommitFailed(message);
        }
    }

    record Commit(String summary, String description) implements CommitCmd {

        @Override
        public Msg run(GitInfoProvider git) {
            git.commit(summary, description);
            return new CommitMsg.Done();
        }

        @Override
        public Msg failed(String message) {
            return new CommitMsg.Failed(message);
        }
    }

    /**
     * Stages the paths, if any, then amends the last commit with the index, keeping its message.
     */
    record Amend(List<String> stage) implements CommitCmd {

        public Amend {
            stage = List.copyOf(stage);
        }

        @Override
        public Msg run(GitInfoProvider git) {
            git.stage(stage);
            git.amendLastCommit();
            return new AmendMsg.Done();
        }

        @Override
        public Msg failed(String message) {
            return new AmendMsg.Failed(message);
        }
    }

    /**
     * Replaces the message of the last commit.
     */
    record Reword(String summary, String description) implements CommitCmd {

        @Override
        public Msg run(GitInfoProvider git) {
            git.reword(summary, description);
            return new RewordMsg.Done();
        }

        @Override
        public Msg failed(String message) {
            return new RewordMsg.Failed(message);
        }
    }
}
