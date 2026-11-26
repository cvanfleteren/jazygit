package net.vanfleteren.jazygit.features.branches.branch;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.AppError;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Phrase;

/**
 * What went wrong with an operation on a branch: pushing, pulling, renaming, moving commits, deleting or nuking.
 */
public sealed interface BranchError extends AppError {

    /**
     * The upstream entered for a push is not {@code <remote> <branch>}.
     */
    record InvalidUpstream(String text) implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of("branches.error.upstream.invalid", text.strip());
        }
    }

    /**
     * Checking out {@code branch} was asked for, but the worktree shown is rebasing it, with its HEAD detached
     * meanwhile: it is the branch worked on here already, and the rebase is to be continued or aborted (ADR 197).
     */
    record RebasingHere(BranchName branch) implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of("branches.error.checkout.rebasingHere", branch.value());
        }
    }

    /**
     * A push was asked for, but the branch is behind its upstream, which has commits the branch never had, as
     * someone else pushed them: a force push would drop them, so it is to be pulled first (ADR 116).
     *
     * @param diverged whether the branch also has commits of its own, which a pull then rebases or merges
     */
    record BehindUpstream(BranchName branch, boolean diverged) implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of(diverged ? "branches.error.push.diverged" : "branches.error.push.behind", branch.value());
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
            return Phrase.of("branches.error.upstream.changed", branch.value(), upstream);
        }
    }

    /**
     * An operation on the checked out branch was asked for, but HEAD is detached.
     */
    record NoBranchCheckedOut() implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of("branches.error.detached");
        }
    }

    /**
     * Setting the base of {@code branch} was asked for, but it is the main branch or a protected one, which is built
     * on no other (ADR 143).
     */
    record RootHasNoBase(BranchName branch) implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of("branches.error.setBase.root", branch.value());
        }
    }

    /**
     * Setting the base of {@code branch} was asked for, but {@code by} states it is built on {@code base}, as its
     * open pull request does on GitHub, which comes before what is recorded (ADR 143, 169): it is changed there.
     */
    record BaseStated(BranchName branch, BranchName base, Phrase by) implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of(
                    "branches.error.setBase.stated",
                    Phrase.verbatim(branch.value()),
                    by,
                    Phrase.verbatim(base.value()));
        }
    }

    /**
     * A pull of a branch that is not checked out was asked for, but its commits not yet pushed keep it from
     * being fast-forwarded.
     */
    record UnpushedCommits(BranchName branch) implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of("branches.error.fastForward.unpushed", branch.value());
        }
    }

    /**
     * Commits are not copied onto {@code target}, a branch, while the working tree has changes, which git would
     * refuse to cherry-pick onto.
     */
    record ChangesBeforeMove(BranchRef target) implements BranchError {

        @Override
        public Phrase phrase() {
            return Phrase.of("branches.error.moveCommits.changes", target.name());
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
                    "branches.error.moveCommits.throughMerge",
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
            return Phrase.of("branches.error.deleteBranch.default", branch.name());
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
                    "branches.error.nukeBranch.notAll",
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
            return Phrase.of("branches.error.pullRequest.diverged", branch.value(), upstream.shortName());
        }
    }
}
