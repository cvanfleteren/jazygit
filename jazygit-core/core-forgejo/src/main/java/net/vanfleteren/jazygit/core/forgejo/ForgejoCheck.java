package net.vanfleteren.jazygit.core.forgejo;

import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.List;
import io.vavr.collection.Seq;
import io.vavr.control.Option;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A check on a commit on Forgejo, as the statuses posted for one context tell it (ADR 195): a CI posts a status each
 * time the check moves on, as Forgejo Actions does for a job when it is made, starts and ends, and again when it is run
 * again.
 *
 * @param name        its context, as {@code build / test (push)}
 * @param state       how it went, as its last status says
 * @param description what its last status says, as {@code Failing after 11m2s}
 * @param url         where its last status links to; none when it links nowhere
 * @param created     when its first status of this attempt was posted
 * @param started     when it started: when Forgejo Actions said its job started running; else when it was posted,
 *                    for a status of another CI, which says nothing of when; none while a job waits to run
 * @param ended       when it ended: when its last status was posted, once that says it passed or failed
 */
public record ForgejoCheck(
        String name,
        CommitStatus.State state,
        String description,
        Option<String> url,
        Instant created,
        Option<Instant> started,
        Option<Instant> ended) {

    // What Forgejo Actions says of a job, in its statuses: these are not translated.
    static final String WAITING = "Waiting to run";
    static final String BLOCKED = "Blocked by required conditions";
    static final String RUNNING = "Has started running";
    static final String SKIPPED = "Has been skipped";

    // Where Forgejo Actions links a job's status to: .../actions/runs/<run>/jobs/<job>, maybe with /attempt/<n>.
    private static final Pattern JOB_LINK = Pattern.compile("/actions/runs/(\\d+)/jobs/(\\d+)(?:/attempt/\\d+)?/?$");

    /**
     * The checks of a commit, from {@code statuses}, all those posted for it, in any order: one for each context, as
     * its last status says, timed from the first of its current attempt, which starts after the last status that ended
     * an earlier one. In the order the contexts were first posted.
     */
    public static List<ForgejoCheck> of(List<Posted> statuses) {
        return statuses.sortBy(Posted::id)
                .groupBy(Posted::context)
                .values()
                .map(ForgejoCheck::folded)
                .toList()
                .sortBy(check -> check._1)
                .map(check -> check._2)
                .filter(check -> check.state() != CommitStatus.State.NONE);
    }

    private static Tuple2<Long, ForgejoCheck> folded(Seq<Posted> posted) {
        List<Posted> all = List.ofAll(posted).sortBy(Posted::id);
        Posted last = all.last();
        // This attempt: after the last status that ended an earlier one.
        List<Posted> attempt = all.drop(all.init().lastIndexWhere(Posted::ends) + 1);
        Posted first = attempt.head();
        CommitStatus.State state = CommitStatus.State.named(last.status());
        boolean done = state == CommitStatus.State.PASSED || state == CommitStatus.State.FAILED;
        boolean actions = last.targetUrl().exists(url -> JOB_LINK.matcher(url).find());
        Option<Instant> started = actions
                ? attempt.find(status -> status.description().equals(RUNNING))
                        .map(Posted::createdAt)
                        .orElse(() -> Option.when(done, first::createdAt))
                : Option.of(first.createdAt());
        return Tuple.of(
                all.head().id(),
                new ForgejoCheck(
                        last.context(),
                        state,
                        last.description(),
                        last.targetUrl(),
                        first.createdAt(),
                        started,
                        Option.when(done, last::createdAt)));
    }

    /**
     * Whether it is done: it passed or failed.
     */
    public boolean done() {
        return state == CommitStatus.State.PASSED || state == CommitStatus.State.FAILED;
    }

    /**
     * Whether it waits to start: a job of Forgejo Actions that waits to run, or for the jobs it needs.
     */
    public boolean queued() {
        return !done() && (description.equals(WAITING) || description.equals(BLOCKED));
    }

    /**
     * Whether it was skipped, so did not run: Forgejo Actions says it passed.
     */
    public boolean skipped() {
        return description.equals(SKIPPED);
    }

    /**
     * The job of Forgejo Actions it is, as its link says; none for a status of another CI.
     */
    public Option<ActionsJob> job() {
        return url.flatMap(link -> {
            Matcher matcher = JOB_LINK.matcher(link);
            return Option.when(
                    matcher.find(),
                    () -> new ActionsJob(Long.parseLong(matcher.group(1)), Integer.parseInt(matcher.group(2))));
        });
    }

    /**
     * A job of Forgejo Actions, as the link of its status names it.
     *
     * @param run the number of its run in the repository, as in {@code #12}
     * @param job where it is among the jobs of its run, from 0
     */
    public record ActionsJob(long run, int job) {}

    /**
     * A status posted for a commit, as Forgejo gives it.
     *
     * @param id          its id, which orders the statuses
     * @param context     what it is of
     * @param status      Forgejo's name of its state, as {@code pending}
     * @param description what the CI says of it
     * @param targetUrl   where it links to
     * @param createdAt   when it was posted
     */
    public record Posted(
            long id, String context, String status, String description, Option<String> targetUrl, Instant createdAt) {

        /**
         * Whether it ended an attempt: it says the check passed or failed.
         */
        boolean ends() {
            CommitStatus.State state = CommitStatus.State.named(status);
            return state == CommitStatus.State.PASSED || state == CommitStatus.State.FAILED;
        }
    }
}
