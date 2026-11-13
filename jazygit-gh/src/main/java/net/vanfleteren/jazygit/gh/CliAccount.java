package net.vanfleteren.jazygit.gh;

import static net.vanfleteren.jazygit.gh.GhCommand.gh;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.gh.GhError.UnexpectedOutput;
import net.vanfleteren.jazygit.gh.model.*;
import net.vanfleteren.jazygit.process.Exec;
import net.vanfleteren.jazygit.process.ExecFailure;
import net.vanfleteren.jazygit.process.ExecutedCommand;

/**
 * {@link Account} through gh, for the repository in a working tree (ADR 114).
 */
final class CliAccount implements Account {

    private final Path worktree;
    private final GhProcess process;
    // As gh's own active accounts: with a token in the environment, gh only knows of that one.
    private final GhProcess unconfigured;
    private final GhToken token;
    // The GitHub host of the remotes, which can be an ssh alias.
    private final Supplier<String> host;
    // The browser set for an account, if any (ADR 88).
    private final Function<GhAccount, Option<String>> browsers;
    // The environment and the system the browser is chosen by, as gh chooses it (ADR 85).
    private final Function<String, Option<String>> environment;
    private final String os;

    CliAccount(
            Path worktree,
            GhProcess process,
            GhProcess unconfigured,
            GhToken token,
            Supplier<String> host,
            Function<GhAccount, Option<String>> browsers,
            Function<String, Option<String>> environment,
            String os) {
        this.worktree = worktree;
        this.process = process;
        this.unconfigured = unconfigured;
        this.token = token;
        this.host = host;
        this.browsers = browsers;
        this.environment = environment;
        this.os = os;
    }

    /**
     * The version in the first line {@code gh --version} prints, e.g. {@code gh version 2.102.0 (2026-09-30)}.
     */
    static Either<UnexpectedOutput, String> parseVersion(String raw) {
        return List.of(raw.split("\\R"))
                .headOption()
                .map(String::strip)
                .filter(line -> line.startsWith("gh version "))
                .map(line -> line.substring("gh version ".length()).split(" ")[0])
                .toEither(() -> new UnexpectedOutput(raw));
    }

    static GhCommand browseCommand(GhBranch branch) {
        return gh("browse", "--branch", branch.name());
    }

    @Override
    public Either<GhError, String> version(String executable) {
        return new GhProcess(worktree, executable, GhToken.NONE).read(gh("--version"), CliAccount::parseVersion);
    }

    @Override
    public Either<GhError, List<GhLogin>> logins() {
        // Exits with 0 also when an account's token does not work anymore, which the state says.
        return unconfigured.readJson(LoginsQuery.COMMAND, LoginsQuery.DECODER);
    }

    @Override
    public Option<GhAccount> current() {
        return token.configured()
                .map(configured -> new GhAccount(SshHost.resolve(worktree, configured.host()), configured.user()))
                .orElse(() -> activeAccount(SshHost.resolve(worktree, host.get())));
    }

    @Override
    public Option<ChosenBrowser> ghBrowser(String host) {
        return Browser.chosen(environment, () -> configuredBrowser(host), os);
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> openPullRequest(int number) {
        return web(gh("pr", "view", String.valueOf(number), "--web"), accountBrowser());
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> openPullRequest(String url) {
        return web(gh("pr", "view", url, "--web"), accountBrowser());
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> openInBrowser(PullRequestLink link) {
        Option<String> account = accountBrowser();
        if (link.host().isEmpty()) {
            return web(gh("pr", "view", String.valueOf(link.number()), "--web"), account);
        }
        return account.orElse(() -> ghBrowser(link.host()).map(ChosenBrowser::command))
                .map(browser -> launch(browser, link.url()))
                .getOrElse(() -> web(gh("pr", "view", link.pullRequest(), "--web"), account));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> openLink(String url) {
        return accountBrowser()
                .orElse(() -> ghBrowser(SshHost.resolve(worktree, host.get())).map(ChosenBrowser::command))
                .orElse(() -> Browser.desktop(os))
                .map(browser -> launch(browser, url))
                .getOrElse(() -> Either.left(new GhFailure(new GhError.NoBrowser(), List.empty())));
    }

    @Override
    public Either<GhFailure, List<ExecutedCommand>> browse(GhBranch branch) {
        return web(browseCommand(branch), accountBrowser());
    }

    /**
     * gh's active account on {@code host}, as its own files name it; none when a token in the environment makes gh
     * run as the account of that token instead, which its files do not name, or when gh names none.
     */
    private Option<GhAccount> activeAccount(String host) {
        List<String> tokens = GhAccount.isGitHubCom(host)
                ? List.of("GH_TOKEN", "GITHUB_TOKEN")
                : List.of("GH_ENTERPRISE_TOKEN", "GITHUB_ENTERPRISE_TOKEN");
        if (tokens.exists(name -> environment.apply(name).exists(value -> !value.isBlank()))) {
            return Option.none();
        }
        return unconfigured
                .read(gh("config", "get", "user", "-h", host), raw -> Either.right(raw.strip()))
                .toOption()
                .filter(user -> !user.isEmpty())
                .map(user -> new GhAccount(host, user));
    }

    /**
     * The browser set for the account the repository uses now, if any (ADR 88).
     */
    private Option<String> accountBrowser() {
        return current().flatMap(browsers).map(String::strip).filter(browser -> !browser.isEmpty());
    }

    /**
     * Runs {@code command}, which opens something in the browser, in {@code browser} when one is set for the account.
     */
    private Either<GhFailure, List<ExecutedCommand>> web(GhCommand command, Option<String> browser) {
        return process.log(
                browser.map(set -> command.withEnvironment("GH_BROWSER", set)).getOrElse(command));
    }

    /**
     * gh's {@code browser} setting for {@code host}, or its own; none when it is not set, or cannot be read.
     */
    private Option<String> configuredBrowser(String host) {
        return process.read(gh("config", "get", "browser", "--host", host), Either::right)
                .toOption();
    }

    /**
     * Opens {@code url} in {@code browser}, as it is set, without waiting for it to close.
     */
    private Either<GhFailure, List<ExecutedCommand>> launch(String browser, String url) {
        List<String> commandLine = Browser.commandLine(browser, url);
        return Exec.launch(worktree, commandLine, Browser.SETTLE)
                .mapLeft(failure -> new GhFailure(
                        switch (failure) {
                            case ExecFailure.Interrupted() -> new GhError.Interrupted("browser");
                            case ExecFailure.NonZero(int exitCode, var stderr, var stdout) ->
                                new GhError.BrowserFailed(browser, Option.of(exitCode));
                            default -> new GhError.BrowserFailed(browser, Option.none());
                        },
                        List.of(new ExecutedCommand(commandLine))))
                .map(List::of);
    }
}
