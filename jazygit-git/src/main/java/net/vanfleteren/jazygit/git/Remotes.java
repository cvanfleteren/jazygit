package net.vanfleteren.jazygit.git;

import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.git.model.CommitFile;
import net.vanfleteren.jazygit.git.model.DeleteScope;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.PushMode;
import net.vanfleteren.jazygit.git.model.RemoteBranch;
import net.vanfleteren.jazygit.git.model.RemoteName;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * The remotes, and everything that talks to them: pushing, pulling and fetching, deleting a branch on its remote and
 * fetching the commits of a pull request. It is the only role that passes git the credentials (ADR 69).
 */
public interface Remotes {

    // The remotes, as git remote lists them.

    /**
     * The remotes that fetching all fetches from: all of them but those with {@code skipFetchAll} set.
     */
    Either<GitError, List<RemoteName>> list();

    /**
     * The URL of each remote, by its name.
     */
    Either<GitError, Map<String, String>> urls();

    // Pushing branches, as git push does.

    /**
     * Pushes the local branch {@code branch}, which tracks {@code upstream}, where {@code push.default} says: to
     * {@code upstream}, or to the branch of the same name on its remote.
     *
     * @return the command lines that were run, or why git failed; {@link GitError.PushRefused} when
     *     {@code push.default} does not push the branch there, before anything is pushed
     */
    Either<GitFailure, List<ExecutedCommand>> push(BranchName branch, RemoteBranch upstream, PushMode mode);

    /**
     * Pushes the local branch {@code branch} to {@code upstream}, which becomes its upstream once the push
     * succeeded.
     */
    Either<GitFailure, List<ExecutedCommand>> pushSettingUpstream(BranchName branch, RemoteBranch upstream);

    /**
     * Pushes each local branch {@code _1} of {@code branches} to the branch {@code _2} on {@code remote}, all of them
     * or none, each only while it is on the remote as last fetched, and each then tracking it (ADR 74).
     */
    Either<GitFailure, List<ExecutedCommand>> pushAll(RemoteName remote, List<Tuple2<BranchName, BranchName>> branches);

    // Pulling the checked out branch, as git pull does.

    /**
     * Pulls the checked out branch from its upstream, as git's {@code pull.rebase} and {@code pull.ff} say, without
     * asking for a merge message. A pull that stops on conflicts is left so, and fails with
     * {@link GitError.StoppedOnConflicts}.
     */
    Either<GitFailure, List<ExecutedCommand>> pull();

    // Fetching from the remotes, as git fetch does.

    /**
     * Fetches from {@code remote}, and tells whether that changed a remote-tracking branch: one moved, came or went.
     * It never asks for credentials or passphrases: what would need them fails.
     */
    Either<GitFailure, Boolean> fetch(RemoteName remote);

    /**
     * Fast-forwards the local branch {@code branch}, which is not checked out, to {@code upstream}, the branch it
     * tracks. Fails when that is not a fast-forward.
     */
    Either<GitFailure, List<ExecutedCommand>> fastForward(BranchName branch, BranchRef upstream);

    // Deleting branches on their remote.

    /**
     * Deletes the branch {@code name}, locally and/or {@code remote}, the branch it is pushed to. With both, the
     * remote one goes first, so a failure leaves the local branch in place. Locally, git refuses a branch that is
     * not merged, which is a {@link GitError.NotFullyMerged}.
     */
    Either<GitFailure, List<ExecutedCommand>> deleteBranch(BranchName name, RemoteBranch remote, DeleteScope scope);

    /**
     * Deletes {@code remote} on its remote.
     */
    Either<GitFailure, List<ExecutedCommand>> deleteRemoteBranch(RemoteBranch remote);

    // Renaming a branch on its remote.

    /**
     * Renames {@code upstream}, which the local branch {@code branch} follows, to {@code branch}'s name on its remote,
     * as it is there: both or neither, and only while the new name is not taken there and {@code upstream} did not
     * move since it was fetched. {@code branch} then follows the renamed one; its own commits are not pushed.
     *
     * @return the command lines that were run, or why not: a {@link GitError.UpstreamNotRenamed} with why git failed,
     *     a {@link GitError.UpstreamNotOnRemote} when {@code upstream} is gone, and else why {@code branch} could not
     *     be made to follow the renamed one, which then happened
     */
    Either<GitFailure, List<ExecutedCommand>> renameUpstream(BranchName branch, RemoteBranch upstream);

    // What a pull request on GitHub changes, as GitHub shows it (ADR 55).

    /**
     * The files the pull request of {@code commits} changes: its head against the merge base of its head and its
     * base. Its commits are fetched first, from the remote of its repository, when the repository lacks either; what
     * they change is then read through {@link Diffs#pullRequestFileChanges}, which assumes that this ran first.
     */
    Either<GitError, List<CommitFile>> pullRequestFiles(PullRequestCommits commits);
}
