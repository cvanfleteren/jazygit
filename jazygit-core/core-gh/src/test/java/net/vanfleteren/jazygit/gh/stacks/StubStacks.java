package net.vanfleteren.jazygit.gh.stacks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.gh.model.PrNumber;
import net.vanfleteren.jazygit.gh.process.GhFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link Stacks} for tests: changing the stacks succeeds without running anything. Fakes override what they need.
 */
public interface StubStacks extends Stacks {

    @Override
    default Either<GhFailure, List<ExecutedCommand>> create(List<PrNumber> pullRequests) {
        return Either.right(List.of(StackMutations.create(pullRequests).executed()));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> add(int stack, List<PrNumber> pullRequests) {
        return Either.right(List.of(StackMutations.add(stack, pullRequests).executed()));
    }

    @Override
    default Either<GhFailure, List<ExecutedCommand>> unstack(int stack) {
        return Either.right(List.of(StackMutations.unstack(stack).executed()));
    }
}
