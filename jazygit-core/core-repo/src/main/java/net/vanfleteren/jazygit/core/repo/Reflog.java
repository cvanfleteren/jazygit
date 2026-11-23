package net.vanfleteren.jazygit.core.repo;

import io.vavr.collection.List;

/**
 * A page of the reflog of HEAD, or why there is none to read (ADR 186).
 */
public sealed interface Reflog {

    /**
     * The entries read, newest first. Fewer than were asked for means there are no more.
     */
    record Entries(List<ReflogEntry> entries) implements Reflog {}

    /**
     * Why the reflog of HEAD has no entries at all.
     */
    enum Empty implements Reflog {
        /**
         * HEAD has no commit yet.
         */
        NO_COMMITS,
        /**
         * git does not keep reflogs here: {@code core.logAllRefUpdates} is off, as it is by default in a bare
         * repository.
         */
        OFF
    }
}
