package net.vanfleteren.jazygit.feature.fetch;

import io.vavr.collection.List;
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
    record Tick() implements FetchMsg {}

    /**
     * The remotes to fetch from, in order.
     */
    record RemotesListed(List<String> remotes) implements FetchMsg {}

    record ListingFailed(Failure failure) implements FetchMsg {}

    /**
     * {@code remote} was fetched from; the model's clock tells when.
     */
    record Fetched(String remote) implements FetchMsg {}

    record FetchFailed(String remote, Failure failure) implements FetchMsg {}
}
