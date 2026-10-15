package net.vanfleteren.jazygit.process;

import java.io.IOException;

/**
 * Why a program run by {@link Exec} did not succeed, as data. The callers turn it into their own errors.
 */
public sealed interface ExecFailure {

    /**
     * The program could not be started, e.g. because it is not installed.
     */
    record NotStarted(IOException cause) implements ExecFailure {}

    record TimedOut() implements ExecFailure {}

    record Interrupted() implements ExecFailure {}

    /**
     * The program ran, and exited with a non-zero code.
     */
    record NonZero(int exitCode, String stderr, String stdout) implements ExecFailure {}
}
