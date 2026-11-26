package net.vanfleteren.jazygit.features.github;

import io.vavr.collection.HashMap;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.gh.CliGitHub;
import net.vanfleteren.jazygit.core.gh.GitHub;
import net.vanfleteren.jazygit.core.gh.process.GhToken;
import net.vanfleteren.jazygit.features.github.checks.timings.CheckTimingsFile;
import net.vanfleteren.jazygit.features.github.prs.unseen.UnseenPullRequestsFile;
import net.vanfleteren.jazygit.features.github.remarks.CollapsedFile;
import net.vanfleteren.jazygit.features.github.settings.GitHubSettings;
import net.vanfleteren.jazygit.features.github.state.GitHubAccount;
import net.vanfleteren.jazygit.features.github.state.GitHubStores;
import net.vanfleteren.jazygit.kernel.settings.FormerState;
import net.vanfleteren.jazygit.kernel.settings.SettingsPaths;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.ui.Feature;
import org.slf4j.LoggerFactory;

/**
 * What GitHub's commands run against, which its feature builds once a repository is opened (ADR 169, 171): gh as the
 * account configured for the repository, where it keeps what it learned of the repository, and the token git talks to
 * the remotes with.
 */
public final class GitHubServices {

    // A gh that is nowhere, so that what runs without GitHub given fails as without gh installed.
    private static final String NO_GH = "jazygit-without-gh";

    /**
     * gh, as the account configured for the repository; without it given, a gh that is not installed.
     */
    public static final Env.Service<GitHub> GH = Env.Service.of(
            "github.gh", CliGitHub.of(Path.of(""), NO_GH, new GhToken(Path.of(""), NO_GH, Option::none)));

    private GitHubServices() {}

    /**
     * gh, as {@code env} runs it.
     */
    public static GitHub of(Env env) {
        return env.service(GH);
    }

    /**
     * What GitHub runs against for the repository {@code opening} opens: gh at the executable its settings name, as
     * the account configured for the repository, and git as that account too, if any; ssh, run by gh for what the
     * user starts, may ask the user as git's does (ADR 150).
     */
    static Feature.Contribution running(Feature.RepositoryOpening opening) {
        // What was kept for all repositories at once is gone (ADR 63).
        Path state = opening.stateFile();
        FormerState.forget(state, UnseenPullRequestsFile.FORMER_KEY, SettingsPaths.formerCheckTimingsFile(state))
                .onFailure(failed -> LoggerFactory.getLogger(GitHubServices.class)
                        .debug("What was kept for all repositories at once could not be deleted", failed));
        GhToken token = new GhToken(
                opening.git().worktree(),
                GitHubSettings.gh(opening.settings()),
                () -> GitHubAccount.configured(opening.git()));
        return new Feature.Contribution(
                Env.Services.NONE
                        // The browser of an account is read as a link opens, so one set since holds (ADR 88).
                        .with(
                                GH,
                                CliGitHub.of(
                                        opening.git().worktree(),
                                        GitHubSettings.gh(opening.settings()),
                                        token,
                                        () -> GitHubAccount.host(opening.git()),
                                        account -> opening.store()
                                                .load()
                                                .settings()
                                                .get(GitHubSettings.BROWSERS)
                                                .of(account.host(), account.user()),
                                        opening.ssh(),
                                        opening.cacheDir()))
                        .with(GitHubStores.UNSEEN, new UnseenPullRequestsFile(opening.repositories()))
                        .with(GitHubStores.CHECK_TIMINGS, new CheckTimingsFile(opening.repositories()))
                        .with(GitHubStores.COLLAPSED, new CollapsedFile(opening.repositories()))
                        .with(Env.FORGE_ACCOUNT, GitHubAccount.ON_FORGE),
                // Without the token, git still talks to the remotes, as the credential helper's own account.
                Option.of(() -> token.environment().getOrElse(HashMap.empty())));
    }
}
