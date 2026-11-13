package net.vanfleteren.jazygit.gh;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link Stacks} through gh, of the repository gh finds from the git remotes of its working tree (ADR 114).
 */
final class CliStacks implements Stacks {

    private final GhProcess process;

    CliStacks(GhProcess process) {
        this.process = process;
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> create(List<Integer> pullRequests) {
        return process.log(StackMutations.create(pullRequests));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> add(int stack, List<Integer> pullRequests) {
        return process.log(StackMutations.add(stack, pullRequests));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> unstack(int stack) {
        return process.log(StackMutations.unstack(stack));
    }
}
