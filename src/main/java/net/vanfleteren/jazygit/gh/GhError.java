package net.vanfleteren.jazygit.gh;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.model.GhAccount;

import java.io.IOException;

/**
 * Why a {@code gh} command failed, as data. Turning it into text is up to the caller.
 */
public sealed interface GhError {

    /**
     * The {@code gh} executable could not be started.
     */
    record GhUnavailable(IOException cause) implements GhError {
    }

    /**
     * @param operation the gh command, e.g. {@code pr create}
     */
    record TimedOut(String operation) implements GhError {
    }

    /**
     * @param operation the gh command, e.g. {@code pr create}
     */
    record Interrupted(String operation) implements GhError {
    }

    /**
     * gh ran, but failed.
     *
     * @param explanation the first line gh printed about why, preferring its error output; none if it
     *                    printed nothing
     */
    record CommandFailed(Option<String> explanation, int exitCode) implements GhError {
    }

    /**
     * The token of {@code account}, which the repository is configured to use, could not be had from gh, say as
     * it is not logged in anymore; no gh command runs without it.
     *
     * @param cause why {@code gh auth token} failed
     */
    record TokenUnavailable(GhAccount account, GhError cause) implements GhError {
    }

    /**
     * gh printed something that could not be made sense of.
     */
    record UnexpectedOutput(String output) implements GhError {
    }
}
