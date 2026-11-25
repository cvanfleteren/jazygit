package net.vanfleteren.jazygit.kernel.ui.text;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.Midway;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;

/**
 * How an operation the repository is in the middle of is named: in the header, in the lists of the branches pane and
 * in the dialogs, alike (ADR 197).
 */
public final class InProgressText {

    private static final String SEPARATOR = " · ";

    private InProgressText() {}

    /**
     * What kind of operation it is, e.g. "rebase".
     */
    public static String kind(InProgress inProgress) {
        return ErrorText.of(AppError.Unfinished.kind(inProgress));
    }

    /**
     * What a worktree is in the middle of, with how many files there still have conflicts, if any:
     * {@code REBASING feature 1/2 onto main · 1 conflict}. Commits are named by the local branch of {@code branches}
     * they are the tip of, if any.
     */
    public static String label(Midway midway, List<Branch> branches) {
        return withConflicts(operation(midway.inProgress(), true, branches), midway.conflicts());
    }

    /**
     * As {@link #label}, on the row of the branch it works on, which names that branch already:
     * {@code REBASING 1/2 onto main · 1 conflict}.
     */
    public static String ofItsBranch(Midway midway, List<Branch> branches) {
        return withConflicts(operation(midway.inProgress(), false, branches), midway.conflicts());
    }

    private static String withConflicts(String operation, int conflicts) {
        return conflicts > 0 ? operation + SEPARATOR + Messages.get("status.conflicts", conflicts) : operation;
    }

    /**
     * @param named whether a rebase names the branch it rebases
     */
    private static String operation(InProgress inProgress, boolean named, List<Branch> branches) {
        return switch (inProgress) {
            case InProgress.Merging(Sha theirs) -> Messages.get("status.merging", name(theirs, branches));
            case InProgress.Rebasing(Option<BranchName> branch, Sha onto, int step, int total) ->
                branch.filter(name -> named)
                        .map(name -> Messages.get("status.rebasing", name.value(), step, total, name(onto, branches)))
                        .getOrElse(() -> Messages.get("status.rebasingDetached", step, total, name(onto, branches)));
            case InProgress.CherryPicking(Sha commit) -> Messages.get("status.cherryPicking", name(commit, branches));
            case InProgress.Reverting(Sha commit) -> Messages.get("status.reverting", name(commit, branches));
        };
    }

    /**
     * The local branch {@code commit} is the tip of, or else its short sha.
     */
    private static String name(Sha commit, List<Branch> branches) {
        return branches.find(branch -> branch.tip().equals(commit))
                .map(branch -> branch.name().value())
                .getOrElse(commit::shortSha);
    }
}
