package net.vanfleteren.jazygit.features.github.state;

import net.vanfleteren.jazygit.kernel.settings.CheckTimingsStore;
import net.vanfleteren.jazygit.kernel.settings.UnseenPullRequestsStore;
import net.vanfleteren.jazygit.kernel.state.Env;

/**
 * Where GitHub's feature keeps what it learned of a repository between sessions, as what its commands run against
 * (ADR 169): the pull requests not seen yet, and how long the checks took. Without them given, nothing is kept.
 */
public final class GitHubStores {

    /**
     * Where the pull requests not seen yet are kept (ADR 29).
     */
    public static final Env.Service<UnseenPullRequestsStore> UNSEEN =
            Env.Service.of("github.unseen", UnseenPullRequestsStore.NONE);

    /**
     * Where how long the checks took is kept (ADR 32).
     */
    public static final Env.Service<CheckTimingsStore> CHECK_TIMINGS =
            Env.Service.of("github.checkTimings", CheckTimingsStore.NONE);

    private GitHubStores() {}
}
