package net.vanfleteren.jazygit.kernel.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.GitHub;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.kernel.settings.CheckTimingsStore;
import net.vanfleteren.jazygit.kernel.settings.CollapsedStore;
import net.vanfleteren.jazygit.kernel.settings.RecentRepositories;
import net.vanfleteren.jazygit.kernel.settings.SettingsStore;
import net.vanfleteren.jazygit.kernel.settings.UnseenPullRequestsStore;
import net.vanfleteren.jazygit.kernel.state.build.ForgeAccount;
import net.vanfleteren.jazygit.kernel.state.github.GitHubAccount;

/**
 * What a {@link Cmd} runs against: the repository through git, its GitHub repository through gh, where the
 * settings, the recent repositories, the unseen pull requests, how long the checks took and the review threads
 * collapsed or opened are kept, where another repository is opened, and the terminal the app is shown on.
 */
public record Env(
        Git git,
        GitHub gitHub,
        SettingsStore settings,
        RecentRepositories recent,
        UnseenPullRequestsStore unseen,
        CheckTimingsStore checkTimings,
        CollapsedStore collapsed,
        Repositories repositories,
        Terminal terminal) {

    /**
     * As given, keeping no review threads collapsed or opened.
     */
    public Env(
            Git git,
            GitHub gitHub,
            SettingsStore settings,
            RecentRepositories recent,
            UnseenPullRequestsStore unseen,
            CheckTimingsStore checkTimings,
            Repositories repositories,
            Terminal terminal) {
        this(git, gitHub, settings, recent, unseen, checkTimings, CollapsedStore.NONE, repositories, terminal);
    }

    /**
     * As given, keeping no check timings.
     */
    public Env(
            Git git,
            GitHub gitHub,
            SettingsStore settings,
            RecentRepositories recent,
            UnseenPullRequestsStore unseen,
            Repositories repositories,
            Terminal terminal) {
        this(git, gitHub, settings, recent, unseen, CheckTimingsStore.NONE, repositories, terminal);
    }

    /**
     * As given, keeping no unseen pull requests.
     */
    public Env(
            Git git,
            GitHub gitHub,
            SettingsStore settings,
            RecentRepositories recent,
            Repositories repositories,
            Terminal terminal) {
        this(git, gitHub, settings, recent, UnseenPullRequestsStore.NONE, repositories, terminal);
    }

    /**
     * As given, on no terminal.
     */
    public Env(Git git, GitHub gitHub, SettingsStore settings, RecentRepositories recent, Repositories repositories) {
        this(git, gitHub, settings, recent, repositories, Terminal.NONE);
    }

    /**
     * Against git and gh, keeping no settings and no recent repositories, and opening no other repository.
     */
    public Env(Git git, GitHub gitHub) {
        this(git, gitHub, SettingsStore.NONE);
    }

    /**
     * Against git and gh, keeping the settings in {@code settings}, keeping no recent repositories, and opening
     * no other repository.
     */
    public Env(Git git, GitHub gitHub, SettingsStore settings) {
        this(git, gitHub, settings, RecentRepositories.NONE, Repositories.NONE);
    }

    /**
     * As this, opening other repositories in {@code repositories} and shown on {@code terminal}.
     */
    public Env withApp(Repositories repositories, Terminal terminal) {
        return new Env(git, gitHub, settings, recent, unseen, checkTimings, collapsed, repositories, terminal);
    }

    /**
     * The account the repository is configured to use on its forge, for a program jazygit starts that may use the
     * forge's own program, as an agent (ADR 162); none when none is configured, and that program runs as its active
     * one, as jazygit does. It names no forge, so a feature that knows none can pass it on.
     */
    public Option<ForgeAccount> forgeAccount() {
        return gitHub.account().configured().map(GitHubAccount::forge);
    }
}
