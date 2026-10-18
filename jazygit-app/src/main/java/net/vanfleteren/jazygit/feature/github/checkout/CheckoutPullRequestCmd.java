package net.vanfleteren.jazygit.feature.github.checkout;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequestSummary;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.OperationCmd;

/**
 * Checks out {@code pullRequest}, of the repository, in place, as {@code gh pr checkout} does.
 */
public record CheckoutPullRequestCmd(PullRequestSummary pullRequest) implements OperationCmd {

    public static final Operation CHECKOUT_PR =
            Operation.of("log.title.checkoutPr", "error.checkoutPr.failed").waitingForUnfinished();

    @Override
    public Operation operation() {
        return CHECKOUT_PR;
    }

    @Override
    public Option<String> subject() {
        return Option.of(pullRequest.repository() + "#" + pullRequest.number());
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub().checkoutPullRequest(pullRequest.number()).fold(this::failedWith, this::succeeded);
    }
}
