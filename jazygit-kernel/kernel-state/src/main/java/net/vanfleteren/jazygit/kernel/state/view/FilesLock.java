package net.vanfleteren.jazygit.kernel.state.view;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.PaneId;

/**
 * The list the files pane is locked to, by Enter in it or {@code F} on a branch: the pane follows the highlight of that
 * list only, whichever pane has the focus, until {@code Esc} (ADR 149).
 *
 * @param pane   the pane the list is in
 * @param list   which of the lists of that pane: the branches pane lists the branches, a divergence or the commits of
 *               a branch
 * @param at     what the list followed when the pane last listed for it: the files listed stay until it follows
 *               something else, also when they are not what it follows, as the files of a pull request after {@code F};
 *               none until the frame after the lock, which tells
 * @param serial which lock this is: each Enter locks anew, which moves the focus into the files
 */
public record FilesLock(PaneId pane, In list, Option<Followed> at, int serial) {

    /**
     * A lock to {@code list} of {@code pane}, after {@code before}, the lock there was, if any.
     */
    public static FilesLock of(PaneId pane, In list, Option<FilesLock> before) {
        return new FilesLock(
                pane, list, Option.none(), before.map(lock -> lock.serial() + 1).getOrElse(1));
    }

    /**
     * This, having listed for {@code followed}.
     */
    public FilesLock at(Followed followed) {
        return new FilesLock(pane, list, Option.of(followed), serial);
    }

    /**
     * Whether the list it is locked to is still shown in {@code view}, {@code branches} when it is the branches pane:
     * a divergence or the commits of a branch that were closed take the lock with them.
     */
    public boolean holds(RepoView view, boolean branches) {
        return list == In.of(view, branches);
    }

    /**
     * Which list a pane shows.
     */
    public enum In {
        /**
         * The list of the pane itself: the commits, the stashes or the branches.
         */
        LIST,
        /**
         * The divergence of a branch with its upstream, in the branches pane (ADR 20).
         */
        DIVERGENCE,
        /**
         * The commits of a branch, in the branches pane (ADR 23).
         */
        BRANCH_COMMITS,
        /**
         * Another tab of the branches pane than the local branches: the remote branches or the worktrees, which a lock
         * to the local branches does not hold in.
         */
        OTHER_TAB;

        /**
         * The list shown in {@code view} by the pane, {@code branches} when it is the branches pane, which shows a
         * divergence or the commits of a branch in place of its branches, or another tab; the others show their own
         * list.
         */
        public static In of(RepoView view, boolean branches) {
            BranchesView shown = view.branches();
            if (!branches) {
                return LIST;
            }
            if (shown.divergence().isDefined() && shown.tab() == BranchesTab.LOCAL) {
                return DIVERGENCE;
            }
            if (shown.branchCommits().isDefined()) {
                return BRANCH_COMMITS;
            }
            return shown.tab() == BranchesTab.LOCAL ? LIST : OTHER_TAB;
        }
    }
}
