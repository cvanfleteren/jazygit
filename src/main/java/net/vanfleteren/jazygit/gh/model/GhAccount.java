package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An account gh is logged in to, which gh commands for a repository run as.
 *
 * @param host the GitHub host, e.g. {@code github.com}; it can be an ssh host alias, until resolved
 * @param user the login of the account
 */
public record GhAccount(String host, String user) {

    /**
     * The host of github.com, where a repository is when its remotes do not say.
     */
    public static final String GITHUB = "github.com";

    private static final String ORIGIN = "origin";
    // [user@]host:path, as scp writes it; a ':' right before a '/' or '\' is a Windows drive or a URL instead.
    private static final Pattern SCP_LIKE = Pattern.compile("^(?:[^@/]+@)?([^:/\\\\]+):(?![/\\\\])");

    /**
     * Whether {@code host} is GitHub itself rather than a GitHub Enterprise Server: github.com, or a
     * {@code ghe.com} data residency host.
     */
    public static boolean isGitHubCom(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.equals(GITHUB) || lower.endsWith(".ghe.com");
    }

    /**
     * The GitHub host of the repository with the remotes {@code urls}, by name: the host of {@code origin}, else
     * that of the first remote on a host named like GitHub; none when there is neither.
     */
    public static Option<String> remoteHost(Map<String, String> urls) {
        return urls.get(ORIGIN).flatMap(GhAccount::hostOf)
                .orElse(() -> urls.toList().sortBy(remote -> remote._1)
                        .flatMap(remote -> hostOf(remote._2))
                        .find(host -> host.toLowerCase(Locale.ROOT).contains("github")
                                || host.toLowerCase(Locale.ROOT).endsWith(".ghe.com")));
    }

    /**
     * The host of the git remote {@code url}: an {@code https://} or {@code ssh://} URL, or {@code [user@]host:path}
     * as scp writes it; none for a local path.
     */
    public static Option<String> hostOf(String url) {
        String stripped = url.strip();
        if (stripped.contains("://")) {
            return Try.of(() -> URI.create(stripped).getHost()).toOption()
                    .filter(host -> host != null && !host.isEmpty());
        }
        Matcher scp = SCP_LIKE.matcher(stripped);
        return Option.when(scp.find(), () -> scp.group(1));
    }
}
