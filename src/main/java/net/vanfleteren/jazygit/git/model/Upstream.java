package net.vanfleteren.jazygit.git.model;

/**
 * The remote-tracking branch a branch follows, and how far the two have drifted apart.
 *
 * @param ref    the remote-tracking ref, e.g. {@code refs/remotes/origin/main}
 * @param tip    the commit that ref points to
 * @param ahead  the commits of the branch not yet pushed
 * @param behind the commits of the upstream not yet in the branch
 */
public record Upstream(String ref, Sha tip, int ahead, int behind) {
}
