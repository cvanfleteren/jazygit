package net.vanfleteren.jazygit.feature.fetch;

import io.vavr.collection.List;
import java.time.Duration;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Update;

public sealed interface FetchMsg extends Msg {

    @Override
    default Update.Next apply(Model model) {
        return FetchUpdate.update(model, this);
    }

    /**
     * Periodic prompt to fetch from the remotes.
     */
    record Tick() implements FetchMsg, Msg.Periodic {

        // A fetch every 5 minutes rather than every minute.
        public static final Duration WHILE_AWAY = Duration.ofMinutes(5);

        @Override
        public Duration whileAway() {
            return WHILE_AWAY;
        }
    }

    /**
     * The remotes to fetch from, in order.
     */
    record RemotesListed(List<RemoteName> remotes) implements FetchMsg {}

    record ListingFailed(Failure failure) implements FetchMsg {}

    /**
     * {@code remote} was fetched from, and {@code rest} are still to fetch from; the model's clock tells when.
     *
     * @param changed whether that changed a remote-tracking branch: one moved, came or went
     */
    record Fetched(RemoteName remote, List<RemoteName> rest, boolean changed) implements FetchMsg {}

    record FetchFailed(RemoteName remote, List<RemoteName> rest, Failure failure) implements FetchMsg {}
}
