package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Reading the open pull requests from GitHub, which the branches show. It runs next to the other periodic
 * reads, after each fetch and after each change made on GitHub.
 */
public final class PullRequestsUpdate {

    private PullRequestsUpdate() {
    }

    public static Next update(Model model, PullRequestsMsg msg) {
        return switch (msg) {
            case PullRequestsMsg.Refresh() -> load(Next.of(model));
            case PullRequestsMsg.Loaded(List<PullRequest> pullRequests) -> Next.of(read(model)
                    .withPullRequests(Option.of(byHeadBranch(pullRequests))).withPullRequestsFailure(Option.none()));
            // gh can be missing, logged out or offline, or the remote not on GitHub: then no pull requests show.
            // That is no reason to bother the user until they ask for a pull request.
            case PullRequestsMsg.LoadFailed(var failure) -> Next.of(read(model).withPullRequestsFailure(Option.of(failure)));
        };
    }

    /**
     * {@code next}, also reading the pull requests again; unless a read is still running, which gets the
     * latest state already.
     */
    public static Next load(Next next) {
        return next.model().readingPullRequests() > 0 ? next : loadAnyway(next);
    }

    /**
     * {@code next}, also reading the pull requests again, even when a read is still running: one that may have
     * missed a change just made.
     */
    public static Next loadAnyway(Next next) {
        return new Next(reading(next.model()), next.cmds().append(new LoadPullRequests()));
    }

    private static Model reading(Model model) {
        return model.withReadingPullRequests(model.readingPullRequests() + 1);
    }

    private static Model read(Model model) {
        return model.withReadingPullRequests(Math.max(0, model.readingPullRequests() - 1));
    }

    /**
     * The pull requests by the branch they merge from. Several can merge from the same branch, into different
     * bases: the newest, the one with the highest number, is kept, so the choice does not depend on the order
     * gh lists them in.
     */
    static Map<String, PullRequest> byHeadBranch(List<PullRequest> pullRequests) {
        // Vavr's toMap has no merge function; a later entry replaces an earlier one, so ascending numbers keep the newest.
        return pullRequests.sortBy(PullRequest::number).toMap(PullRequest::headBranch, pr -> pr);
    }
}
