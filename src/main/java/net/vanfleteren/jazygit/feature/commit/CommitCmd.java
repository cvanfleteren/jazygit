package net.vanfleteren.jazygit.feature.commit;

import net.vanfleteren.jazygit.git.GitCommandException;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Msg;
import java.util.List;
import java.util.Optional;

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
        public Optional<String> logTitleKey() {
            return Optional.of("log.title.stage");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return new CommitMsg.StagedForCommit(git.stage(paths));
        }

        @Override
        public Msg failed(String message) {
            return new CommitMsg.StageForCommitFailed(message);
        }
    }

    record Commit(String summary, String description) implements CommitCmd {

        @Override
        public Optional<String> logTitleKey() {
            return Optional.of("log.title.commit");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return new CommitMsg.Done(git.commit(summary, description));
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
        public Optional<String> logTitleKey() {
            return Optional.of("log.title.amend");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return new AmendMsg.Done(GitCommandException.chain(() -> git.stage(stage), git::amendLastCommit));
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
        public Optional<String> logTitleKey() {
            return Optional.of("log.title.reword");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return new RewordMsg.Done(git.reword(summary, description));
        }

        @Override
        public Msg failed(String message) {
            return new RewordMsg.Failed(message);
        }
    }
}
