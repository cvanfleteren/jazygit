package net.vanfleteren.jazygit.gh.model;

import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;

/**
 * A workflow run on a commit of a branch, with its jobs: one build of the branch (ADR 131).
 *
 * @param workflow  the name of its workflow
 * @param event     what started it, like {@code push}
 * @param runId     the id of its workflow run
 * @param attempt   which attempt of its run it is, from 1
 * @param sha       the commit it built
 * @param title     the first line of the commit's message
 * @param createdAt when its run was created: of a re-run, when its first attempt was
 * @param jobs      its jobs, the check runs of its run
 * @param file      the path of the file of its workflow, which says which job needs which (ADR 131); none when GitHub
 *                  does not say
 */
public record Build(
        String workflow,
        String event,
        long runId,
        int attempt,
        String sha,
        String title,
        Instant createdAt,
        List<Job> jobs,
        Option<String> file) {

    private static final Set<String> FAILED = HashSet.of("FAILURE", "TIMED_OUT", "STARTUP_FAILURE", "ACTION_REQUIRED");

    /**
     * A build without the file of its workflow.
     */
    public Build(
            String workflow,
            String event,
            long runId,
            int attempt,
            String sha,
            String title,
            Instant createdAt,
            List<Job> jobs) {
        this(workflow, event, runId, attempt, sha, title, createdAt, jobs, Option.none());
    }

    /**
     * How it ended: running while one of its jobs runs; else failed when one failed, timed out or could not start;
     * else cancelled when one was; else passed.
     */
    public Outcome outcome() {
        if (jobs.exists(Job::running)) {
            return Outcome.RUNNING;
        }
        if (jobs.exists(job -> FAILED.contains(job.conclusion()))) {
            return Outcome.FAILED;
        }
        return jobs.exists(job -> job.conclusion().equals("CANCELLED")) ? Outcome.CANCELLED : Outcome.PASSED;
    }

    /**
     * When it started: when its run was created, or of a re-run, when its first job started, as its run keeps the
     * first attempt's start (ADR 32).
     */
    public Instant started() {
        return attempt == 1
                ? createdAt
                : jobs.flatMap(Job::startedAt).minBy(Comparator.naturalOrder()).getOrElse(createdAt);
    }

    /**
     * How long it took, from its start to its last job's end; none while it runs.
     */
    public Option<Duration> duration() {
        return Option.when(
                        outcome() != Outcome.RUNNING,
                        () -> jobs.flatMap(Job::completedAt).maxBy(Comparator.naturalOrder()))
                .flatMap(end -> end)
                .map(end -> Duration.between(started(), end))
                .filter(took -> !took.isNegative());
    }

    /**
     * Its jobs that ran: not skipped, and started.
     */
    public List<Job> ran() {
        return jobs.filter(
                job -> !job.conclusion().equals("SKIPPED") && job.startedAt().isDefined());
    }

    /**
     * How a build ended.
     */
    public enum Outcome {
        PASSED,
        FAILED,
        CANCELLED,
        RUNNING
    }

    /**
     * A job of a build: a check run of its workflow run.
     *
     * @param name        its name, as GitHub shows it, like {@code verify / acceptance (1/6)}
     * @param conclusion  how it ended, as GitHub says it: {@code SUCCESS}, {@code FAILURE}, …; empty while it runs
     * @param startedAt   when it started; none while it is queued
     * @param completedAt when it was done; none while it is not
     */
    public record Job(String name, String conclusion, Option<Instant> startedAt, Option<Instant> completedAt) {

        /**
         * Whether it is not done yet.
         */
        public boolean running() {
            return conclusion.isEmpty();
        }

        /**
         * How long it ran; none while it is not done, or did not start.
         */
        public Option<Duration> duration() {
            return startedAt.flatMap(start -> completedAt.map(end -> Duration.between(start, end)));
        }
    }
}
