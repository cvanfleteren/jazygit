package net.vanfleteren.jazygit.kernel.state;

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
     * Whether anything is carried: the remotes were fetched from, or a part carries something.
     */
    public boolean any() {
        return !equals(NONE);
    }

    /**
     * {@code initial}, the model of a repository just opened, with what this carries.
     */
    public Model into(Model initial) {
        return initial.withParts(parts).mapJobs(jobs -> jobs.withLastFetch(lastFetch));
    }
}
