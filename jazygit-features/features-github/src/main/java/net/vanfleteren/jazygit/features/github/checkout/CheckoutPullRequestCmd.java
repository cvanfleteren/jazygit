package net.vanfleteren.jazygit.features.github.checkout;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.operation.Operation;
import net.vanfleteren.jazygit.kernel.state.operation.OperationCmd;

/**
 * Checks out {@code pullRequest}, of the repository, in place, as {@code gh pr checkout} does.
 */
public record CheckoutPullRequestCmd(PullRequestSummary pullRequest) implements OperationCmd {

    public static final Operation CHECKOUT_PR = Operation.of(
                    "github.log.title.checkoutPr", "github.error.checkoutPr.failed")
            .waitingForUnfinished();

    @Override
    public Operation operation() {
        return CHECKOUT_PR;
    }

    @Override
    public Option<String> subject() {
        return Option.of(pullRequest.repository() + "#" + pullRequest.number().value());
    }

    @Override
    public Option<Guard> guard() {
        return Option.of(Guard.REMOTE_REFS);
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub()
                .pullRequests()
                .checkout(pullRequest.number())
                .mapLeft(GitHubFailure::of)
                .fold(this::failed, this::succeeded);
    }
}
