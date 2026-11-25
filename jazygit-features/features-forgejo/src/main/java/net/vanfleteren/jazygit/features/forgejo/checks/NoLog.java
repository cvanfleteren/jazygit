package net.vanfleteren.jazygit.features.forgejo.checks;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.forgejo.ForgejoCheck;

/**
 * Why a check has no log to open (ADR 160, 195).
 */
public enum NoLog {
    /**
     * It is queued or runs: its log is read once it is done.
     */
    NOT_DONE,
    /**
     * It was skipped, so it did not run.
     */
    SKIPPED,
    /**
     * It is a status of another CI than Forgejo Actions, whose log Forgejo does not have.
     */
    NOT_ACTIONS;

    /**
     * Why {@code check} has no log; none when it has one, as far as the checks tell.
     */
    public static Option<NoLog> of(ForgejoCheck check) {
        return check.job().isEmpty()
                ? Option.of(NOT_ACTIONS)
                : !check.done() ? Option.of(NOT_DONE) : check.skipped() ? Option.of(SKIPPED) : Option.none();
    }
}
