package net.vanfleteren.jazygit.git.model;

/**
 * The remote branch a branch follows, and how far the two have drifted apart.
 *
 * @param branch the branch on its remote, e.g. {@code main} on {@code origin}
 * @param ahead  the commits of the branch not yet pushed
 * @param behind the commits of the upstream not yet in the branch
 */
public record Upstream(RemoteBranch branch, int ahead, int behind) {
}
