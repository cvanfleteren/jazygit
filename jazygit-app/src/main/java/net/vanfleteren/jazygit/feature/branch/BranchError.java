package net.vanfleteren.jazygit.feature.branch;

import io.vavr.collection.List;
import java.util.Locale;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.AppError;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Phrase;

/**
 * What went wrong with an operation on a branch: pushing, pulling, renaming, moving commits, deleting, nuking, or
 * rebasing and pushing a stack.
 */
public sealed interface BranchError extends AppError {

    /**
     * The upstream entered for a push is not {@code <remote> <branch>}.
     */
    record InvalidUpstream(String text) implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.upstream.invalid", text.strip());
        }
    }

    /**
     * Renaming the upstream of the branch too was chosen, but the branch no longer follows that upstream, or it is no
     * longer on the remote, as the branches were read again while the dialog was open (ADR 90).
     *
     * @param upstream the short name of the upstream the dialog named, e.g. {@code origin/feature}
     */
    record UpstreamChanged(BranchName branch, String upstream) implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.upstream.changed", branch.value(), upstream);
        }
    }

    /**
     * An operation on the checked out branch was asked for, but HEAD is detached.
     */
    record NoBranchCheckedOut() implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.detached");
        }
    }

    /**
     * A pull of a branch that is not checked out was asked for, but its commits not yet pushed keep it from
     * being fast-forwarded.
     */
    record UnpushedCommits(BranchName branch) implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.fastForward.unpushed", branch.value());
        }
    }

    /**
     * Stack {@code stack} is not rebased, as {@code branch} of it, a branch or a pull request, is as {@code why} says
     * (ADR 74).
     */
    record StackNotRebased(int stack, String branch, StackRefusal why) implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.stackNotRebased", Phrase.verbatim(String.valueOf(stack)), why.phrase(branch));
        }
    }

    /**
     * Stack {@code stack} is not pushed, as {@code branch} of it, a branch or a pull request, is as {@code why} says
     * (ADR 74).
     */
    record StackNotPushed(int stack, String branch, StackRefusal why) implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.stackNotPushed", Phrase.verbatim(String.valueOf(stack)), why.phrase(branch));
        }
    }

    /**
     * Commits are not copied onto {@code target}, a branch, while the working tree has changes, which git would
     * refuse to cherry-pick onto.
     */
    record ChangesBeforeMove(BranchRef target) implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.moveCommits.changes", target.name());
        }
    }

    /**
     * The commits of {@code branch} from {@code oldest} up are not moved, as {@code oldest} came in through
     * {@code merge} and so does its parent, {@code parent}: {@code branch} would be left on a commit of the merged
     * branch instead of one on its own line.
     */
    record MovedThroughMerge(Sha oldest, BranchName branch, Sha merge, Sha parent) implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of(
                    "error.moveCommits.throughMerge",
                    oldest.shortSha(),
                    branch.value(),
                    merge.shortSha(),
                    parent.shortSha());
        }
    }

    /**
     * Deleting a remote branch, or nuking a local one, was asked for, but it is the default branch.
     *
     * @param branch the branch: a remote one to delete, e.g. {@code origin/main}, or a local one to nuke
     */
    record DefaultBranch(BranchRef branch) implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.deleteBranch.default", branch.name());
        }
    }

    /**
     * Nuking {@code branch} did not delete all it was to delete (ADR 43).
     *
     * @param left     what is left, named as the dialog named it: the worktree, the branch, the remote branch
     * @param failures why, one for each step that failed
     */
    record NotAllNuked(BranchName branch, List<String> left, List<Failure> failures) implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of(
                    "error.nukeBranch.notAll",
                    Phrase.verbatim(branch.value()),
                    Phrase.verbatim(left.mkString(", ")),
                    Phrase.joined("; ", failures.map(Phrase::failure)));
        }
    }

    /**
     * A pull request, or a push of a stack, was asked for, but the branch has diverged from {@code upstream}, which
     * only a force push gets its commits onto.
     */
    record Diverged(BranchName branch, RemoteBranch upstream) implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.pullRequest.diverged", branch.value(), upstream.shortName());
        }
    }

    /**
     * The pull request {@code pullRequest}, as {@code owner/name#number}, cannot be checked out, as it is of
     * another repository.
     */
    record OtherRepository(String pullRequest) implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.checkoutPr.otherRepository", pullRequest);
        }
    }

    /**
     * Why a stack is not rebased or pushed (ADR 74).
     */
    enum StackRefusal {
        /** The worktree has changes, which a rebase would bring onto the top instead of where they were. */
        CHANGES,
        /** Its base is not known. */
        NO_BASE,
        /** GitHub did not say the branch of an open pull request of it, which would be moved and pushed. */
        UNKNOWN_BRANCH,
        /** None of its pull requests is open: there is nothing to rebase. */
        NOTHING_OPEN,
        /** Its base is not on the remote its branches are pushed to: it is fetched first. */
        BASE_NOT_FETCHED,
        /** Where a merged branch under it was is not known: its commits would be replayed. */
        MERGED_UNKNOWN,
        /** An open pull request of it has no local branch, which cannot be moved or pushed. */
        NOT_CHECKED_OUT,
        /** A branch of it is pushed to another remote than the others: they are pushed in one push. */
        OTHER_REMOTE,
        /** A branch of it is checked out in another worktree, under whose files it would move. */
        OTHER_WORKTREE,
        /** A branch of it is in a merge queue, which rewriting it takes it out of. */
        MERGE_QUEUE,
        /** A branch of it never had what is on its upstream, which a rebase and push would lose: it is pulled first. */
        BEHIND_REMOTE,
        /**
         * As {@link #BEHIND_REMOTE}, for a branch that does not follow its branch on the remote, which a pull then does
         * not bring in.
         */
        BEHIND_UNFOLLOWED;

        /**
         * Why, said of {@code branch}.
         */
        Phrase phrase(String branch) {
            return Phrase.of("error.stackNotRebased." + name().toLowerCase(Locale.ROOT), Phrase.verbatim(branch));
        }
    }
}
