package net.vanfleteren.jazygit.features.forge.checks;

import io.vavr.control.Option;

/**
 * One check on a commit, as every forge has them (ADR 209): what the flow of the checks popup reads of it to pick it,
 * run it again and open its log. What only the forge reads, as how long it took, stays in {@code source}, which the
 * forge wrote and only the forge reads back.
 *
 * @param name   what the forge calls it
 * @param state  how it went, or that it has not yet
 * @param job    the job of the forge's Actions it is; none for a check of another CI, or a commit status
 * @param source the check as the forge read it
 */
public record ForgeCheck(String name, State state, Option<Job> job, Source source) {

    /**
     * Whether it is done: it passed, was skipped or failed.
     */
    public boolean done() {
        return state != State.PENDING;
    }

    /**
     * Whether it failed.
     */
    public boolean failed() {
        return state == State.FAILED;
    }

    /**
     * How a check went.
     */
    public enum State {
        /** It is queued or runs. */
        PENDING,
        /** It passed. */
        PASSED,
        /** It was skipped, so it did not run. */
        SKIPPED,
        /** It failed. */
        FAILED
    }

    /**
     * The job of the forge's Actions a check is.
     *
     * @param run      the run it is part of, as the forge numbers it in the repository
     * @param id       its id, as the forge reads its log by; none while the forge has not given it one yet
     * @param workflow the name of the workflow of its run
     * @param event    what started its run, like {@code push}; empty when the forge does not say
     */
    public record Job(long run, Option<Long> id, String workflow, String event) {}

    /**
     * The check as a forge read it, which only that forge reads back.
     */
    public interface Source {}
}
