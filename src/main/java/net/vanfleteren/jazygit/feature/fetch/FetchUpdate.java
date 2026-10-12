package net.vanfleteren.jazygit.feature.fetch;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Fetching;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Fetching from the remotes in the background, one after the other, so the remote being fetched from can be
 * shown. Neither the commands nor the failures are: it runs every few seconds, also while offline. A remote
 * that cannot be fetched from does not keep the others from being fetched.
 */
public final class FetchUpdate {

    private FetchUpdate() {
    }

    public static Next update(Model model, FetchMsg msg) {
        return switch (msg) {
            // A fetch that is still running is not started again.
            case FetchMsg.Tick() -> model.fetching().isDefined()
                    ? Next.of(model)
                    : Next.of(model.withFetching(Option.of(Fetching.LISTING)), new FetchCmd.ListRemotes());
            case FetchMsg.RemotesListed(List<String> remotes) -> next(model, remotes);
            case FetchMsg.ListingFailed(var failure) -> Next.of(model.withFetching(Option.none()));
            case FetchMsg.Fetched(String remote) -> next(model, queued(model));
            case FetchMsg.FetchFailed(String remote, var failure) -> next(model, queued(model));
        };
    }

    private static List<String> queued(Model model) {
        return model.fetching().map(Fetching::queued).getOrElse(List.empty());
    }

    /**
     * Fetches from the first of {@code remotes}; with none left, the fetch is done and the branches show what
     * came in.
     */
    private static Next next(Model model, List<String> remotes) {
        return remotes.isEmpty()
                ? Update.refresh(model.withFetching(Option.none()))
                : Next.of(model.withFetching(Option.of(new Fetching(Option.of(remotes.head()), remotes.tail()))),
                new FetchCmd.Fetch(remotes.head()));
    }
}
