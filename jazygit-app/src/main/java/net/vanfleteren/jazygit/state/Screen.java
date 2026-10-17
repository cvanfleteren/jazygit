package net.vanfleteren.jazygit.state;

/**
 * What the left of the screen shows: the repository, or the pull requests.
 */
public enum Screen {
    /**
     * The files, branches, commits and stashes of the repository.
     */
    REPOSITORY,
    /**
     * The pull requests on GitHub.
     */
    PULL_REQUESTS;

    /**
     * The other screen, which the tab key switches to.
     */
    public Screen other() {
        return this == REPOSITORY ? PULL_REQUESTS : REPOSITORY;
    }
}
