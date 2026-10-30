package net.vanfleteren.jazygit.gh;

import io.vavr.control.Option;
import java.io.IOException;
import net.vanfleteren.jazygit.gh.model.GhAccount;

/**
 * Why a {@code gh} command failed, as data. Turning it into text is up to the caller.
 */
public sealed interface GhError {

    /**
     * The {@code gh} executable could not be started.
     */
    record GhUnavailable(IOException cause) implements GhError {}

    /**
     * The {@code gh} command did not finish in time.
     *
     * @param operation the gh command, e.g. {@code pr create}
     */
    record TimedOut(String operation) implements GhError {}

    /**
     * The {@code gh} command was interrupted.
     *
     * @param operation the gh command, e.g. {@code pr create}
     */
    record Interrupted(String operation) implements GhError {}

    /**
     * gh ran, but failed.
     *
     * @param explanation the first line gh printed about why, preferring its error output; none if it
     *                    printed nothing
     */
    record CommandFailed(Option<String> explanation, int exitCode) implements GhError {}

    /**
     * The token of {@code account}, which the repository is configured to use, could not be had from gh, say as
     * it is not logged in anymore; no gh command runs without it.
     *
     * @param cause why {@code gh auth token} failed
     */
    record TokenUnavailable(GhAccount account, GhError cause) implements GhError {}

    /**
     * gh is older than jazygit needs: it did not know a flag that came with {@code needed}, like {@code --slurp}
     * with 2.48 (ADR 42).
     *
     * @param needed the version of gh it needs at least, like {@code 2.48}
     */
    record TooOld(String needed) implements GhError {}

    /**
     * gh printed something that could not be made sense of.
     *
     * @param output why: where its JSON did not decode and what was wrong there, like
     *     {@code hosts.github.com[0].state: missing} (ADR 42), or, for what is not read as JSON yet, what gh printed
     */
    record UnexpectedOutput(String output) implements GhError {}
}
