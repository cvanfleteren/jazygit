package net.vanfleteren.jazygit.state;

import io.vavr.collection.HashSet;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Instant;

/**
 * How fresh what was read in the background is, like the remotes that are fetched or the pull requests: when it
 * was last read, and which of its reads failed the last time, so what is shown may be stale.
 *
 * @param read    when a read last succeeded; none before one did
 * @param failing what failed to be read the last time it was tried, e.g. a remote
 */
public record Freshness(Option<Instant> read, Set<String> failing) {

    /**
     * Nothing read yet, and nothing failed.
     */
    public static final Freshness NEVER = new Freshness(Option.none(), HashSet.empty());

    /**
     * This, with {@code what} read at {@code at}.
     */
    public Freshness succeeded(String what, Instant at) {
        return new Freshness(Option.of(at), failing.remove(what));
    }

    /**
     * This, with {@code what} failed to be read.
     */
    public Freshness failed(String what) {
        return new Freshness(read, failing.add(what));
    }

    /**
     * Whether a read failed the last time it was tried.
     */
    public boolean stale() {
        return !failing.isEmpty();
    }
}
