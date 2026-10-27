package net.vanfleteren.jazygit.feature.worktree;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.state.AppError;

/**
 * What the worktree features refuse up front.
 */
public sealed interface WorktreeRefusal extends AppError.Refusal {

    /**
     * The main worktree was asked to be removed, which holds the repository.
     *
     * @param worktree the name of its directory
     */
    record MainWorktreeNotRemovable(String worktree) implements WorktreeRefusal {
        @Override
        public String key() {
            return "error.removeWorktree.main";
        }

        @Override
        public List<Object> args() {
            return List.of(worktree);
        }
    }

    /**
     * The worktree shown was asked to be removed.
     *
     * @param worktree the name of its directory
     */
    record ShownWorktreeNotRemovable(String worktree) implements WorktreeRefusal {
        @Override
        public String key() {
            return "error.removeWorktree.shown";
        }

        @Override
        public List<Object> args() {
            return List.of(worktree);
        }
    }

    /**
     * A locked worktree was asked to be removed, which its lock keeps git from doing. Unlike the other reasons a
     * worktree is not removed, this one goes away once it is unlocked.
     *
     * @param worktree the name of its directory
     * @param reason   why it was locked; empty when no reason was given
     */
    record WorktreeLocked(String worktree, String reason) implements WorktreeRefusal {
        @Override
        public String key() {
            return reason.isBlank() ? "error.removeWorktree.locked" : "error.removeWorktree.lockedBecause";
        }

        @Override
        public List<Object> args() {
            return reason.isBlank() ? List.of(worktree) : List.of(worktree, reason);
        }
    }

    /**
     * The worktree shown was asked to be switched to.
     *
     * @param worktree the name of its directory
     */
    record WorktreeShownAlready(String worktree) implements WorktreeRefusal {
        @Override
        public String key() {
            return "error.switchWorktree.shown";
        }

        @Override
        public List<Object> args() {
            return List.of(worktree);
        }
    }

    /**
     * A worktree whose directory is gone was asked to be switched to.
     *
     * @param worktree the name of its directory
     */
    record WorktreeMissing(String worktree) implements WorktreeRefusal {
        @Override
        public String key() {
            return "error.switchWorktree.missing";
        }

        @Override
        public List<Object> args() {
            return List.of(worktree);
        }
    }

    /**
     * Pruning the worktrees was asked for, but none has its directory gone.
     */
    record NothingToPrune() implements WorktreeRefusal {
        @Override
        public String key() {
            return "error.pruneWorktrees.nothing";
        }
    }

    /**
     * The main worktree was asked to be locked or unlocked, which git refuses.
     */
    record MainWorktreeNotLockable() implements WorktreeRefusal {
        @Override
        public String key() {
            return "error.lockWorktree.main";
        }
    }
}
