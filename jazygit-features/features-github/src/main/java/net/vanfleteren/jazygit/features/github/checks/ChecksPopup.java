package net.vanfleteren.jazygit.features.github.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Instant;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.core.gh.timing.CheckTimings;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.github.show.PullRequestPopup;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.github.CheckTimes;
import net.vanfleteren.jazygit.kernel.state.github.ChecksReached;
import net.vanfleteren.jazygit.kernel.state.github.GitHubState;
import net.vanfleteren.jazygit.kernel.state.github.PendingChecks;
import net.vanfleteren.jazygit.kernel.state.github.Reading;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Showing the checks on the tip of a branch one by one, with how long they took or run (ADR 32), read again every so
 * often (ADR 34), with how far they are together (ADR 89), running the failed ones again (ADR 135), and opening the log
 * of the one picked (ADR 160).
 *
 * @param branch        the branch the user asked for the checks of, local or remote
 * @param onGitHub      the branch on GitHub whose checks those are: the one a local branch follows
 * @param checks        what was read of them, or why that failed; nothing while they are first read
 * @param reading       whether they are read again, while {@code checks} stay shown, and why
 * @param refreshFailed why reading them again failed the last time, so the checks shown are stale; nothing once a
 *                      read succeeds
 * @param readAt        when the last read of them started
 * @param reached       how far the checks were shown to be when they were last read again, or what was learned of
 *                      how long they take changed, so they are not shown going back when they are expected later
 *                      than before; nothing before that, or once they were done
 * @param from          the pull request dialog they were opened from, which Escape goes back to; kept up to date as
 *                      its details are read meanwhile
 * @param rerun         what it says of running failed checks again; nothing before {@code r} was pressed
 * @param picked        the check picked, as the user picked it; nothing to pick the first one listed
 * @param noLogAsked    whether {@code e} was pressed on the check picked, so that it says why that has no log, while it
 *                      has none; not since another was picked
 */
public record ChecksPopup(
        BranchRef branch,
        RemoteBranch onGitHub,
        Option<Either<Failure, PendingChecks>> checks,
        Reading reading,
        Option<Failure> refreshFailed,
        Instant readAt,
        Option<ChecksReached> reached,
        Option<PullRequestPopup> from,
        Option<RerunNote> rerun,
        Option<CheckPick> picked,
        boolean noLogAsked)
        implements Popup {

    /**
     * The checks listed at once; the others are scrolled to.
     */
    public static final int VISIBLE_CHECKS = 16;

    /**
     * The popup while the checks are first read, from {@code now}.
     */
    public static ChecksPopup loading(BranchRef branch, RemoteBranch onGitHub, Instant now) {
        return new ChecksPopup(
                branch,
                onGitHub,
                Option.none(),
                Reading.NOT,
                Option.none(),
                now,
                Option.none(),
                Option.none(),
                Option.none(),
                Option.none(),
                false);
    }

    /**
     * The popup while the checks are first read, opened from the pull request dialog {@code from}, which it goes back
     * to.
     */
    public static ChecksPopup loading(BranchRef branch, RemoteBranch onGitHub, Instant now, PullRequestPopup from) {
        return new ChecksPopup(
                branch,
                onGitHub,
                Option.none(),
                Reading.NOT,
                Option.none(),
                now,
                Option.none(),
                Option.of(from),
                Option.none(),
                Option.none(),
                false);
    }

    /**
     * The popup going back to {@code dialog}, the pull request dialog it was opened from.
     */
    public ChecksPopup withFrom(PullRequestPopup dialog) {
        return new ChecksPopup(
                branch,
                onGitHub,
                checks,
                reading,
                refreshFailed,
                readAt,
                reached,
                Option.of(dialog),
                rerun,
                picked,
                noLogAsked);
    }

    /**
     * The popup saying {@code note} of running failed checks again.
     */
    public ChecksPopup withRerun(RerunNote note) {
        return new ChecksPopup(
                branch,
                onGitHub,
                checks,
                reading,
                refreshFailed,
                readAt,
                reached,
                from,
                Option.of(note),
                picked,
                false);
    }

    /**
     * The popup with {@code check} picked (ADR 160): it no longer says why the one picked before has no log.
     */
    public ChecksPopup withPicked(Check check) {
        return new ChecksPopup(
                branch,
                onGitHub,
                checks,
                reading,
                refreshFailed,
                readAt,
                reached,
                from,
                rerun,
                Option.of(CheckPick.of(check)),
                false);
    }

    /**
     * The popup saying why the check picked has no log, while it has none.
     */
    public ChecksPopup withNoLogAsked() {
        return new ChecksPopup(
                branch, onGitHub, checks, reading, refreshFailed, readAt, reached, from, rerun, picked, true);
    }

    /**
     * Why the check picked in {@code model} has no log, once {@code e} was pressed on it; none while it has one.
     */
    public Option<NoLog> noLog(Model model) {
        return noLogAsked ? pickedCheck(model).flatMap(NoLog::of) : Option.none();
    }

    /**
     * The checks read in the order they are listed, as far as {@code model} tells how long they take: the failed ones
     * first.
     */
    public List<Check> ordered(Model model) {
        List<Check> all = checksRead();
        return CheckTimes.order(all, all, times(model));
    }

    /**
     * The check picked among those listed in {@code model}: the one the user picked, while it is listed; else the
     * first one listed. None while none are.
     */
    public Option<Check> pickedCheck(Model model) {
        List<Check> ordered = ordered(model);
        return picked.flatMap(pick -> ordered.find(pick::matches)).orElse(ordered::headOption);
    }

    /**
     * The repository the checks read are of, as {@code owner/name}; none while none are read.
     */
    public Option<String> repository() {
        return checks.flatMap(Either::toOption).map(PendingChecks::repository);
    }

    /**
     * The popup showing {@code read}: the checks, or why reading them failed. Checks that were read already stay
     * shown when reading them again fails, marked stale. Failed checks asked to run again are seen to, once one of
     * them is read not done.
     */
    public ChecksPopup with(Either<Failure, PendingChecks> read) {
        if (read.isLeft() && wasRead()) {
            return new ChecksPopup(
                    branch,
                    onGitHub,
                    checks,
                    Reading.NOT,
                    Option.of(read.getLeft()),
                    readAt,
                    reached,
                    from,
                    rerun,
                    picked,
                    noLogAsked);
        }
        List<Check> checksRead =
                read.map(pending -> pending.of(onGitHub.branch())).getOrElse(List.empty());
        return new ChecksPopup(
                branch,
                onGitHub,
                Option.of(read),
                Reading.NOT,
                Option.none(),
                readAt,
                reached,
                from,
                // Why nothing was run again was of the checks before; what was asked to run again stays.
                asked().<RerunNote>map(asked -> asked.reading(checksRead)),
                picked,
                noLogAsked);
    }

    /**
     * The popup showing {@code read}, as it is shown in {@code model}: how far the checks it shows were stays reached.
     */
    public ChecksPopup with(Either<Failure, PendingChecks> read, Model model) {
        return reaching(model).with(read);
    }

    /**
     * Once what was learned of how long checks take changes from what {@code before} knows, how far the checks were
     * shown to be in it stays reached, as when they are read again; in the pull request dialog it goes back to too.
     */
    @Override
    public ChecksPopup checkTimesChanging(Model before) {
        ChecksPopup reaching = reaching(before);
        return from.map(dialog -> reaching.withFrom(dialog.reaching(before))).getOrElse(reaching);
    }

    /**
     * The popup keeping how far its checks are shown to be in {@code model}, as reached by those same checks; nothing
     * once they are done, as they are then shown full anyway, nor when they are only commit statuses, which no
     * workflow run tells apart from those of another commit, and which are counted, so do not go back; as it is while
     * none are read.
     */
    private ChecksPopup reaching(Model model) {
        List<Check> all = checksRead();
        if (all.isEmpty()) {
            return this;
        }
        return new ChecksPopup(
                branch,
                onGitHub,
                checks,
                reading,
                refreshFailed,
                readAt,
                ChecksReached.keeping(all, progress(model)),
                from,
                rerun,
                picked,
                noLogAsked);
    }

    /**
     * The popup while its checks are read again from {@code now}, as {@code reading} tells.
     */
    public ChecksPopup readingAgain(Reading reading, Instant now) {
        return new ChecksPopup(
                branch, onGitHub, checks, reading, refreshFailed, now, reached, from, rerun, picked, noLogAsked);
    }

    /**
     * Whether the checks are read again, while they stay shown.
     */
    public boolean refreshing() {
        return reading != Reading.NOT;
    }

    /**
     * Whether the checks can be read again: once they were read, or that failed, and no other read runs.
     */
    public boolean refreshable() {
        return checks.isDefined() && !refreshing();
    }

    /**
     * Whether the checks are due to be read again by themselves at {@code now}: one of them is not done, as far as the
     * last read that did not fail tells, or failed ones were asked to run again and are not seen to yet (ADR 135), or
     * the last read started {@link CheckTimings#READ_WHEN_DONE} ago or more. After a read failed, the checks shown are
     * stale, so they are read every minute, as ADR 19 has a failing read do (ADR 34).
     */
    public boolean dueByItself(Instant now) {
        return refreshFailed.isEmpty() && checksRead().exists(check -> !check.done())
                || rerunAwaited(now)
                || !now.isBefore(readAt.plus(CheckTimings.READ_WHEN_DONE));
    }

    /**
     * Whether failed checks were asked to run again, and are not seen to yet at {@code now}, for a minute at most.
     */
    public boolean rerunAwaited(Instant now) {
        return asked().exists(asked -> asked.awaited(now));
    }

    /**
     * What it says of failed checks asked to run again; nothing when they were not.
     */
    public Option<RerunNote.Asked> asked() {
        return rerun.filter(RerunNote.Asked.class::isInstance).map(RerunNote.Asked.class::cast);
    }

    /**
     * The checks read, in no particular order; none while they are first read, or when that failed.
     */
    public List<Check> checksRead() {
        return checks.flatMap(Either::toOption)
                .map(read -> read.of(onGitHub.branch()))
                .getOrElse(List.empty());
    }

    /**
     * How long the checks read take, as learned in {@code model}, of the repository they are of; nothing while they are
     * first read, or when that failed.
     */
    public Option<CheckTimes> times(Model model) {
        return checks.flatMap(Either::toOption)
                .flatMap(read -> model.part(GitHubState.PART)
                        .checkTimes()
                        .filter(known -> known.repository().equals(read.repository())));
    }

    /**
     * How far the checks read are together in {@code model}: at least as far as they were reached to be, while they
     * are the same checks, of the same attempts of the same workflow runs. A workflow run again, or the checks of
     * another commit, start from 0.
     */
    public double progress(Model model) {
        return ChecksReached.progress(reached, checksRead(), times(model), model.now());
    }

    /**
     * Whether its checks were read, though they may since have failed to be read again.
     */
    public boolean wasRead() {
        return checks.exists(Either::isRight);
    }
}
