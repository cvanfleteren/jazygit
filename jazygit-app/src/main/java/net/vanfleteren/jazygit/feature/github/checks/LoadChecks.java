package net.vanfleteren.jazygit.feature.github.checks;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Label;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.PendingChecks;

/**
 * Reads the checks on the tip of {@code branch} on GitHub one by one, with their timing, which the user is waiting
 * for.
 */
public record LoadChecks(RemoteBranch branch) implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("work.checksOf", branch.branch().value()));
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub()
                .branchCheckRuns(List.of(branch.branch().value()))
                .fold(this::failedWith, runs -> new ChecksMsg.Loaded(branch, PendingChecks.of(runs)));
    }

    @Override
    public Msg failed(Failure failure) {
        return new ChecksMsg.Failed(branch, failure);
    }
}
