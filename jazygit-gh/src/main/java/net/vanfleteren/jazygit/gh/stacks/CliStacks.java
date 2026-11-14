package net.vanfleteren.jazygit.gh.stacks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.process.GhFailure;
import net.vanfleteren.jazygit.gh.process.GhProcess;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link Stacks} through gh, of the repository gh finds from the git remotes of its working tree (ADR 114).
 */
public final class CliStacks implements Stacks {

    private final GhProcess process;

    public CliStacks(GhProcess process) {
        this.process = process;
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> create(List<PrNumber> pullRequests) {
        return process.log(StackMutations.create(pullRequests));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> add(int stack, List<PrNumber> pullRequests) {
        return process.log(StackMutations.add(stack, pullRequests));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> unstack(int stack) {
        return process.log(StackMutations.unstack(stack));
    }
}
