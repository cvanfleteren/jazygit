package net.vanfleteren.jazygit.features.branches.branch.push;

import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Asks git, before a force push of the branch of {@code popup} to {@code upstream} is offered, whether the branch ever
 * had the commit its upstream is at, as its reflog says: what {@code --force-if-includes} asks. A branch rewritten
 * here had it; one behind what someone else pushed, and jazygit fetched in the background, never had it (ADR 116).
 */
public record ForcePushCheck(ForcePushPopup popup, RemoteBranch upstream) implements Cmd {

    @Override
    public Msg run(Env env) {
        return env.git()
                .history()
                .everHad(popup.branch(), upstream.tracking())
                .fold(error -> failed(Failure.of(error)), had -> new PushMsg.ForceChecked(popup, had));
    }

    @Override
    public Msg failed(Failure failure) {
        return new PushMsg.ForceCheckFailed(popup.branch(), failure);
    }
}
