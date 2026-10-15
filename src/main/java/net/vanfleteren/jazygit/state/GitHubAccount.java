package net.vanfleteren.jazygit.state;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.GhAccount;
import net.vanfleteren.jazygit.git.GitInfoProvider;

/**
 * The gh account a repository is configured to use, kept in its own git configuration, so it stays with the
 * repository, and other repositories keep using gh's active account.
 */
public final class GitHubAccount {

    /**
     * The key of the account's login in the repository's git configuration.
     */
    public static final String USER_KEY = "jazygit.githubUser";

    private GitHubAccount() {
    }

    /**
     * The account configured for the repository of {@code git}, on the GitHub host of its remotes; none when
     * there is none configured, or the configuration could not be read.
     */
    public static Option<GhAccount> configured(GitInfoProvider git) {
        return git.localConfig(USER_KEY).getOrElse(Option.none())
                .filter(user -> !user.isBlank())
                .map(user -> new GhAccount(host(git), user));
    }

    /**
     * The GitHub host of the remotes of {@code git}; github.com when they do not say.
     */
    public static String host(GitInfoProvider git) {
        return git.remoteUrls().toOption().flatMap(GhAccount::remoteHost).getOrElse(GhAccount.GITHUB);
    }
}
