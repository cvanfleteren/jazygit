package net.vanfleteren.jazygit.features.forgejo.pulls;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.forgejo.ForgejoMergeStyle;
import net.vanfleteren.jazygit.core.forgejo.ForgejoPullRequest;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.forgejo.show.FailedMerge;
import net.vanfleteren.jazygit.features.forgejo.show.ShownPullRequest;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Part;

/**
 * Forgejo's part of the model for its pull requests (ADR 157, 176): the open ones of the home (ADR 177), by the
 * branch of a remote they merge, the reads of a pull request given out, the merges that failed (ADR 180), and how
 * those jazygit scheduled are to be merged (ADR 181).
 *
 * @param home         the home they were read of
 * @param open         the open pull requests, by the branch they merge; none until they are first read
 * @param failure      why they could not be read the last time, if they could not
 * @param reads        the last read of a pull request given out, which a dialog waits for (ADR 34)
 * @param failedMerges the last merge that failed of each pull request, by its number, while it is open
 * @param scheduled    what is kept of each merge scheduled for when the checks succeed, by the number of its pull
 *                     request, while it is open: the style jazygit scheduled it as, or that Forgejo dropped it
 */
public record ForgejoPullRequests(
        Option<RemoteName> home,
        Option<Map<RemoteBranch, ForgejoPullRequest>> open,
        Option<Failure> failure,
        long reads,
        Map<Long, FailedMerge> failedMerges,
        Map<Long, Scheduling> scheduled) {

    public static final ForgejoPullRequests NONE =
            new ForgejoPullRequests(Option.none(), Option.none(), Option.none(), 0, HashMap.empty(), HashMap.empty());

    // Another worktree of the repository has the same home, and the same pull requests (ADR 182).
    public static final Part<ForgejoPullRequests> PART =
            Part.of("forgejo.pullRequests", NONE).carrying(Option::of);

    /**
     * The open pull request of {@code home} that merges the branch {@code branch} is pushed to (ADR 177), if one does;
     * none from a list of another home.
     */
    public Option<ForgejoPullRequest> of(Branch branch, RemoteName home) {
        return open.filter(prs -> read(home)).flatMap(prs -> prs.get(branch.pushTarget()));
    }

    /**
     * Whether the open pull requests of {@code home} were read.
     */
    public boolean read(RemoteName home) {
        return open.isDefined() && this.home.contains(home);
    }

    /**
     * These once the open pull requests of {@code of} read are {@code read}, or reading them failed as {@code failed}
     * says: the pull requests read before of the same home stay known then. What is kept of a merge of a pull request
     * no longer open is dropped, as is all of it once the home is another.
     */
    public ForgejoPullRequests listed(
            RemoteName of, Option<Map<RemoteBranch, ForgejoPullRequest>> read, Option<Failure> failed) {
        boolean same = home.contains(of);
        Option<Map<RemoteBranch, ForgejoPullRequest>> before = same ? open : Option.none();
        return new ForgejoPullRequests(
                Option.of(of),
                read.orElse(before),
                failed,
                reads,
                stillOpen(failedMerges, same, read),
                stillOpen(scheduled, same, read));
    }

    private static <T> Map<Long, T> stillOpen(
            Map<Long, T> byNumber, boolean same, Option<Map<RemoteBranch, ForgejoPullRequest>> read) {
        return !same
                ? HashMap.empty()
                : read.map(prs -> byNumber.filterKeys(number -> prs.values().exists(pr -> pr.number() == number)))
                        .getOrElse(byNumber);
    }

    /**
     * These with {@code created}, just created in {@code of} (ADR 178), as the open pull request that merges
     * {@code head}, before the list is read again; unchanged when they are of another home, or not read yet.
     */
    public ForgejoPullRequests created(RemoteName of, RemoteBranch head, ForgejoPullRequest created) {
        return new ForgejoPullRequests(
                home,
                open.filter(prs -> read(of)).map(prs -> prs.put(head, created)).orElse(open),
                failure,
                reads,
                failedMerges,
                scheduled);
    }

    /**
     * The style jazygit scheduled the merge of pull request {@code number} as, if it did, for the merge its timeline
     * shows scheduled.
     */
    public Option<ForgejoMergeStyle> style(long number) {
        return scheduled
                .get(number)
                .flatMap(kept -> kept instanceof Scheduling.Ours(var style, var _) ? Option.of(style) : Option.none());
    }

    /**
     * {@code shown} as the dialog shows it: without the merge its timeline says is scheduled when Forgejo said, as it
     * was to be cancelled, that this one was not, as it dropped it when merging it failed.
     */
    public ShownPullRequest shown(ShownPullRequest shown) {
        Option<Scheduling> kept = scheduled.get(shown.pullRequest().number());
        return shown.scheduled()
                        .exists(merge -> kept.exists(dropped -> dropped instanceof Scheduling.Dropped(Instant at)
                                && !merge.at().isAfter(at)))
                ? shown.withoutScheduled()
                : shown;
    }

    /**
     * These once {@code shown} was read, as {@link #shown} gives it: a style jazygit kept is of the merge it shows
     * scheduled first, and forgotten once it shows another, as one scheduled in the browser; a merge Forgejo dropped
     * is forgotten once another is scheduled. One it does not show, as its timeline could not be read, keeps them.
     */
    public ForgejoPullRequests readShown(ShownPullRequest shown) {
        long number = shown.pullRequest().number();
        Map<Long, Scheduling> kept = shown.scheduled()
                .flatMap(merge -> scheduled.get(number).map(was -> keptOnceShown(was, merge.at())))
                .map(now -> now.fold(() -> scheduled.remove(number), is -> scheduled.put(number, is)))
                .getOrElse(scheduled);
        return new ForgejoPullRequests(home, open, failure, reads, failedMerges, kept);
    }

    // What is kept of a merge as was kept, once its timeline shows one scheduled at at.
    private static Option<Scheduling> keptOnceShown(Scheduling was, Instant at) {
        return switch (was) {
            case Scheduling.Ours(var style, var bound)
            when bound.isEmpty() -> Option.of(new Scheduling.Ours(style, Option.of(at)));
            case Scheduling.Ours(var _, var bound) -> bound.contains(at) ? Option.of(was) : Option.none();
            case Scheduling.Dropped(Instant dropped) -> at.isAfter(dropped) ? Option.none() : Option.of(was);
        };
    }

    /**
     * These with the next read of a pull request given out.
     */
    public ForgejoPullRequests givingOutRead() {
        return new ForgejoPullRequests(home, open, failure, reads + 1, failedMerges, scheduled);
    }

    /**
     * These with {@code failed} as the last merge that failed of its pull request, in place of one before.
     */
    public ForgejoPullRequests mergeFailed(FailedMerge failed) {
        return new ForgejoPullRequests(
                home, open, failure, reads, failedMerges.put(failed.number(), failed), scheduled);
    }

    /**
     * These once pull request {@code number} was merged: a merge of it that failed before is forgotten.
     */
    public ForgejoPullRequests merged(long number) {
        return new ForgejoPullRequests(
                home, open, failure, reads, failedMerges.remove(number), scheduled.remove(number));
    }

    /**
     * These once the merge of pull request {@code number} was scheduled as {@code style}: a merge of it that failed
     * before is forgotten, as a new merge takes its place, as on GitHub (ADR 112). Its style is of the merge the next
     * read shows scheduled.
     */
    public ForgejoPullRequests mergeScheduled(long number, ForgejoMergeStyle style) {
        return new ForgejoPullRequests(
                home,
                open,
                failure,
                reads,
                failedMerges.remove(number),
                scheduled.put(number, new Scheduling.Ours(style, Option.none())));
    }

    /**
     * These once the merge of pull request {@code number} was cancelled.
     */
    public ForgejoPullRequests unscheduled(long number) {
        return new ForgejoPullRequests(home, open, failure, reads, failedMerges, scheduled.remove(number));
    }

    /**
     * These once Forgejo said no merge of pull request {@code number} is scheduled, when its timeline said one was at
     * {@code at}: that one is not shown any more, as Forgejo dropped it (ADR 181).
     */
    public ForgejoPullRequests dropped(long number, Instant at) {
        return new ForgejoPullRequests(
                home, open, failure, reads, failedMerges, scheduled.put(number, new Scheduling.Dropped(at)));
    }

    /**
     * What jazygit keeps of the merge of a pull request scheduled for when its checks succeed (ADR 181), which Forgejo
     * does not tell.
     */
    public sealed interface Scheduling {

        /**
         * jazygit scheduled it as {@code style}; {@code at} is when its timeline says it was, once read.
         */
        record Ours(ForgejoMergeStyle style, Option<Instant> at) implements Scheduling {}

        /**
         * Forgejo said none is scheduled, when its timeline said one was at {@code at}: it dropped that one.
         */
        record Dropped(Instant at) implements Scheduling {}
    }
}
