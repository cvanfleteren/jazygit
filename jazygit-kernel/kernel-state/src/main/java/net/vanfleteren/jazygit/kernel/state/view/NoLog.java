package net.vanfleteren.jazygit.kernel.state.view;

import net.vanfleteren.jazygit.core.repo.Sha;

/**
 * A row highlighted in the branches pane that has no log to show, and why, which the content panel says in its place.
 */
public sealed interface NoLog {

    /**
     * What belongs to a group of branches but whose branch is neither checked out nor fetched (ADR 72, ADR 167).
     *
     * @param what what it is called, as {@code #95}
     */
    record Unfetched(String what) implements NoLog {}

    /**
     * HEAD, detached at {@code at}, listed first in the local branches: its log is the one the commits pane shows.
     */
    record Detached(Sha at) implements NoLog {}
}
