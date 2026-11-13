package net.vanfleteren.jazygit.gh;

/**
 * A {@link GitHub} for tests: a repository without pull requests or branches, whose operations all succeed without
 * running anything, each role a stub of its own. A test replaces the roles it needs:
 * {@code StubGitHub.of().withPullRequests(new StubPullRequests() { … })} (ADR 114).
 */
public final class StubGitHub {

    private StubGitHub() {}

    /**
     * A GitHub repository without pull requests.
     */
    public static GitHub of() {
        return new GitHub(
                new StubPullRequests() {},
                new StubReviews() {},
                new StubChecks() {},
                new StubStacks() {},
                new StubAccount() {});
    }
}
