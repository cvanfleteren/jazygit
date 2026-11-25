package net.vanfleteren.jazygit.features.midway.resolve;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.model.RepoStatus;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.Head;
import net.vanfleteren.jazygit.core.repo.InProgress;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Model;

/**
 * What the two sides of the conflicts of an operation in progress are (ADR 198).
 *
 * @param ours   the side of HEAD: the branch checked out, or what a rebase rebases onto
 * @param theirs the other: the branch or commit merged, the commit cherry-picked or reverted, or the commit being
 *               rebased
 */
public record Sides(SideName ours, SideName theirs) {

    /**
     * The sides of what the repository of {@code model} is in the middle of. A conflict left with no operation in
     * progress, as by a stash that was popped, has HEAD on one side and what was brought onto it on the other. None
     * while the status is not read.
     */
    public static Option<Sides> of(Model model) {
        List<Branch> branches = model.repo().branches().toOption().getOrElse(List.empty());
        return model.repo().status().toOption().map(status -> of(status, branches));
    }

    private static Sides of(RepoStatus status, List<Branch> branches) {
        SideName head = switch (status.head()) {
            case Head.OnBranch onBranch -> new SideName.Branch(onBranch.branch());
            case Head.Detached detached -> named(detached.at(), branches);
        };
        return status.inProgress()
                .map(inProgress -> of(head, inProgress, branches))
                .getOrElse(() -> new Sides(head, new SideName.Incoming()));
    }

    private static Sides of(SideName head, InProgress inProgress, List<Branch> branches) {
        return switch (inProgress) {
            case InProgress.Rebasing rebasing -> new Sides(named(rebasing.onto(), branches), new SideName.Rebased());
            case InProgress.Merging merging -> new Sides(head, named(merging.theirs(), branches));
            case InProgress.CherryPicking picking -> new Sides(head, named(picking.commit(), branches));
            case InProgress.Reverting reverting -> new Sides(head, named(reverting.commit(), branches));
        };
    }

    /**
     * The local branch {@code commit} is the tip of, if any, else the commit.
     */
    private static SideName named(Sha commit, List<Branch> branches) {
        return branches.find(branch -> branch.tip().equals(commit))
                .<SideName>map(branch -> new SideName.Branch(branch.name()))
                .getOrElse(() -> new SideName.Commit(commit));
    }
}
