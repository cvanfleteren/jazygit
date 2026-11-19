package net.vanfleteren.jazygit.core.gh.model;

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
 * @param origin    which workflow it is a run of, and the branch and pull request it ran for, as far as GitHub says
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
        Option<String> file,
        Origin origin) {

    private static final Set<String> FAILED_CONCLUSIONS =
            HashSet.of("FAILURE", "TIMED_OUT", "STARTUP_FAILURE", "ACTION_REQUIRED");

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
        this(workflow, event, runId, attempt, sha, title, createdAt, jobs, Option.none(), Origin.NONE);
    }

    /**
     * A build with the file of its workflow, of which nothing else is known.
     */
    public Build(
            String workflow,
            String event,
            long runId,
            int attempt,
            String sha,
            String title,
            Instant createdAt,
            List<Job> jobs,
            Option<String> file) {
        this(workflow, event, runId, attempt, sha, title, createdAt, jobs, file, Origin.NONE);
    }

    /**
     * How it ended: running while it has no job yet, as when its concurrency group holds it back, or while one of its
     * jobs runs; else failed when one failed, timed out or could not start; else cancelled when one was; else passed.
     */
    public Outcome outcome() {
        return Outcome.of(jobs);
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
     * How long it waited from its run's start to its first job's start, for a runner; none for a re-run, whose run
     * keeps the first attempt's start, or before a job started (ADR 133).
     */
    public Option<Duration> queued() {
        return Option.when(attempt == 1, () -> jobs.flatMap(Job::startedAt).minBy(Comparator.naturalOrder()))
                .flatMap(first -> first)
                .map(first -> Duration.between(createdAt, first))
                .filter(waited -> !waited.isNegative());
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
     * Which workflow a build is a run of, and what it ran for.
     *
     * @param workflowId  the id of its workflow on GitHub, to read the runs of that workflow on every branch with
     * @param branch      the branch it ran on; none when GitHub does not say, as of a branch deleted since
     * @param pullRequest the number of the pull request it ran for, if it ran for one
     */
    public record Origin(Option<String> workflowId, Option<String> branch, Option<Integer> pullRequest) {

        public static final Origin NONE = new Origin(Option.none(), Option.none(), Option.none());
    }

    /**
     * How a build ended.
     */
    public enum Outcome {
        PASSED,
        FAILED,
        CANCELLED,
        RUNNING;

        /**
         * How a build of {@code jobs} ended: running while it has none yet, or while one runs; else failed when one
         * failed, timed out or could not start; else cancelled when one was; else passed.
         */
        public static Outcome of(List<Job> jobs) {
            if (jobs.isEmpty() || jobs.exists(Job::running)) {
                return RUNNING;
            }
            if (jobs.exists(job -> FAILED_CONCLUSIONS.contains(job.conclusion()))) {
                return FAILED;
            }
            return jobs.exists(job -> job.conclusion().equals("CANCELLED")) ? CANCELLED : PASSED;
        }
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
