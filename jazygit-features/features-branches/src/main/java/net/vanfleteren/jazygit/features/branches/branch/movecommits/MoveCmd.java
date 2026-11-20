package net.vanfleteren.jazygit.features.branches.branch.movecommits;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.process.GitFailure;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.branches.branch.BranchCmd;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationMsg;

/**
 * Moving the newest commits of the checked out branch to another branch: reading which commits that are, and moving
 * them.
 */
public sealed interface MoveCmd extends Cmd {

    Operation MOVE = Operation.of("branches.log.title.moveCommits", "branches.error.moveCommits.failed")
            .waitingForUnfinished();

    /**
     * Reading which commits would move, which is reported as this when it fails.
     */
    Operation READ = Operation.of("branches.log.title.moveCommits", "branches.error.moveCommits.readFailed");

    /**
     * Reads the commits of {@code from} from {@code oldest} up to its tip, and how many of them are on
     * {@code upstream} already.
     */
    record ReadRange(BranchName from, Sha oldest, Option<RemoteBranch> upstream) implements MoveCmd {

        @Override
        public Option<Label> label() {
            return Option.of(Label.of("branches.work.range", from.value()));
        }

        @Override
        public Msg run(Env env) {
            return env.git()
                    .history()
                    .commitRange(from, oldest, upstream)
                    .mapLeft(Failure::of)
                    .fold(this::failed, range -> new MoveMsg.RangeRead(from, oldest, range));
        }

        @Override
        public Msg failed(Failure failure) {
            return new MoveMsg.RangeFailed(from, failure);
        }
    }

    /**
     * Moves the commits of {@code from} from {@code oldest} up to its tip to {@code target}; then force-pushes
     * {@code from} to {@code forcePush}, if given.
     */
    record Move(BranchName from, Sha oldest, MoveTarget target, Option<RemoteBranch> forcePush)
            implements MoveCmd, BranchCmd.ChecksOutBranch {

        @Override
        public Operation operation() {
            return MOVE;
        }

        @Override
        public BranchName branch() {
            return target.branch();
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return switch (target) {
                case MoveTarget.NewBranch(var branch, var onto) -> git.commits().move(from, oldest, branch, onto);
                case MoveTarget.Existing(var branch) -> git.commits().moveTo(from, oldest, branch);
            };
        }

        /**
         * Reports a stop on conflicts as one of moving commits, as the branch they are taken off still has them.
         */
        @Override
        public Msg run(Env env) {
            Msg outcome = ChecksOutBranch.super.run(env);
            return outcome instanceof OperationMsg.Stopped(var cmd, var commands, var notice)
                    ? new MoveMsg.Stopped(this, commands, notice)
                    : outcome;
        }

        @Override
        public Msg succeeded(List<ExecutedCommand> commands) {
            return new MoveMsg.Moved(this, commands);
        }
    }
}
