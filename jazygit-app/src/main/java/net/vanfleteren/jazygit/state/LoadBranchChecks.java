package net.vanfleteren.jazygit.state;

/**
 * Reads how the CI checks are doing on the branches on GitHub, and which remotes are on GitHub. It runs in the
 * background, as it talks to GitHub, and nobody waits for it.
 */
public record LoadBranchChecks() implements Cmd {

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        return env.gitHub()
                .branchChecks()
                .fold(
                        this::failedWith,
                        checks -> env.git()
                                .remoteUrls()
                                .fold(
                                        this::failedWith,
                                        urls -> new BranchChecksMsg.Loaded(GitHubChecks.of(checks, urls))));
    }

    @Override
    public Msg failed(Failure failure) {
        return new BranchChecksMsg.LoadFailed(failure);
    }
}
