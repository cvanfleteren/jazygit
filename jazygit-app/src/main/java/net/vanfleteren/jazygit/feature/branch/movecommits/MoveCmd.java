package net.vanfleteren.jazygit.feature.branch.movecommits;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.BranchCmd;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.GitInfoProvider;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.OperationMsg;

/**
 * Moving the newest commits of the checked out branch to another branch: reading which commits that are, and moving
 * them.
 */
public sealed interface MoveCmd extends Cmd {

    Operation MOVE =
            Operation.of("log.title.moveCommits", "error.moveCommits.failed").waitingForUnfinished();

    /**
     * Reading which commits would move, which is reported as this when it fails.
     */
    Operation READ = Operation.of("log.title.moveCommits", "error.moveCommits.readFailed");

    /**
     * Reads the commits of {@code from} from {@code oldest} up to its tip, and how many of them are on
     * {@code upstream} already.
     */
    record ReadRange(Branch.Name from, Sha oldest, Option<RemoteBranch> upstream) implements MoveCmd {

        @Override
        public Msg run(Env env) {
            return env.git()
                    .commitRange(from, oldest, upstream)
                    .fold(this::failedWith, range -> new MoveMsg.RangeRead(from, oldest, range));
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
    record Move(Branch.Name from, Sha oldest, MoveTarget target, Option<RemoteBranch> forcePush)
            implements MoveCmd, BranchCmd.ChecksOutBranch {

        @Override
        public Operation operation() {
            return MOVE;
        }

        @Override
        public Branch.Name branch() {
            return target.branch();
        }

        @Override
        public Either<? extends GitFailure<?>, List<ExecutedCommand>> perform(GitInfoProvider git) {
            return switch (target) {
                case MoveTarget.NewBranch(var branch, var onto) ->
                    git.moveCommits(from, oldest, branch, onto.map(Move::revision));
                case MoveTarget.Existing(var branch) -> git.moveCommitsTo(from, oldest, branch);
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

        private static String revision(BranchRef ref) {
            return switch (ref) {
                case BranchRef.Local(Branch.Name name) -> "refs/heads/" + name.value();
                case BranchRef.Remote(RemoteBranch remote) -> remote.ref();
            };
        }
    }
}
