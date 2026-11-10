package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.git.model.Sha;

/**
 * What the user is told about how the last operation went, when that is no failure but still needs their
 * attention, as data; the view turns it into text.
 */
public sealed interface Notice {

    /**
     * The operation stopped on conflicts, which the user resolves before going on.
     *
     * @param inProgress what the repository is in the middle of now; none for a squash merge, which the user
     *                   commits once the conflicts are resolved
     * @param conflicts  how many files have conflicts
     */
    record StoppedOnConflicts(Option<InProgress> inProgress, int conflicts) implements Notice {}

    /**
     * Moving the commits of {@code branch} since {@code oldest} to {@code target}, a new or an existing branch,
     * stopped on conflicts while copying them onto it: {@code branch} still has them, until the user moves it back.
     */
    record MoveStopped(StoppedOnConflicts stopped, BranchName branch, Sha oldest, BranchName target)
            implements Notice {}

    /**
     * GitHub added the pull request of {@code branch} to the merge queue of its base branch, which merges it later.
     */
    record MergeEnqueued(BranchName branch) implements Notice {}

    /**
     * GitHub was still merging the pull request of {@code branch} after so long that it is no longer asked about.
     */
    record MergeStillPending(BranchName branch) implements Notice {}

    /**
     * The stack of pull request {@code pullRequest}, linked to be rebased, was not listed by GitHub after so long
     * that it is no longer waited for: it was not rebased (ADR 74).
     */
    record StackNotListed(int pullRequest) implements Notice {}

    /**
     * Stack {@code stack} was ready to be rebased while another dialog was open, which it does not take the place of:
     * it was not rebased (ADR 74).
     */
    record StackNotAsked(int stack) implements Notice {}

    /**
     * The branches of stack {@code stack} were checked out (ADR 99): {@code made} were made, {@code fastForwarded}
     * fast-forwarded, and {@code kept} kept as they were, though not where their branch on the remote is.
     */
    record StackCheckedOut(int stack, int made, int fastForwarded, int kept) implements Notice {}

    /**
     * The terminal was asked to copy {@code lines} lines of a diff to the clipboard (ADR 94).
     */
    record Copied(int lines) implements Notice {}

    /**
     * The terminal was asked to copy the path {@code path} to the clipboard (ADR 94).
     */
    record PathCopied(String path) implements Notice {}

    /**
     * Nothing could be copied, as the app has no terminal of its own to ask (ADR 94).
     */
    record NotCopied() implements Notice {}

    /**
     * Nothing was copied, as the terminal could not be written to (ADR 94).
     */
    record CopyFailed() implements Notice {}
}
