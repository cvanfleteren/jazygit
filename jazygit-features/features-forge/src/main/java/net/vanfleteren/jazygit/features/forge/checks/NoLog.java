package net.vanfleteren.jazygit.features.forge.checks;

import io.vavr.control.Option;

/**
 * Why a check has no log to open (ADR 160).
 */
public enum NoLog {

    /**
     * It is queued or runs: the forge has the log of a job once it is done.
     */
    NOT_DONE,

    /**
     * It was skipped, so it did not run.
     */
    SKIPPED,

    /**
     * It is a check of another CI, or a commit status, whose log the forge does not have.
     */
    NOT_ACTIONS;

    /**
     * Why {@code check} has no log; none when it has one, as far as the checks tell.
     */
    public static Option<NoLog> of(ForgeCheck check) {
        return check.job()
                .fold(
                        () -> Option.of(NOT_ACTIONS),
                        job -> job.id().isEmpty() || !check.done()
                                ? Option.of(NOT_DONE)
                                : check.state() == ForgeCheck.State.SKIPPED ? Option.of(SKIPPED) : Option.none());
    }
}
