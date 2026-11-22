package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.Forgejo;
import net.vanfleteren.jazygit.core.forgejo.ForgejoRepository;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.forgejo.builds.ForgejoServices;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Tells, once Forgejo turns on, whether fj's token goes to {@code home}, the remote the pull requests are opened
 * against (ADR 177), unencrypted, as one over plain HTTP, in the background (ADR 174).
 */
public record CheckPlainHttp(RemoteName home) implements Cmd {

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        Forgejo forgejo = ForgejoServices.of(env);
        return new PullRequestMsg.PlainHttpChecked(ForgejoRemotes.of(env, forgejo, home)
                .map(ForgejoRemotes::home)
                .filter(forgejo::sendsTokenInTheClear)
                .map(ForgejoRepository::base));
    }

    // Nothing to warn of when it cannot be told.
    @Override
    public Msg failed(Failure failure) {
        return new PullRequestMsg.PlainHttpChecked(Option.none());
    }
}
