package net.vanfleteren.jazygit.git.model;

/**
 * The remote-tracking branch a branch follows, and how far the two have drifted apart.
 *
 * @param name   its short name, e.g. {@code origin/main}
 * @param ahead  the commits of the branch not yet pushed
 * @param behind the commits of the upstream not yet in the branch
 */
public record Upstream(String name, int ahead, int behind) {
}
