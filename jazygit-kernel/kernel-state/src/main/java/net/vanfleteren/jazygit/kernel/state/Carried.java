package net.vanfleteren.jazygit.kernel.state;

import io.vavr.control.Option;
import java.nio.file.Path;

/**
 * What another worktree of the same repository opens with, of what the one before knew (ADR 182): the worktrees share
 * their remotes, branches and git configuration, so what was read of those and of their forge holds for it too. What
 * is the worktree's own, its HEAD, status and files, is read afresh.
 *
 * @param parts     what the parts carry of themselves ({@link Parts#carried()})
 * @param lastFetch when a remote was last fetched from, and the remotes that failed the last time: the worktrees fetch
 *                  into the same remote-tracking branches
 */
public record Carried(Parts parts, Freshness lastFetch) {

    /**
     * Nothing carried: a repository opened afresh.
     */
    public static final Carried NONE = new Carried(Parts.NONE, Freshness.NEVER);

    /**
     * What another worktree of the repository {@code last} shows opens with.
     */
    public static Carried from(Model last) {
        return new Carried(last.parts().carried(), last.jobs().lastFetch());
    }

    /**
     * What {@code opened}, a repository opened in place of {@code shown}, whose model is {@code last}, carries of it:
     * what the worktrees share when it is another worktree of the same repository, both known to share one common git
     * directory; nothing for another repository, and nothing for the worktree shown opened anew, as a setting that
     * only applies to a repository shown anew opens it.
     */
    public static Carried between(Shown shown, Shown opened, Model last) {
        return shown.commonDir().isDefined()
                        && shown.commonDir().equals(opened.commonDir())
                        && !shown.worktree().equals(opened.worktree())
                ? from(last)
                : NONE;
    }

    /**
     * Whether a remote was fetched from, so that the worktree opened waits for the tick of the fetch rather than
     * fetching at once.
     */
    public boolean fetched() {
        return lastFetch.read().isDefined();
    }

    /**
     * {@code initial}, the model of a repository just opened, with what this carries.
     */
    public Model into(Model initial) {
        return initial.withParts(parts).mapJobs(jobs -> jobs.withLastFetch(lastFetch));
    }

    /**
     * A repository as it is shown, or opened.
     *
     * @param worktree  the root of its working tree
     * @param commonDir the git directory its worktrees share, if it was found
     */
    public record Shown(Path worktree, Option<Path> commonDir) {}
}
