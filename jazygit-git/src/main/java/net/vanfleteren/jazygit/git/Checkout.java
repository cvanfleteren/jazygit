package net.vanfleteren.jazygit.git;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.GitError.ProcessError;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.Revision;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * Checking out branches, as git checkout does, and finding the commit a revision the user typed names (ADR 69).
 */
public interface Checkout {

    /**
     * Checks out the local branch {@code branch}. Fails with {@link GitError.LocalChanges} when
     * uncommitted changes would be overwritten.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> branch(BranchName branch);

    /**
     * Checks out the local branch {@code branch}, taking the uncommitted changes along: they are stashed
     * first and popped again afterwards. When the checkout itself fails, the changes are popped back
     * where they were; when popping them on {@code branch} fails, they stay in the stash, which is a
     * {@link GitError.StashNotRestored}.
     */
    Either<GitFailure<GitError>, List<ExecutedCommand>> branchWithStash(BranchName branch);

    /**
     * Creates the local branch {@code name} at {@code startPoint} and checks it out; records {@code parent} as the
     * branch it came from, if given, as {@link GitInfoProvider#parentBranch} reads it. Whether it follows
     * {@code startPoint} is up to git's {@code branch.autoSetupMerge} (ADR 40).
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> newBranch(
            BranchName name, Revision startPoint, Option<BranchName> parent);

    /**
     * Creates the local branch {@code name} at the remote-tracking branch of {@code upstream}, following it, and
     * checks it out.
     */
    Either<GitFailure<ProcessError>, List<ExecutedCommand>> newTrackingBranch(BranchName name, RemoteBranch upstream);

    /**
     * The commit {@code text}, as the user typed it, names: a branch, a tag, a sha or any other revision; none when
     * it names no commit. Git reads {@code text} as a revision even when it starts with {@code -}.
     */
    Either<GitFailure<ProcessError>, Option<Sha>> resolve(String text);
}
