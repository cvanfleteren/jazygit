package net.vanfleteren.jazygit.kernel.state.github;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.GhAccount;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.kernel.state.build.ForgeAccount;

/**
 * The gh account a repository is configured to use, kept in its own git configuration, so it stays with the
 * repository, and other repositories keep using gh's active account.
 */
public final class GitHubAccount {

    /**
     * The key of the account's login in the repository's git configuration.
     */
    public static final String USER_KEY = "jazygit.githubUser";

    private GitHubAccount() {}

    /**
     * The account configured for the repository of {@code git}, on the GitHub host of its remotes; none when
     * there is none configured, or the configuration could not be read.
     */
    public static Option<GhAccount> configured(Git git) {
        return git.config()
                .local(USER_KEY)
                .getOrElse(Option.none())
                .filter(user -> !user.isBlank())
                .map(user -> new GhAccount(host(git), user));
    }

    /**
     * {@code account} as a forge account, for a program jazygit starts, as an agent (ADR 162): how to run gh as it,
     * each command with its token in the variables gh reads, as jazygit runs gh itself (see {@code GhToken}), so that
     * no token is handed over and gh's active account stays as it is.
     */
    public static ForgeAccount forge(GhAccount account) {
        String token = "\"$(gh auth token --user " + account.user() + " --hostname " + account.host() + ")\"";
        String variables = GhAccount.isGitHubCom(account.host())
                ? "GH_TOKEN=" + token
                : "GH_ENTERPRISE_TOKEN=" + token + " GH_HOST=" + account.host();
        return new ForgeAccount(
                account.user(),
                account.host(),
                // An empty token is as none: gh then runs as its active account, which the agent would not notice.
                "put " + variables + " before each gh command, as in " + variables + " gh run view 401; if gh auth"
                        + " token gives nothing, stop and say so rather than running gh without it, which would run"
                        + " as the active account; never run gh auth switch, which changes the account of every"
                        + " program");
    }

    /**
     * The GitHub host of the remotes of {@code git}; github.com when they do not say.
     */
    public static String host(Git git) {
        return git.remotes()
                .urls()
                .toOption()
                .flatMap(urls -> GhAccount.remoteHost(urls.mapKeys(RemoteName::value)))
                .getOrElse(GhAccount.GITHUB);
    }
}
