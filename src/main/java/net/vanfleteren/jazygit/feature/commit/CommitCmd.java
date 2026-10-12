package net.vanfleteren.jazygit.feature.commit;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Msg;

/**
 * Commands that create or rewrite commits.
 */
public sealed interface CommitCmd extends Cmd {

    /**
     * Stages the paths so they can be committed; the commit dialog opens afterwards.
     */
    record StageForCommit(List<String> paths) implements CommitCmd {

        @Override
        public Option<String> logTitleKey() {
            return Option.of("log.title.stage");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return git.stage(paths).fold(this::failedWith, CommitMsg.StagedForCommit::new);
        }

        @Override
        public Msg failed(String message) {
            return new CommitMsg.StageForCommitFailed(message);
        }
    }

    record Commit(String summary, String description) implements CommitCmd {

        @Override
        public Option<String> logTitleKey() {
            return Option.of("log.title.commit");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return git.commit(new CommitMessage(summary, description)).fold(this::failedWith, CommitMsg.Done::new);
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

        @Override
        public Option<String> logTitleKey() {
            return Option.of("log.title.amend");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return GitFailure.<ProcessError>sequence(() -> git.stage(stage), git::amendLastCommit)
                    .fold(this::failedWith, AmendMsg.Done::new);
        }

        @Override
        public Msg failed(String message) {
            return new AmendMsg.Failed(message);
        }
    }

    /**
     * Squashes a commit into its parent.
     */
    record Squash(Sha sha) implements CommitCmd {

        @Override
        public Option<String> logTitleKey() {
            return Option.of("log.title.squash");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return git.squash(sha).fold(this::failedWith, SquashMsg.Done::new);
        }

        @Override
        public Msg failed(String message) {
            return new SquashMsg.Failed(message);
        }
    }

    /**
     * Replaces the message of the last commit.
     */
    record Reword(Sha sha, String summary, String description) implements CommitCmd {

        @Override
        public Option<String> logTitleKey() {
            return Option.of("log.title.reword");
        }

        @Override
        public Msg run(GitInfoProvider git) {
            return git.reword(sha, new CommitMessage(summary, description)).fold(this::failedWith, RewordMsg.Done::new);
        }

        @Override
        public Msg failed(String message) {
            return new RewordMsg.Failed(message);
        }
    }
}
