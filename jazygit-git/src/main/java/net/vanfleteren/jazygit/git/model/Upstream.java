package net.vanfleteren.jazygit.git.model;

/**
 * The branch a branch follows, and how far the two have drifted apart.
 *
 * @param branch the branch it follows: usually a remote-tracking one, e.g. {@code origin/main}; a local one for a
 *               branch created with {@code --track} from it
 * @param ahead  the commits of the branch not yet pushed
 * @param behind the commits of the upstream not yet in the branch
 */
public record Upstream(BranchRef branch, int ahead, int behind) {}
