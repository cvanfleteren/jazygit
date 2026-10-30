package net.vanfleteren.jazygit.feature.fetch;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.PullRequestsUpdate;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Fetching from the remotes in the background, one after the other, so the remote being fetched from can be
 * shown. Neither the commands nor the failures are: it runs every minute, also while offline. A remote
 * that cannot be fetched from does not keep the others from being fetched. When it last fetched, and whether
 * it failed since, is kept, as the line at the bottom says. A fetch runs while one of its commands does (ADR 39): each
 * fetch from a remote carries the remotes still to fetch from after it.
 */
public final class FetchUpdate {

    // What failed when the remotes could not be listed, as the last fetch says it.
    private static final String LISTING = "";

    private FetchUpdate() {}

    public static Next update(Model model, FetchMsg msg) {
        return switch (msg) {
            // A fetch that is still running is not started again.
            case FetchMsg.Tick() when fetching(model) -> Next.of(model);
            case FetchMsg.Tick() -> start(Next.of(model));
            case FetchMsg.RemotesListed(List<RemoteName> remotes) -> next(model, remotes);
            case FetchMsg.ListingFailed(var failure) -> {
                Next stopped = Next.of(model.mapJobs(
                        jobs -> jobs.withLastFetch(jobs.lastFetch().failed(LISTING))));
                yield model.jobs().fetchAgain() ? start(stopped) : stopped;
            }
            case FetchMsg.Fetched(RemoteName remote, List<RemoteName> rest) ->
                next(
                        model.mapJobs(
                                jobs -> jobs.withLastFetch(jobs.lastFetch().succeeded(remote.value(), model.now()))),
                        rest);
            case FetchMsg.FetchFailed(RemoteName remote, List<RemoteName> rest, var failure) ->
                next(model.mapJobs(jobs -> jobs.withLastFetch(jobs.lastFetch().failed(remote.value()))), rest);
        };
    }

    /**
     * {@code next}, also fetching as soon as possible, as something changed on a remote: now, or, when a fetch is
     * running, which may have missed it, once that one is done.
     */
    public static Next requested(Next next) {
        return fetching(next.model())
                ? new Next(next.model().mapJobs(jobs -> jobs.withFetchAgain(true)), next.cmds())
                : start(next);
    }

    /**
     * Whether a fetch runs: the remotes are listed, or one is fetched from.
     */
    private static boolean fetching(Model model) {
        return model.jobs().runs(FetchCmd.class::isInstance);
    }

    private static Next start(Next next) {
        return new Next(
                next.model().mapJobs(jobs -> jobs.withFetchAgain(false)),
                next.cmds().append(new FetchCmd.ListRemotes()));
    }

    /**
     * Fetches from the first of {@code remotes}; with none left, the fetch is done and the branches show what
     * came in, and how their pull requests are doing now.
     */
    private static Next next(Model model, List<RemoteName> remotes) {
        return remotes.pop2Option()
                .map(next -> Next.of(model, new FetchCmd.Fetch(next._1, next._2)))
                .getOrElse(() -> done(model));
    }

    /**
     * The fetch is done; one that was asked for meanwhile starts right away.
     */
    private static Next done(Model model) {
        Next done = PullRequestsUpdate.poll(Update.refresh(model));
        return model.jobs().fetchAgain() ? start(done) : done;
    }
}
