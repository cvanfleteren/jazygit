package net.vanfleteren.jazygit.kernel.state.view;

import net.vanfleteren.jazygit.core.gh.model.PrNumber;
import net.vanfleteren.jazygit.core.git.model.Sha;

/**
 * A row highlighted in the branches pane that has no log to show, and why, which the content panel says in its place.
 */
public sealed interface NoLog {

    /**
     * A pull request of a stack whose branch is neither checked out nor fetched (ADR 72).
     */
    record Unfetched(PrNumber pullRequest) implements NoLog {}

    /**
     * HEAD, detached at {@code at}, listed first in the local branches: its log is the one the commits pane shows.
     */
    record Detached(Sha at) implements NoLog {}
}
