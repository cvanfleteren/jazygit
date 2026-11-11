package net.vanfleteren.jazygit.feature.github.show;

import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.GhCommit;
import net.vanfleteren.jazygit.gh.model.PullRequest;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails;
import net.vanfleteren.jazygit.gh.model.PullRequestStack;
import net.vanfleteren.jazygit.gh.timing.CheckTimings;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.PullRequestCommits;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.github.CheckTimes;
import net.vanfleteren.jazygit.state.github.ChecksReached;
import net.vanfleteren.jazygit.state.github.Reading;
import net.vanfleteren.jazygit.state.popup.Popup;

/**
 * Showing the pull request of {@code branch}.
 *
 * @param pullRequest   what is known of it from the list of pull requests
 * @param fromFork      whether it comes from a fork, as one opened on the pull requests screen can: its head branch
 *                      is not a branch of this repository, whatever it is called
 * @param details       what was read about it, or why that failed; nothing while it is first being read
 * @param reading       whether it is being read again, while {@code details} stay shown, and why
 * @param refreshFailed why reading it again failed the last time, so the details shown are stale; nothing once a
 *                      read succeeds
 * @param readAt        when the last read of it started
 * @param read          the id of the last read of it that it started, which it waits for while it reads (ADR 34)
 * @param filesWanted   whether the files it changes are shown in its place once its details are read, as f on its
 *                      branch asks; the read it waits for, taken or not, ends that
 * @param reached       how far its checks were shown to be when its details were last read again, or what was
 *                      learned of how long checks take changed, so they are not shown going back (ADR 89)
 */
public record PullRequestPopup(
        BranchName branch,
        PullRequest pullRequest,
        boolean fromFork,
        Option<Either<Failure, PullRequestDetails>> details,
        Reading reading,
        Option<Failure> refreshFailed,
        Instant readAt,
        long read,
        boolean filesWanted,
        Option<ChecksReached> reached)
        implements ShowsPullRequest {

    /**
     * The popup while its details are being read, from {@code now}, by read {@code read}.
     */
    public static PullRequestPopup loading(BranchName branch, PullRequest pullRequest, Instant now, long read) {
        return new PullRequestPopup(
                branch, pullRequest, false, Option.none(), Reading.NOT, Option.none(), now, read, false, Option.none());
    }

    /**
     * The popup of a pull request that comes from a fork.
     */
    public PullRequestPopup ofFork() {
        return new PullRequestPopup(
                branch, pullRequest, true, details, reading, refreshFailed, readAt, read, filesWanted, reached);
    }

    /**
     * The popup while its details are being read, to show the files it changes in its place once they are.
     */
    public PullRequestPopup wantingFiles() {
        return new PullRequestPopup(
                branch, pullRequest, fromFork, details, reading, refreshFailed, readAt, read, true, reached);
    }

    /**
     * The popup showing {@code result}, of read {@code id}: the details, or why reading them failed. Details that
     * were read already stay shown when reading them again fails, marked stale. How far the base moved on is read
     * apart from the rest and can fail alone: the count shown stays when the pull request is still open at the same
     * commit on the same base, where the base can only have moved on further. Only the read it waits for changes it:
     * one it did not start, like one of the dialog before it was opened again, is left to the pull requests listed
     * (ADR 34).
     */
    public PullRequestPopup with(Either<Failure, PullRequestDetails> result, long id) {
        return id != read
                ? this
                : result.isLeft() && detailsRead()
                        ? new PullRequestPopup(
                                branch,
                                pullRequest,
                                fromFork,
                                details,
                                Reading.NOT,
                                Option.of(result.getLeft()),
                                readAt,
                                read,
                                false,
                                reached)
                        : new PullRequestPopup(
                                branch,
                                pullRequest,
                                fromFork,
                                Option.of(result.map(this::keepingBehindBase)),
                                Reading.NOT,
                                Option.none(),
                                readAt,
                                read,
                                false,
                                reached);
    }

    /**
     * The popup once read {@code id} came back too late to be taken (ADR 34): what it shows stays, and when that is
     * the read it waits for, it no longer reads.
     */
    PullRequestPopup notTaken(long id) {
        return id == read
                ? new PullRequestPopup(
                        branch,
                        pullRequest,
                        fromFork,
                        details,
                        Reading.NOT,
                        refreshFailed,
                        readAt,
                        read,
                        false,
                        reached)
                : this;
    }

    private PullRequestDetails keepingBehindBase(PullRequestDetails read) {
        return read.branches().behindBase().isDefined()
                        || read.status().state() != PullRequestDetails.State.OPEN
                        || read.branches().headCommit().isEmpty()
                ? read
                : readDetails()
                        .filter(shown -> shown.branches()
                                        .headCommit()
                                        .equals(read.branches().headCommit())
                                && shown.branches()
                                        .base()
                                        .equals(read.branches().base()))
                        .flatMap(shown -> shown.branches().behindBase())
                        .map(read::withBehindBase)
                        .getOrElse(read);
    }

    /**
     * The popup while its details are read again from {@code now} by read {@code id}, as {@code reading} tells.
     */
    public PullRequestPopup readingAgain(Reading reading, Instant now, long id) {
        return new PullRequestPopup(
                branch, pullRequest, fromFork, details, reading, refreshFailed, now, id, filesWanted, reached);
    }

    /**
     * The popup showing the read it does by itself as one asked for.
     */
    PullRequestPopup asked() {
        return new PullRequestPopup(
                branch,
                pullRequest,
                fromFork,
                details,
                Reading.ASKED,
                refreshFailed,
                readAt,
                read,
                filesWanted,
                reached);
    }

    /**
     * Whether its details are read again, while they stay shown.
     */
    public boolean refreshing() {
        return reading != Reading.NOT;
    }

    /**
     * Whether its details are due to be read again by themselves at {@code now}: one of its checks is not done, as far
     * as the last read that did not fail tells, or the last read started {@link CheckTimings#READ_WHEN_DONE} ago or
     * more. After a read failed, the details shown are stale, so they are read every minute, as ADR 19 has a failing
     * read do (ADR 34).
     */
    public boolean dueByItself(Instant now) {
        return refreshFailed.isEmpty()
                        && readDetails().exists(read -> read.checks().exists(check -> !check.done()))
                || !now.isBefore(readAt.plus(CheckTimings.READ_WHEN_DONE));
    }

    /**
     * The popup once the pull request became a draft when {@code draft}, else ready for review.
     */
    PullRequestPopup withDraft(boolean draft) {
        return new PullRequestPopup(
                branch,
                pullRequest,
                fromFork,
                details.map(read -> read.map(pr -> pr.withDraft(draft))),
                reading,
                refreshFailed,
                readAt,
                read,
                filesWanted,
                reached);
    }

    /**
     * Whether it is a draft, once its details are read.
     */
    public Option<Boolean> draft() {
        return readDetails().map(read -> read.status().draft());
    }

    /**
     * Whether its details were read, rather than being read still or having failed to.
     */
    public boolean detailsRead() {
        return readDetails().isDefined();
    }

    /**
     * Whether it is known to be open, so it can be closed: once its details are read.
     */
    public boolean open() {
        return readDetails().exists(details -> details.status().state() == PullRequestDetails.State.OPEN);
    }

    /**
     * Whether it can be merged as far as is known: it is open, not a draft, and does not conflict. GitHub works
     * out whether it conflicts lazily, so an unknown answer does not stop it. Failing checks, missing reviews
     * and branch protection are left to gh, whose refusal is shown as the error of the merge.
     */
    public boolean mergeable() {
        return open()
                && readDetails()
                        .exists(details -> !details.status().draft()
                                && details.status().mergeable() != PullRequestDetails.Mergeable.CONFLICTING);
    }

    /**
     * The commit its branch was at when it was read, which a merge is only done at; none before it is read.
     */
    public Option<GhCommit> head() {
        return readDetails().flatMap(read -> read.branches().headCommit());
    }

    /**
     * The commits its changes are diffed between, to list the files it changes (ADR 55): only once it is read and
     * known not to be merged, a draft or a closed one too, at commits that are known. A merged one is not diffed:
     * {@code base...head} no longer shows what GitHub kept of it.
     */
    public Option<PullRequestCommits> commits() {
        return readDetails()
                .filter(read -> read.status().state() != PullRequestDetails.State.MERGED)
                .flatMap(read -> read.branches()
                        .baseCommit()
                        .flatMap(base -> Sha.parse(base.oid()))
                        .flatMap(base -> read.branches()
                                .headCommit()
                                .flatMap(head -> Sha.parse(head.oid()))
                                .map(head -> new PullRequestCommits(
                                        read.number(), read.repository(), read.host(), base, head))));
    }

    /**
     * Where it sits in its stack, if it is stacked: as it was read, else as it is listed.
     */
    public Option<PullRequestStack> stack() {
        return readDetails().map(PullRequestDetails::stack).getOrElse(pullRequest::stack);
    }

    /**
     * How far its checks are together in {@code model}, no less far than they were shown to be (ADR 89); 0 before its
     * details are read.
     */
    public double progress(Model model) {
        return readDetails()
                .map(pr -> ChecksReached.progress(reached, pr.checks(), times(model, pr), model.now()))
                .getOrElse(0.0);
    }

    /**
     * How long checks take, as learned in {@code model}, of the repository of {@code pr}.
     */
    public static Option<CheckTimes> times(Model model, PullRequestDetails pr) {
        return model.gitHub().checkTimes().filter(times -> times.repository().equals(pr.repository()));
    }

    /**
     * The popup keeping how far its checks are shown to be in {@code model}, as the checks popup does (ADR 89); as it
     * is while its details are not read, or it has no checks.
     */
    public PullRequestPopup reaching(Model model) {
        return readDetails()
                .filter(pr -> !pr.checks().isEmpty())
                .map(pr -> new PullRequestPopup(
                        branch,
                        pullRequest,
                        fromFork,
                        details,
                        reading,
                        refreshFailed,
                        readAt,
                        read,
                        filesWanted,
                        ChecksReached.keeping(pr.checks(), progress(model))))
                .getOrElse(this);
    }

    /**
     * Once what was learned of how long checks take changes from what {@code before} knows, how far its checks were
     * shown to be in it stays reached, as when its details are read again.
     */
    @Override
    public Popup checkTimesChanging(Model before) {
        return reaching(before);
    }

    @Override
    public PullRequestPopup pullRequestPopup() {
        return this;
    }

    @Override
    public PullRequestPopup withPullRequestPopup(PullRequestPopup dialog) {
        return dialog;
    }

    /**
     * Its details, once they were read.
     */
    public Option<PullRequestDetails> readDetails() {
        return details.flatMap(Either::toOption);
    }
}
