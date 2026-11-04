package net.vanfleteren.jazygit.feature.branch.stack;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.branch.stack.StackChoice.BaseChange;
import net.vanfleteren.jazygit.gh.GhFailure;
import net.vanfleteren.jazygit.gh.GitHub;
import net.vanfleteren.jazygit.gh.model.PullRequestEdit;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.OperationCmd;

/**
 * Making and unmaking stacks on GitHub (ADR 73).
 */
public sealed interface StackCmd extends OperationCmd {

    Operation MAKE_STACK = Operation.of("log.title.makeStack", "error.makeStack.failed");

    Operation ADD_TO_STACK = Operation.of("log.title.addToStack", "error.addToStack.failed");

    Operation UNSTACK = Operation.of("log.title.unstack", "error.unstack.failed");

    /**
     * Takes stack {@code stack} apart on GitHub: its pull requests stay open, with their bases.
     */
    record Unstack(int stack) implements StackCmd {

        @Override
        public Operation operation() {
            return UNSTACK;
        }

        @Override
        public Option<String> subject() {
            return Option.of("#" + stack);
        }

        @Override
        public boolean changesGitHub() {
            return true;
        }

        @Override
        public Msg run(Env env) {
            return env.gitHub().unstack(stack).fold(this::failedWith, this::succeeded);
        }
    }

    /**
     * Changes the bases of {@code changes}, from the bottom up, then makes a stack of {@code pullRequests}, the bottom
     * one first; or puts them on the top of stack {@code stack}, when one is given.
     */
    record MakeStack(List<BaseChange> changes, List<Integer> pullRequests, Option<Integer> stack) implements StackCmd {

        @Override
        public Operation operation() {
            return stack.isDefined() ? ADD_TO_STACK : MAKE_STACK;
        }

        @Override
        public Option<String> subject() {
            return Option.of(pullRequests.map(number -> "#" + number).mkString(" "));
        }

        @Override
        public boolean changesGitHub() {
            return true;
        }

        @Override
        public Msg run(Env env) {
            GitHub gitHub = env.gitHub();
            Either<GhFailure, List<ExecutedCommand>> rebased = changes.foldLeft(
                    Either.right(List.empty()),
                    (done, change) -> done.flatMap(commands -> partly(
                            commands,
                            gitHub.editPullRequest(
                                    change.number(),
                                    new PullRequestEdit(Option.none(), Option.none(), Option.of(change.to()))))));
            return rebased.flatMap(commands -> partly(
                            commands,
                            stack.fold(
                                    () -> gitHub.createStack(pullRequests),
                                    number -> gitHub.addToStack(number, pullRequests))))
                    .fold(this::failedWith, this::succeeded);
        }

        /**
         * {@code next} after {@code done}: what was done before shows in the command log also when it fails.
         */
        private static Either<GhFailure, List<ExecutedCommand>> partly(
                List<ExecutedCommand> done, Either<GhFailure, List<ExecutedCommand>> next) {
            return next.map(done::appendAll)
                    .mapLeft(failure -> new GhFailure(failure.error(), done.appendAll(failure.commands())));
        }
    }
}
