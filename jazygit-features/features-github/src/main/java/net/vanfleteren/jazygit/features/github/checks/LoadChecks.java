package net.vanfleteren.jazygit.features.github.checks;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.RemoteBranch;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.github.PendingChecks;

/**
 * Reads the checks on the tip of {@code branch} on GitHub one by one, with their timing, which the user is waiting
 * for.
 */
public record LoadChecks(RemoteBranch branch) implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("github.work.checksOf", branch.branch().value()));
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub()
                .checks()
                .runs(List.of(branch.branch().value()))
                .mapLeft(Failure::of)
                .fold(this::failed, runs -> new ChecksMsg.Loaded(branch, PendingChecks.of(runs)));
    }

    @Override
    public Msg failed(Failure failure) {
        return new ChecksMsg.Failed(branch, failure);
    }
}
