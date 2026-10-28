package net.vanfleteren.jazygit.git.model;

import io.vavr.control.Option;
import java.nio.file.Path;
import java.time.Instant;

/**
 * A local branch, as {@code git branch} lists it: one of {@code refs/heads}. The remote-tracking branches are
 * {@link RemoteRef}s.
 *
 * @param current   whether it is the branch checked out here
 * @param tip       the commit the branch points to
 * @param tipTime   when that commit was made
 * @param created   when git made the branch, as its reflog says; none when that is not known, as after a clone
 * @param hasRemote whether the branch it is pushed to exists, according to the remote-tracking refs: its upstream,
 *                  or {@code origin/<name>} without one
 * @param isDefault whether this is the repository's main branch, i.e. the default branch of the remote
 * @param tracking  what it follows: nothing, a branch, or one that is gone
 * @param worktree  the root of the other worktree it is checked out in, if it is: git checks a branch out in
 *                  one worktree at a time, so it can be neither checked out nor deleted here
 */
public record Branch(
        BranchName name,
        boolean current,
        Sha tip,
        Instant tipTime,
        Option<Instant> created,
        boolean hasRemote,
        boolean isDefault,
        Tracking tracking,
        Option<Path> worktree) {

    /**
     * When the branch was last worked on: when its tip was committed, or when it was made if that is later, so a
     * new branch is as old as itself rather than as the commit it was made from (ADR 41).
     */
    public Instant lastActivity() {
        return created.filter(time -> time.isAfter(tipTime)).getOrElse(tipTime);
    }

    /**
     * The branch it follows, if that exists; none when its upstream is gone.
     */
    public Option<Upstream> upstream() {
        return tracking instanceof Tracking.Tracks(Upstream upstream) ? Option.of(upstream) : Option.none();
    }

    /**
     * The branch on a remote it follows, which it can be pushed to; none when it follows a local branch.
     */
    public Option<RemoteBranch> remoteUpstream() {
        return upstream().map(Upstream::branch).flatMap(Branch::onRemote);
    }

    /**
     * The branch its upstream was, when that was deleted.
     */
    public Option<BranchRef> goneUpstream() {
        return tracking instanceof Tracking.Gone(BranchRef upstream) ? Option.of(upstream) : Option.none();
    }

    /**
     * The remote branch it is pushed to: its upstream, even when that is gone, or the branch of the same name
     * on {@code origin} when it has none, or follows a local branch.
     */
    public RemoteBranch pushTarget() {
        return upstream()
                .map(Upstream::branch)
                .orElse(this::goneUpstream)
                .flatMap(Branch::onRemote)
                .getOrElse(() -> new RemoteBranch(RemoteBranch.DEFAULT_REMOTE, name));
    }

    /**
     * Where the other worktree it is checked out in is, as text, if it is checked out in one.
     */
    public Option<String> worktreeLocation() {
        return worktree.map(Path::toString);
    }

    /**
     * Whether it is checked out in another worktree.
     */
    public boolean checkedOutElsewhere() {
        return worktree.isDefined();
    }

    /**
     * The commits not yet pushed to the upstream branch; 0 without an upstream.
     */
    public int ahead() {
        return upstream().map(Upstream::ahead).getOrElse(0);
    }

    /**
     * The commits on the upstream branch not yet in this branch; 0 without an upstream.
     */
    public int behind() {
        return upstream().map(Upstream::behind).getOrElse(0);
    }

    /**
     * Whether the branch can be deleted on its remote: it must be there, and it must not be the
     * default branch, which would break every clone.
     */
    public boolean remoteDeletable() {
        return hasRemote && !isDefault;
    }

    private static Option<RemoteBranch> onRemote(BranchRef branch) {
        return branch instanceof BranchRef.Remote(RemoteBranch remote) ? Option.of(remote) : Option.none();
    }
}
