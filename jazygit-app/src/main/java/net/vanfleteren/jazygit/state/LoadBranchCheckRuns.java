package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.BranchName;

/**
 * Reads the checks, one by one and with their timing, on the tips of {@code branches} on GitHub, whose checks are
 * pending (ADR 30). It runs in the background, as it talks to GitHub, and nobody waits for it.
 */
public record LoadBranchCheckRuns(List<BranchName> branches) implements Cmd {

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub()
                .branchCheckRuns(branches.map(BranchName::value))
                .fold(this::failedWith, runs -> new BranchChecksMsg.RunsLoaded(PendingChecks.of(runs)));
    }

    @Override
    public Msg failed(Failure failure) {
        return new BranchChecksMsg.RunsFailed(failure);
    }
}
