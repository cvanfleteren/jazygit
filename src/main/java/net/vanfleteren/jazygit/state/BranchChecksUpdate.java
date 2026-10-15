package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Reading how the CI checks are doing on the branches on GitHub, which the branches without a pull request
 * show. It is read along with the pull requests.
 */
public final class BranchChecksUpdate {

    private BranchChecksUpdate() {
    }

    public static Next update(Model model, BranchChecksMsg msg) {
        return switch (msg) {
            case BranchChecksMsg.Loaded(GitHubChecks checks) ->
                    Next.of(read(model).withBranchChecks(Option.of(checks)));
            // Like for the pull requests, a failure is no reason to bother the user: the checks read last stay.
            case BranchChecksMsg.LoadFailed(var failure) -> Next.of(read(model));
        };
    }

    /**
     * {@code next}, also reading the checks again; unless a read is still running, which gets the latest
     * state already.
     */
    static Next load(Next next) {
        return next.model().readingBranchChecks() > 0 ? next : loadAnyway(next);
    }

    /**
     * {@code next}, also reading the checks again, even when a read is still running: one that may have missed
     * a change just made.
     */
    static Next loadAnyway(Next next) {
        return new Next(next.model().withReadingBranchChecks(next.model().readingBranchChecks() + 1),
                next.cmds().append(new LoadBranchChecks()));
    }

    private static Model read(Model model) {
        return model.withReadingBranchChecks(Math.max(0, model.readingBranchChecks() - 1));
    }
}
