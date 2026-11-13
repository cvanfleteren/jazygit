package net.vanfleteren.jazygit.gh;

import io.vavr.collection.List;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Stacks of pull requests on GitHub (ADR 73, ADR 114).
 */
public interface Stacks {

    /**
     * Makes a stack on GitHub of {@code pullRequests}, the bottom one first: two or more, each with the head of the one
     * below it as its base.
     */
    Either<GhFailure, List<ExecutedCommand>> create(List<Integer> pullRequests);

    /**
     * Puts {@code pullRequests}, the lowest one first, on the top of stack {@code stack}.
     */
    Either<GhFailure, List<ExecutedCommand>> add(int stack, List<Integer> pullRequests);

    /**
     * Takes stack {@code stack} apart on GitHub; its pull requests stay open, with their bases.
     */
    Either<GhFailure, List<ExecutedCommand>> unstack(int stack);
}
