package net.vanfleteren.jazygit.feature.commit;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.CommitMessage;
import net.vanfleteren.jazygit.git.model.ResetMode;
import net.vanfleteren.jazygit.git.model.ResetTarget;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.git.model.SquashMode;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.GitOperationCmd;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;

/**
 * Commands that create or rewrite commits.
 */
public sealed interface CommitCmd extends GitOperationCmd {

    /**
     * Stages the paths so they can be committed; the commit dialog opens afterwards.
     */
    record StageForCommit(List<String> paths) implements CommitCmd {

        @Override
        public Operation operation() {
            return Operation.STAGE;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.stage(paths);
        }

        @Override
        public Msg succeeded(List<ExecutedCommand> commands) {
            return new CommitMsg.StagedForCommit(this, commands);
        }
    }

    record Commit(CommitMessage message) implements CommitCmd {

        @Override
        public Operation operation() {
            return Operation.COMMIT;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.commit(message);
        }
    }

    /**
     * Stages the paths, if any, then amends the last commit with the index, keeping its message.
     */
    record Amend(List<String> stage) implements CommitCmd {

        @Override
        public Operation operation() {
            return Operation.AMEND;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return GitFailure.sequence(() -> git.stage(stage), git::amendLastCommit);
        }
    }

    /**
     * Stages the paths, if any, then amends the commit {@code sha} with the index, keeping its message.
     */
    record AmendCommit(Sha sha, List<String> stage) implements CommitCmd {

        @Override
        public Operation operation() {
            return Operation.AMEND_COMMIT;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return GitFailure.<GitError>sequence(() -> GitFailure.widen(git.stage(stage)), () -> git.amendCommit(sha));
        }
    }

    /**
     * Folds a commit into its parent: a squash, or a fixup when {@code mode} drops either message.
     */
    record Squash(Sha sha, SquashMode mode) implements CommitCmd {

        @Override
        public Operation operation() {
            return mode == SquashMode.SQUASH ? Operation.SQUASH : Operation.FIXUP;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.squash(sha, mode);
        }
    }

    /**
     * Removes a commit from the history of the checked out branch.
     */
    record Drop(Sha sha) implements CommitCmd {

        @Override
        public Operation operation() {
            return Operation.DROP;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.dropCommit(sha);
        }
    }

    /**
     * Replaces the message of a commit.
     */
    record Reword(Sha sha, CommitMessage message) implements CommitCmd {

        @Override
        public Operation operation() {
            return Operation.REWORD;
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.reword(sha, message);
        }
    }

    /**
     * Moves the checked out branch to {@code target}, doing with the changes {@code mode} says.
     */
    record Reset(ResetTarget target, ResetMode mode) implements CommitCmd {

        @Override
        public Operation operation() {
            return Operation.RESET;
        }

        @Override
        public Option<String> subject() {
            return Option.of(target.label());
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return git.reset(target, mode);
        }
    }
}
