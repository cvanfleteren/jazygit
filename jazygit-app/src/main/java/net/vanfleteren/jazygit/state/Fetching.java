package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * A background fetch from the remotes, one remote after the other.
 *
 * @param remote the remote being fetched from; none while the remotes are still being listed
 * @param queued the remotes still to fetch from after it
 * @param again  whether to fetch again once this one is done, as something changed on a remote meanwhile that
 *               this one may have missed
 */
public record Fetching(Option<String> remote, List<String> queued, boolean again) {

    /**
     * A fetch that is still listing the remotes.
     */
    public static final Fetching LISTING = new Fetching(Option.none(), List.empty());

    /**
     * A fetch that is not to be done again.
     */
    public Fetching(Option<String> remote, List<String> queued) {
        this(remote, queued, false);
    }

    /**
     * This fetch, then another one.
     */
    public Fetching thenAgain() {
        return new Fetching(remote, queued, true);
    }
}
