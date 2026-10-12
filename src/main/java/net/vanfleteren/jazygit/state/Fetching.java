package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * A background fetch from the remotes, one remote after the other.
 *
 * @param remote the remote being fetched from; none while the remotes are still being listed
 * @param queued the remotes still to fetch from after it
 */
public record Fetching(Option<String> remote, List<String> queued) {

    /**
     * A fetch that is still listing the remotes.
     */
    public static final Fetching LISTING = new Fetching(Option.none(), List.empty());
}
