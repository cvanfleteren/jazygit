package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Reading how the CI checks are doing on the branches on GitHub, which the branches without a pull request
 * show. It is read along with the pull requests.
 */
public final class BranchChecksUpdate {

    private BranchChecksUpdate() {}

    public static Next update(Model model, BranchChecksMsg msg) {
        return switch (msg) {
            case BranchChecksMsg.Loaded(GitHubChecks checks) ->
                GitHubReads.done(
                        model.mapGitHub(gitHub -> gitHub.withBranchChecks(Option.of(checks))), new LoadBranchChecks());
            // Like for the pull requests, a failure is no reason to bother the user: the checks read last stay.
            case BranchChecksMsg.LoadFailed(var failure) -> GitHubReads.done(model, new LoadBranchChecks());
        };
    }

    /**
     * {@code next}, also reading the checks again; unless a read is still running, which gets the latest
     * state already.
     */
    static Next load(Next next) {
        return GitHubReads.load(next, new LoadBranchChecks());
    }

    /**
     * {@code next}, also reading the checks again, as something changed on GitHub: when a read is still running,
     * which may have missed that, once it is done.
     */
    static Next loadAnyway(Next next) {
        return GitHubReads.loadAnyway(next, new LoadBranchChecks());
    }
}
