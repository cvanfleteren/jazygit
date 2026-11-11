package net.vanfleteren.jazygit.state;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.util.Comparator;
import lombok.With;
import net.vanfleteren.jazygit.git.model.*;

/**
 * What git reports about the repository: each part is loading until it was read once, and the one read last when
 * reading it again failed.
 *
 * @param status          the working tree status and HEAD
 * @param branches        the local branches
 * @param remoteBranches  the remote-tracking branches
 * @param commits         the commit log of HEAD
 * @param ahead           the commits of HEAD that are not on its base, none when it has no base
 * @param stashes         the stashes, newest first
 * @param worktrees       the worktrees of the repository, the one shown first
 * @param worktreeChanges whether the other worktrees have changes, by their location, as read last; one that was
 *                        not read, or could not be read the last time, is not known
 * @param baseDistance    how far each local branch that has a base is from it, by its name, as read last with the
 *                        branches (ADR 46, ADR 58); none before the first read
 * @param parents         the parent of each branch that has one, as read last with the branches (ADR 58, ADR 72):
 *                        what GitHub says it is built on, else the branch it was made from; none before the first
 *                        read
 * @param allFiles        the paths of all files of the working tree, read only while the files pane lists them all
 *                        (ADR 108): loading until then
 */
@With
public record Repo(
        Loadable<RepoStatus> status,
        Loadable<List<Branch>> branches,
        Loadable<List<RemoteRef>> remoteBranches,
        Loadable<List<Commit>> commits,
        Loadable<Option<Ahead>> ahead,
        Loadable<List<Stash>> stashes,
        Loadable<List<Worktree>> worktrees,
        Map<String, Boolean> worktreeChanges,
        Map<BranchName, BaseDistance> baseDistance,
        Map<BranchName, BranchName> parents,
        Loadable<List<String>> allFiles) {

    public static final Repo LOADING = new Repo(
            Loadable.loading(),
            Loadable.loading(),
            Loadable.loading(),
            Loadable.loading(),
            Loadable.loading(),
            Loadable.loading(),
            Loadable.loading(),
            HashMap.empty(),
            HashMap.empty(),
            HashMap.empty(),
            Loadable.loading());

    /**
     * The files the files pane lists for the working tree: its changed files, and, when {@code all} of them are
     * listed, each of its other files as one without changes (ADR 108). Those are loading until all files are read
     * once, so that the tree starts from all of them.
     */
    public Loadable<List<FileEntry>> workingFiles(boolean all) {
        if (!all) {
            return status.map(RepoStatus::files);
        }
        return switch (allFiles) {
            case Loadable.Loaded<List<String>>(List<String> paths) ->
                status.map(read -> {
                    Set<String> changed = read.files().map(FileEntry::path).toSet();
                    return read.files()
                            .appendAll(paths.filter(path -> !changed.contains(path))
                                    .map(FileEntry::unchanged));
                });
            case Loadable.Loading<List<String>>() -> Loadable.loading();
            case Loadable.Failed<List<String>>(Failure failure) -> new Loadable.Failed<>(failure);
        };
    }

    /**
     * How many commits of its parent {@code branch} does not have, as on its remote when it is there: what GitHub
     * calls diverged in a stack, which it does not merge until it is rebased (ADR 73). None when it has them all, has
     * no parent, or that was not read.
     */
    public Option<Integer> behindParent(BranchName branch) {
        return parents.get(branch)
                .flatMap(parent -> baseDistance.get(branch).filter(distance -> switch (distance.base()) {
                    case BranchRef.Local(BranchName local) -> local.equals(parent);
                    case BranchRef.Remote(RemoteBranch remote) ->
                        remote.branch().equals(parent);
                }))
                .map(BaseDistance::behind)
                .filter(behind -> behind > 0);
    }

    /**
     * The commit {@code sha} in the log of HEAD, if the log is loaded and has it.
     */
    public Option<Commit> commit(Sha sha) {
        return commits.toOption()
                .flatMap(loaded -> loaded.find(commit -> commit.sha().equals(sha)));
    }

    /**
     * The local branch {@code name}, if the branches are loaded and it is one of them.
     */
    public Option<Branch> branch(BranchName name) {
        return branches.toOption()
                .flatMap(loaded -> loaded.find(branch -> branch.name().equals(name)));
    }

    /**
     * The remote-tracking branch of {@code branch}, if the remote branches are loaded and it is one of them.
     */
    public Option<RemoteRef> remoteBranch(RemoteBranch branch) {
        return remoteBranches
                .toOption()
                .flatMap(loaded -> loaded.find(ref -> ref.branch().equals(branch)));
    }

    /**
     * The local branch that follows {@code upstream}, if the branches are loaded and one does. Of several, the first
     * of: the one named as {@code upstream} that no worktree has checked out, any other that none has, one that
     * another worktree has checked out, the one checked out here; the first by name where that leaves more than one
     * (ADR 109).
     */
    public Option<Branch> trackingBranch(RemoteBranch upstream) {
        return branches.toOption()
                .flatMap(loaded -> loaded.filter(
                                branch -> branch.remoteUpstream().contains(upstream))
                        .sorted(Comparator.comparingInt((Branch branch) -> followerRank(branch, upstream))
                                .thenComparing(branch -> branch.name().value()))
                        .headOption());
    }

    private static int followerRank(Branch branch, RemoteBranch upstream) {
        if (branch.current()) {
            return 3;
        }
        if (branch.checkedOutElsewhere()) {
            return 2;
        }
        return branch.name().equals(upstream.branch()) ? 0 : 1;
    }

    /**
     * The checked out branch, if the branches are loaded and one is checked out.
     */
    public Option<Branch> currentBranch() {
        return branches.toOption().flatMap(loaded -> loaded.find(Branch::current));
    }

    /**
     * The worktree at {@code location}, if the worktrees are loaded and it is one of them.
     */
    public Option<Worktree> worktree(String location) {
        return worktrees
                .toOption()
                .flatMap(loaded -> loaded.find(worktree -> worktree.location().equals(location)));
    }

    /**
     * The main worktree, if the worktrees are loaded and the repository has one.
     */
    public Option<Worktree> mainWorktree() {
        return worktrees.toOption().flatMap(loaded -> loaded.find(Worktree::main));
    }

    /**
     * The worktree shown, if the worktrees are loaded.
     */
    public Option<Worktree> currentWorktree() {
        return worktrees.toOption().flatMap(loaded -> loaded.find(Worktree::current));
    }

    /**
     * Whether {@code worktree} has changes, if that is known: for the one shown, from its status.
     */
    public Option<Boolean> hasChanges(Worktree worktree) {
        return worktree.current()
                ? status.toOption().map(loaded -> loaded.files().nonEmpty())
                : worktreeChanges.get(worktree.location());
    }

    /**
     * The worktrees whose changes are read, once the worktrees are loaded: the others that are still on disk.
     */
    public List<String> otherWorktrees() {
        return worktrees
                .getOrElse(List.empty())
                .filter(worktree -> !worktree.current() && !worktree.prunable())
                .map(Worktree::location);
    }
}
