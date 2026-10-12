package net.vanfleteren.jazygit.feature.fetch;

import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.state.Update.Next;

/**
 * Fetching from the remotes in the background. Neither its commands nor its failures are shown: it runs
 * every few seconds, also while offline.
 */
public final class FetchUpdate {

    private FetchUpdate() {
    }

    public static Next update(Model model, FetchMsg msg) {
        return switch (msg) {
            // A fetch that is still running is not started again.
            case FetchMsg.Tick() -> model.fetching()
                    ? Next.of(model)
                    : Next.of(model.withFetching(true), new FetchCmd.FetchAll());
            // The branches then show what came in.
            case FetchMsg.Done(var commands) -> Update.refresh(model.withFetching(false));
            case FetchMsg.Failed(String message) -> Next.of(model.withFetching(false));
        };
    }
}
