package net.vanfleteren.jazygit.app;

import io.vavr.collection.HashMap;
import net.vanfleteren.jazygit.gh.CliGitHub;
import net.vanfleteren.jazygit.gh.process.GhToken;
import net.vanfleteren.jazygit.git.CliGit;
import net.vanfleteren.jazygit.git.Git;
import net.vanfleteren.jazygit.process.SshAsking;
import net.vanfleteren.jazygit.settings.CheckTimingsStore;
import net.vanfleteren.jazygit.settings.CollapsedStore;
import net.vanfleteren.jazygit.settings.RecentRepositories;
import net.vanfleteren.jazygit.settings.Settings;
import net.vanfleteren.jazygit.settings.SettingsStore;
import net.vanfleteren.jazygit.settings.UnseenPullRequestsStore;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Repositories;
import net.vanfleteren.jazygit.state.Terminal;
import net.vanfleteren.jazygit.state.github.GitHubAccount;

/**
 * What commands run against for a repository on disk: git and gh as the account configured for it.
 */
final class RepositoryEnv {

    private RepositoryEnv() {}

    /**
     * What commands run against for the repository {@code git} reads: gh at the executable {@code settings} name,
     * and both gh and git as the account configured for the repository, if any; ssh, run by them for what the user
     * starts, may ask the user as {@code ssh} lets it (ADR 150).
     */
    static Env of(
            Git git,
            Settings settings,
            SettingsStore store,
            RecentRepositories recent,
            UnseenPullRequestsStore unseen,
            CheckTimingsStore checkTimings,
            CollapsedStore collapsed,
            Repositories repositories,
            Terminal terminal,
            SshAsking ssh) {
        GhToken token = new GhToken(git.worktree(), settings.gh(), () -> GitHubAccount.configured(git));
        // Without the token, git still talks to the remotes, as the credential helper's own account.
        return new Env(
                CliGit.withCredentials(git, () -> token.environment().getOrElse(HashMap.empty()), ssh),
                // The browser of an account is read as a link opens, so one set since holds (ADR 88).
                CliGitHub.of(
                        git.worktree(),
                        settings.gh(),
                        token,
                        () -> GitHubAccount.host(git),
                        account -> store.load().settings().browsers().of(account.host(), account.user()),
                        ssh),
                store,
                recent,
                unseen,
                checkTimings,
                collapsed,
                repositories,
                terminal);
    }

    /**
     * As {@code env}, but for the repository {@code git} reads, with the {@code settings} of the session, where ssh
     * may ask the user as {@code ssh} lets it.
     */
    static Env reopen(Env env, Git git, Settings settings, SshAsking ssh) {
        return of(
                git,
                settings,
                env.settings(),
                env.recent(),
                env.unseen(),
                env.checkTimings(),
                env.collapsed(),
                env.repositories(),
                env.terminal(),
                ssh);
    }
}
