package net.vanfleteren.jazygit.gh.model;

import io.vavr.control.Option;
import java.time.Instant;

/**
 * A check run of a GitHub Actions workflow, with when it ran (ADR 32).
 *
 * @param id           its id on GitHub, which tells it apart from every other run of the same check
 * @param workflow     the name of its workflow
 * @param conclusion   how it ended, as GitHub says it: {@code SUCCESS}, {@code FAILURE}, …; empty while it runs
 * @param startedAt    when it started; none while it is queued
 * @param completedAt  when it was done; none while it is not
 * @param runId        the id of its workflow run
 * @param runCreatedAt when its workflow run was created: of a re-run, when its first attempt was
 * @param runAttempt   which attempt of its workflow run it is, from 1
 * @param event        what started its workflow run, like {@code push} or {@code pull_request}
 */
public record CheckRun(
        long id,
        String workflow,
        String conclusion,
        Option<Instant> startedAt,
        Option<Instant> completedAt,
        long runId,
        Instant runCreatedAt,
        int runAttempt,
        String event) {

    /**
     * Whether it passed: not skipped, nor neutral.
     */
    public boolean succeeded() {
        return conclusion.equals("SUCCESS");
    }
}
