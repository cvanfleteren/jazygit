package net.vanfleteren.jazygit.core.gh;

import io.vavr.control.Option;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.core.gh.account.CliAccount;
import net.vanfleteren.jazygit.core.gh.checks.CliChecks;
import net.vanfleteren.jazygit.core.gh.model.GhAccount;
import net.vanfleteren.jazygit.core.gh.process.GhProcess;
import net.vanfleteren.jazygit.core.gh.process.GhToken;
import net.vanfleteren.jazygit.core.gh.pullrequests.CliPullRequests;
import net.vanfleteren.jazygit.core.gh.reviews.CliReviews;
import net.vanfleteren.jazygit.core.gh.stacks.CliStacks;
import net.vanfleteren.jazygit.core.process.SshAsking;

/**
 * Gives the {@link GitHub} of the repository in a working tree, whose roles go through the {@code gh} command line
 * tool (ADR 114). gh finds the GitHub repository from the git remotes itself.
 */
public final class CliGitHub {

    private CliGitHub() {}

    /**
     * GitHub for the repository in {@code worktree}, through the gh at {@code executable}, as the account of
     * {@code token}.
     */
    public static GitHub of(Path worktree, String executable, GhToken token) {
        return of(worktree, executable, token, () -> GhAccount.GITHUB, account -> Option.none());
    }

    /**
     * GitHub as {@link #of(Path, String, GhToken)}, for a repository whose remotes are on {@code host}, opening links
     * in the browser {@code browsers} gives the account the repository uses, if any (ADR 88).
     */
    public static GitHub of(
            Path worktree,
            String executable,
            GhToken token,
            Supplier<String> host,
            Function<GhAccount, Option<String>> browsers) {
        return of(
                worktree,
                executable,
                token,
                host,
                browsers,
                name -> Option.of(System.getenv(name)),
                System.getProperty("os.name", ""));
    }

    /**
     * GitHub as {@link #of(Path, String, GhToken)}, choosing the browser by the variables {@code environment} gives
     * and the system {@code os}.
     */
    public static GitHub of(
            Path worktree, String executable, GhToken token, Function<String, Option<String>> environment, String os) {
        return of(worktree, executable, token, () -> GhAccount.GITHUB, account -> Option.none(), environment, os);
    }

    /**
     * GitHub as {@link #of(Path, String, GhToken, Supplier, Function)}, choosing the browser by the variables
     * {@code environment} gives and the system {@code os}.
     */
    public static GitHub of(
            Path worktree,
            String executable,
            GhToken token,
            Supplier<String> host,
            Function<GhAccount, Option<String>> browsers,
            Function<String, Option<String>> environment,
            String os) {
        return of(worktree, executable, token, host, browsers, environment, os, SshAsking.Unchanged.INSTANCE);
    }

    /**
     * GitHub as {@link #of(Path, String, GhToken, Supplier, Function)}, where ssh may ask the user as {@code ssh} lets
     * it while gh checks a pull request out (ADR 150).
     */
    public static GitHub of(
            Path worktree,
            String executable,
            GhToken token,
            Supplier<String> host,
            Function<GhAccount, Option<String>> browsers,
            SshAsking ssh) {
        return of(
                worktree,
                executable,
                token,
                host,
                browsers,
                name -> Option.of(System.getenv(name)),
                System.getProperty("os.name", ""),
                ssh);
    }

    /**
     * GitHub as {@link #of(Path, String, GhToken, Supplier, Function, SshAsking)}, choosing the browser by the
     * variables {@code environment} gives and the system {@code os}.
     */
    public static GitHub of(
            Path worktree,
            String executable,
            GhToken token,
            Supplier<String> host,
            Function<GhAccount, Option<String>> browsers,
            Function<String, Option<String>> environment,
            String os,
            SshAsking ssh) {
        GhProcess process = new GhProcess(worktree, executable, token);
        return new GitHub(
                new CliPullRequests(process, ssh),
                new CliReviews(process),
                new CliChecks(process),
                new CliStacks(process),
                new CliAccount(
                        worktree,
                        process,
                        new GhProcess(worktree, executable, GhToken.NONE),
                        token,
                        host,
                        browsers,
                        environment,
                        os));
    }
}
