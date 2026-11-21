package net.vanfleteren.jazygit.features.github;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.process.GhError;
import net.vanfleteren.jazygit.core.gh.process.GhFailure;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.process.ExecutedCommand;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Phrase;
import net.vanfleteren.jazygit.kernel.ui.text.ErrorText;

/**
 * gh failed: a failure that says itself, as the kernel names no forge (ADR 171).
 *
 * @param error         why gh failed
 * @param commands      the command lines that ran before it failed, the failing one last
 * @param cleanupFailed why cleaning up after the failure with git failed too, if it did
 */
public record GitHubFailure(GhError error, List<ExecutedCommand> commands, Option<GitError> cleanupFailed)
        implements Failure.Phrased {

    public GitHubFailure(GhError error) {
        this(error, List.empty(), Option.none());
    }

    public GitHubFailure(GhError error, List<ExecutedCommand> commands) {
        this(error, commands, Option.none());
    }

    /**
     * gh failed, after running {@code failure}'s commands.
     */
    public static Failure of(GhFailure failure) {
        return new GitHubFailure(failure.error(), failure.commands());
    }

    /**
     * gh refused before any command ran.
     */
    public static Failure of(GhError error) {
        return new GitHubFailure(error);
    }

    /**
     * Whether gh did not run as the account chosen for the repository could not be had.
     */
    public static boolean accountUnavailable(Failure failure) {
        return failure instanceof GitHubFailure(GhError.TokenUnavailable unavailable, var commands, var cleanup);
    }

    /**
     * Whether GitHub no longer has what gh acted on, as a comment someone deleted meanwhile.
     */
    public static boolean gone(Failure failure) {
        return failure instanceof GitHubFailure(GhError.Gone gone, var commands, var cleanup);
    }

    @Override
    public Phrase phrase() {
        return cleanupFailed
                .<Phrase>map(cleanup ->
                        Phrase.of("error.cleanupFailed", said(error), new Phrase.Failed(new Failure.Git(cleanup))))
                .getOrElse(() -> said(error));
    }

    /**
     * {@code error} in the user's language.
     */
    public static String text(GhError error) {
        return ErrorText.of(said(error));
    }

    /**
     * What {@code error} says.
     */
    public static Phrase said(GhError error) {
        return switch (error) {
            case GhError.GhUnavailable(var cause) -> Phrase.of("github.gh.notInstalled");
            case GhError.TimedOut(String operation) -> Phrase.of("github.gh.timedOut", operation);
            case GhError.Interrupted(String operation) -> Phrase.of("github.gh.interrupted", operation);
            case GhError.TooMuchOutput(String operation) -> Phrase.of("github.gh.tooMuchOutput", operation);
            case GhError.CommandFailed(var explanation, int exitCode) ->
                explanation
                        .map(Phrase::verbatim)
                        .getOrElse(() -> Phrase.of("github.gh.exitedWithCode", String.valueOf(exitCode)));
            case GhError.UnexpectedOutput(String output) -> Phrase.of("github.gh.unexpectedOutput", output);
            case GhError.TooOld(String needed) -> Phrase.of("github.gh.tooOld", needed);
            case GhError.Gone() -> Phrase.of("github.gh.gone");
            case GhError.BrowserFailed(String browser, var exitCode) ->
                exitCode.map(code -> Phrase.of("github.gh.browserFailed", browser, String.valueOf(code)))
                        .getOrElse(() -> Phrase.of("github.gh.browserNotStarted", browser));
            case GhError.NoBrowser() -> Phrase.of("github.gh.noBrowser");
            case GhError.FileFailed(var file, var cause) ->
                Phrase.of("github.gh.fileFailed", String.valueOf(file), ErrorText.reason(cause));
            case GhError.TokenUnavailable(var account, GhError cause) ->
                Phrase.of(
                        "github.gh.tokenUnavailable",
                        Phrase.verbatim(account.user()),
                        Phrase.verbatim(account.host()),
                        said(cause));
        };
    }
}
