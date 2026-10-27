package net.vanfleteren.jazygit;

import io.vavr.collection.HashMap;
import net.vanfleteren.jazygit.gh.CliGitHub;
import net.vanfleteren.jazygit.gh.GhToken;
import net.vanfleteren.jazygit.git.CliGitInfoProvider;
import net.vanfleteren.jazygit.settings.CheckTimingsStore;
import net.vanfleteren.jazygit.settings.RecentRepositories;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.settings.UnseenPullRequestsStore;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.GitHubAccount;
import net.vanfleteren.jazygit.state.Repositories;
import net.vanfleteren.jazygit.state.Terminal;

/**
 * What commands run against for a repository on disk: git and gh as the account configured for it.
 */
final class RepositoryEnv {

    private RepositoryEnv() {}

    /**
     * What commands run against for the repository {@code git} reads: gh at the executable {@code settings} name,
     * and both gh and git as the account configured for the repository, if any.
     */
    static Env of(
            CliGitInfoProvider git,
            Settings settings,
            SettingsStore store,
            RecentRepositories recent,
            UnseenPullRequestsStore unseen,
            CheckTimingsStore checkTimings,
            Repositories repositories,
            Terminal terminal) {
        GhToken token = new GhToken(git.worktree(), settings.gh(), () -> GitHubAccount.configured(git));
        // Without the token, git still talks to the remotes, as the credential helper's own account.
        return new Env(
                git.withCredentials(() -> token.environment().getOrElse(HashMap.empty())),
                new CliGitHub(git.worktree(), settings.gh(), token),
                store,
                recent,
                unseen,
                checkTimings,
                repositories,
                terminal);
    }

    /**
     * As {@code env}, but for the repository {@code git} reads, with the {@code settings} of the session.
     */
    static Env reopen(Env env, CliGitInfoProvider git, Settings settings) {
        return of(
                git,
                settings,
                env.settings(),
                env.recent(),
                env.unseen(),
                env.checkTimings(),
                env.repositories(),
                env.terminal());
    }
}
