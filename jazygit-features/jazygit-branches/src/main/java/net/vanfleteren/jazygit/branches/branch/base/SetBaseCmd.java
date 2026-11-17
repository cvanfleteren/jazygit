package net.vanfleteren.jazygit.branches.branch.base;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.branches.branch.BranchCmd;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.process.GitFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.operation.Operation;

/**
 * Records {@code base} as the branch {@code branch} is built on, in {@code branch.<name>.gh-merge-base}, or removes
 * what is recorded for none (ADR 143). The branches are read again after, which shows the new base.
 */
public record SetBaseCmd(BranchName branch, Option<BranchName> base) implements BranchCmd {

    public static final Operation SET_BASE =
            Operation.of("branches.log.title.setBase", "branches.error.setBase.failed");

    @Override
    public Operation operation() {
        return SET_BASE;
    }

    @Override
    public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
        return git.branches().setParent(branch, base);
    }
}
