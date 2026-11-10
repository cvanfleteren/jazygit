package net.vanfleteren.jazygit.feature.branch.stack;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.git.GitFailure;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.GitOperationCmd;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.OperationCmd;

/**
 * Checking out a stack on GitHub (ADR 99): fetching its branches, to say what happens to each, then making and
 * fast-forwarding them.
 */
public sealed interface StackCheckoutCmd extends OperationCmd {

    Operation FETCH_STACK = Operation.of("log.title.fetchStack", "error.fetchStack.failed");

    Operation CHECK_OUT_STACK = Operation.of("log.title.checkOutStack", "error.checkOutStack.failed")
            .labelledWhenBusy("branches.busy.checkOutStack");

    /**
     * Fetches the branches of the open pull requests of {@code stack} from {@code remote}, then reads the branches,
     * to say what checking it out does to each.
     *
     * @param on the number of the pull request it was asked for on, if it was asked for on one
     */
    record Fetch(PullRequestStack stack, RemoteName remote, Option<Integer> on) implements StackCheckoutCmd {

        @Override
        public Operation operation() {
            return FETCH_STACK;
        }

        @Override
        public Option<String> subject() {
            return Option.of("#" + stack.number());
        }

        @Override
        public Option<Guard> guard() {
            return Option.of(Guard.REMOTE_REFS);
        }

        @Override
        public Msg run(Env env) {
            Git git = env.git();
            return git.remotes()
                    .fetchBranches(remote, StackCheckout.fetched(stack))
                    .flatMap(commands -> git.branches()
                            .all()
                            .<GitFailure>mapLeft(error -> new GitFailure(error, commands))
                            .map(read -> (Msg) new StackMsg.StackFetched(
                                    this, commands, StackCheckout.of(stack, remote, read, on))))
                    .fold(this::failedWith, msg -> msg);
        }
    }

    /**
     * Makes and fast-forwards the branches of {@code checkout}, all of them or none; then {@code landing} says which
     * of them is checked out.
     */
    record CatchUp(StackCheckout checkout, StackCheckout.Landing landing) implements StackCheckoutCmd, GitOperationCmd {

        @Override
        public Operation operation() {
            return CHECK_OUT_STACK;
        }

        @Override
        public Option<String> subject() {
            return Option.of("#" + checkout.stack());
        }

        // The branches that are there already, which move.
        @Override
        public List<BranchRef> busyBranches() {
            return checkout.lines()
                    .filter(line -> line.kind() == StackCheckout.Kind.FAST_FORWARDED)
                    .map(line -> new BranchRef.Local(line.branch()));
        }

        @Override
        public Either<GitFailure, List<ExecutedCommand>> perform(Git git) {
            return checkout.caughtUp().isEmpty()
                    ? GitFailure.nothing()
                    : git.branches().catchUp(checkout.caughtUp());
        }

        @Override
        public Msg succeeded(List<ExecutedCommand> commands) {
            return new StackMsg.CaughtUp(this, commands);
        }
    }
}
