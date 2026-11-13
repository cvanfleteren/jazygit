package net.vanfleteren.jazygit.gh.process;

import static net.vanfleteren.jazygit.gh.process.GhCommand.gh;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.gh.model.GhAccount;

/**
 * The token of the account a repository is configured to use with gh, as the environment variables that make gh,
 * and git through gh's credential helper, use it instead of gh's active account. gh's own configuration is left
 * as it is, so other repositories and other programs keep using the active account.
 *
 * <p>The token is asked from gh once, the first time it is needed, and kept in memory only. That it could not be
 * had is kept too: gh commands are not run as another account than the one configured.
 */
public final class GhToken {

    /**
     * No account configured: gh runs as its active account.
     */
    public static final GhToken NONE = new GhToken(Path.of(""), "gh", Option::none);

    private final Path worktree;
    private final String executable;
    private final Supplier<Option<GhAccount>> account;
    // The variables, or why they could not be had; none until first asked.
    private Option<Either<GhError, Map<String, String>>> known = Option.none();

    /**
     * The token of the {@code account} configured for the repository in {@code worktree}, if any, from the gh at
     * {@code executable}; the account is only looked up once the token is needed.
     */
    public GhToken(Path worktree, String executable, Supplier<Option<GhAccount>> account) {
        this.worktree = worktree;
        this.executable = executable;
        this.account = account;
    }

    /**
     * The variables that make gh use {@code token} for {@code host}: {@code GH_TOKEN} for GitHub itself, and
     * {@code GH_ENTERPRISE_TOKEN} with the host for a GitHub Enterprise Server.
     */
    static Map<String, String> variables(String host, String token) {
        return GhAccount.isGitHubCom(host)
                ? HashMap.of("GH_TOKEN", token)
                : HashMap.of("GH_ENTERPRISE_TOKEN", token, "GH_HOST", host);
    }

    /**
     * The environment variables that make gh and git use the configured account; none when there is no account
     * configured.
     */
    public synchronized Either<GhError, Map<String, String>> environment() {
        if (known.isEmpty()) {
            known = Option.of(account.get().fold(() -> Either.right(HashMap.empty()), this::fetch));
        }
        return known.get();
    }

    /**
     * The account the repository is configured to use, if any, on the host as its remotes give it.
     */
    public Option<GhAccount> configured() {
        return account.get();
    }

    /**
     * Forgets the token, so the next {@link #environment()} asks gh again: for when GitHub did not accept it.
     */
    public synchronized void forget() {
        known = Option.none();
    }

    private Either<GhError, Map<String, String>> fetch(GhAccount configured) {
        String host = SshHost.resolve(worktree, configured.host());
        GhAccount resolved = new GhAccount(host, configured.user());
        return new GhProcess(worktree, executable, NONE)
                .read(
                        gh("auth", "token", "--user", resolved.user(), "--hostname", resolved.host()),
                        raw -> Option.of(raw.strip())
                                .filter(token -> !token.isEmpty())
                                .toEither(() -> new GhError.UnexpectedOutput(raw)))
                .map(token -> variables(host, token))
                .mapLeft(cause -> new GhError.TokenUnavailable(resolved, cause));
    }
}
