package net.vanfleteren.jazygit.feature.fetch;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Fetching;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.PullRequestsUpdate;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Fetching from the remotes in the background, one after the other, so the remote being fetched from can be
 * shown. Neither the commands nor the failures are: it runs every minute, also while offline. A remote
 * that cannot be fetched from does not keep the others from being fetched.
 */
public final class FetchUpdate {

    private FetchUpdate() {
    }

    public static Next update(Model model, FetchMsg msg) {
        return switch (msg) {
            // A fetch that is still running is not started again.
            case FetchMsg.Tick() -> model.fetching().isDefined() ? Next.of(model) : start(Next.of(model));
            case FetchMsg.RemotesListed(List<String> remotes) -> next(model, remotes);
            case FetchMsg.ListingFailed(var failure) -> {
                Next stopped = Next.of(model.withFetching(Option.none()));
                yield model.fetching().exists(Fetching::again) ? start(stopped) : stopped;
            }
            case FetchMsg.Fetched(String remote) -> next(model, queued(model));
            case FetchMsg.FetchFailed(String remote, var failure) -> next(model, queued(model));
        };
    }

    /**
     * {@code next}, also fetching as soon as possible, as something changed on a remote: now, or, when a fetch is
     * running, which may have missed it, once that one is done.
     */
    public static Next requested(Next next) {
        return next.model().fetching()
                .map(running -> new Next(next.model().withFetching(Option.of(running.thenAgain())), next.cmds()))
                .getOrElse(() -> start(next));
    }

    private static Next start(Next next) {
        return new Next(next.model().withFetching(Option.of(Fetching.LISTING)),
                next.cmds().append(new FetchCmd.ListRemotes()));
    }

    private static List<String> queued(Model model) {
        return model.fetching().map(Fetching::queued).getOrElse(List.empty());
    }

    /**
     * Fetches from the first of {@code remotes}; with none left, the fetch is done and the branches show what
     * came in, and how their pull requests are doing now.
     */
    private static Next next(Model model, List<String> remotes) {
        boolean again = model.fetching().exists(Fetching::again);
        return remotes.pop2Option()
                .map(next -> Next.of(model.withFetching(Option.of(new Fetching(Option.of(next._1), next._2, again))),
                        new FetchCmd.Fetch(next._1)))
                .getOrElse(() -> done(model));
    }

    /**
     * The fetch is done; one that was asked for meanwhile starts right away.
     */
    private static Next done(Model model) {
        boolean again = model.fetching().exists(Fetching::again);
        Next done = PullRequestsUpdate.load(Update.refresh(model.withFetching(Option.none())));
        return again ? start(done) : done;
    }
}
