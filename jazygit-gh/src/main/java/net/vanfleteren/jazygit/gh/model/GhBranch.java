package net.vanfleteren.jazygit.gh.model;

/**
 * A branch as GitHub names it, in the repository: the name it has on the remote, which may not be the name of the
 * local branch that pushes to it (ADR 40).
 */
public record GhBranch(String name) {

    /**
     * The branch of a pull request whose branch GitHub did not say: it has no name.
     */
    public static final GhBranch UNKNOWN = new GhBranch("");

    /**
     * Whether GitHub said which branch it is.
     */
    public boolean isKnown() {
        return !name.isEmpty();
    }
}
