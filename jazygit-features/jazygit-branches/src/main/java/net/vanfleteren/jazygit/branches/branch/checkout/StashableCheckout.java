package net.vanfleteren.jazygit.branches.branch.checkout;

import net.vanfleteren.jazygit.git.process.GitError;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.operation.GitOperationCmd;

/**
 * A checkout, of a branch or of a commit, that uncommitted changes in the way can be taken along on through a stash:
 * when git refuses it for them, the user is offered that instead of being told it failed.
 */
public interface StashableCheckout extends GitOperationCmd {

    /**
     * What is checked out, as the popup that offers the stash names it.
     */
    String target();

    /**
     * The same checkout, taking the uncommitted changes along.
     */
    GitOperationCmd withStash();

    @Override
    default Msg run(Env env) {
        return perform(env.git())
                .fold(
                        failure -> failure.error() instanceof GitError.LocalChanges
                                ? new CheckoutMsg.NeedsStash(this, failure.commands())
                                : failed(Failure.of(failure)),
                        this::succeeded);
    }
}
