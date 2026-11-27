package net.vanfleteren.jazygit.core.git.model;

/**
 * How a pull reconciles the branch with what its upstream has.
 */
public enum PullMode {
    /**
     * As git's {@code pull.rebase} and {@code pull.ff} say ({@code git pull}); a pull of a diverged branch is refused
     * when neither is set.
     */
    CONFIGURED,
    /**
     * Replays the commits of the branch on top of its upstream ({@code git pull --rebase}), whatever the settings say.
     */
    REBASE,
    /**
     * Merges the upstream into the branch ({@code git pull --no-rebase}), whatever the settings say.
     */
    MERGE
}
