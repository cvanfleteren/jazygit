package net.vanfleteren.jazygit.features.github.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.features.github.GitHubFailure;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads the checks, one by one and with their timing, on the tips of {@code branches} on GitHub, whose checks are
 * pending (ADR 32). It runs in the background, as it talks to GitHub, and nobody waits for it.
 */
public record LoadBranchCheckRuns(List<BranchName> branches) implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("github.work.checks"));
    }

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub()
                .checks()
                .runs(branches.map(BranchName::value))
                .mapLeft(GitHubFailure::of)
                .fold(this::failed, runs -> new BranchChecksMsg.RunsLoaded(PendingChecks.of(runs)));
    }

    @Override
    public Msg failed(Failure failure) {
        return new BranchChecksMsg.RunsFailed(failure);
    }
}
