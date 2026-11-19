package net.vanfleteren.jazygit.features.github.checks;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;

/**
 * Why a check has no log to open (ADR 160).
 */
public enum NoLog {
    /**
     * It is queued or runs: GitHub has the log of a job once it is done.
     */
    NOT_DONE,
    /**
     * It was skipped, so it did not run.
     */
    SKIPPED,
    /**
     * It is a check of another app, or a commit status, whose log GitHub does not have.
     */
    NOT_ACTIONS;

    /**
     * Why {@code check} has no log; none when it has one, as far as the checks tell.
     */
    public static Option<NoLog> of(Check check) {
        return check.run()
                .fold(
                        () -> Option.of(NOT_ACTIONS),
                        run -> run.id() == 0 || !check.done()
                                ? Option.of(NOT_DONE)
                                : run.conclusion().equals("SKIPPED") ? Option.of(SKIPPED) : Option.none());
    }
}
