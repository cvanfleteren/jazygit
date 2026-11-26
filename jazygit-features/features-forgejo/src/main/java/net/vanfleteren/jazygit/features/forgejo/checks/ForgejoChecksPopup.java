package net.vanfleteren.jazygit.features.forgejo.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.core.forgejo.ForgejoCheck;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.forgejo.show.ForgejoPullRequestPopup;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Showing the checks on the tip of a branch on Forgejo one by one (ADR 195), as GitHub's are: with how long they took
 * or run (ADR 32), read again every so often (ADR 34), with how far they are together (ADR 89), running the failed
 * ones again (ADR 135), and opening the log of the one picked (ADR 160).
 *
 * @param branch        the branch the user asked for the checks of, local or remote
 * @param onForgejo     the branch on Forgejo whose checks those are: the one a local branch follows
 * @param tip           the commit whose checks are read, when its remote branch is not known
 * @param checks        what was read of them, or why that failed; nothing while they are first read
 * @param reading       whether they are read again, while {@code checks} stay shown, and why
 * @param refreshFailed why reading them again failed the last time, so the checks shown are stale; nothing once a
 *                      read succeeds
 * @param readAt        when the last read of them started
 * @param from          the pull request dialog they were opened from, which Escape goes back to
 * @param rerun         what it says of running failed checks again; nothing before {@code r} was pressed
 * @param picked        the name of the check picked, as the user picked it; nothing to pick the first one listed
 * @param noLogAsked    whether {@code e} was pressed on the check picked, so that it says why that has no log, while it
 *                      has none; not since another was picked
 */
public record ForgejoChecksPopup(
        BranchRef branch,
        RemoteBranch onForgejo,
        Sha tip,
        Option<Either<Failure, ChecksRead>> checks,
        Reading reading,
        Option<Failure> refreshFailed,
        Instant readAt,
        Option<ForgejoPullRequestPopup> from,
        Option<RerunNote> rerun,
        Option<String> picked,
        boolean noLogAsked)
        implements Popup {

    /**
     * How long after the last read the checks are read again, when they are all done: as GitHub's are (ADR 34).
     */
    public static final Duration READ_WHEN_DONE = Duration.ofSeconds(55);

    /**
     * The popup while the checks on {@code tip} are first read, from {@code now}; opened from {@code from}, which it
     * goes back to, if it was.
     */
    public static ForgejoChecksPopup loading(
            BranchRef branch, RemoteBranch onForgejo, Sha tip, Instant now, Option<ForgejoPullRequestPopup> from) {
        return new ForgejoChecksPopup(
                branch,
                onForgejo,
                tip,
                Option.none(),
                Reading.NOT,
                Option.none(),
                now,
                from,
                Option.none(),
                Option.none(),
                false);
    }

    /**
     * The popup saying {@code note} of running failed checks again.
     */
    public ForgejoChecksPopup withRerun(RerunNote note) {
        return new ForgejoChecksPopup(
                branch, onForgejo, tip, checks, reading, refreshFailed, readAt, from, Option.of(note), picked, false);
    }

    /**
     * The popup with {@code check} picked (ADR 160): it no longer says why the one picked before has no log.
     */
    public ForgejoChecksPopup withPicked(ForgejoCheck check) {
        return new ForgejoChecksPopup(
                branch,
                onForgejo,
                tip,
                checks,
                reading,
                refreshFailed,
                readAt,
                from,
                rerun,
                Option.of(check.name()),
                false);
    }

    /**
     * The popup saying why the check picked has no log, while it has none.
     */
    public ForgejoChecksPopup withNoLogAsked() {
        return new ForgejoChecksPopup(
                branch, onForgejo, tip, checks, reading, refreshFailed, readAt, from, rerun, picked, true);
    }

    /**
     * Why the check picked has no log, once {@code e} was pressed on it; none while it has one.
     */
    public Option<NoLog> noLog() {
        return noLogAsked ? pickedCheck().flatMap(NoLog::of) : Option.none();
    }

    /**
     * The checks read in the order they are listed: the failed ones first.
     */
    public List<ForgejoCheck> ordered() {
        return read().map(ChecksRead::ordered).getOrElse(List.empty());
    }

    /**
     * The check picked among those listed: the one the user picked, while it is listed; else the first one listed.
     * None while none are.
     */
    public Option<ForgejoCheck> pickedCheck() {
        List<ForgejoCheck> ordered = ordered();
        return picked.flatMap(name -> ordered.find(check -> check.name().equals(name)))
                .orElse(ordered::headOption);
    }

    /**
     * What was read of the checks, when that succeeded, though they may since have failed to be read again.
     */
    public Option<ChecksRead> read() {
        return checks.flatMap(Either::toOption);
    }

    /**
     * The checks read, in the order they were posted; none while they are first read, or when that failed.
     */
    public List<ForgejoCheck> checksRead() {
        return read().map(ChecksRead::checks).getOrElse(List.empty());
    }

    /**
     * The popup showing {@code result}: the checks, or why reading them failed. Checks that were read already stay
     * shown when reading them again fails, marked stale. Failed checks asked to run again are seen to, once one of
     * them is read not done.
     */
    public ForgejoChecksPopup with(Either<Failure, ChecksRead> result) {
        if (result.isLeft() && read().isDefined()) {
            return new ForgejoChecksPopup(
                    branch,
                    onForgejo,
                    tip,
                    checks,
                    Reading.NOT,
                    Option.of(result.getLeft()),
                    readAt,
                    from,
                    rerun,
                    picked,
                    noLogAsked);
        }
        List<ForgejoCheck> read = result.map(ChecksRead::checks).getOrElse(List.empty());
        return new ForgejoChecksPopup(
                branch,
                onForgejo,
                result.map(ChecksRead::sha).getOrElse(tip),
                Option.of(result),
                Reading.NOT,
                Option.none(),
                readAt,
                from,
                // Why nothing was run again was of the checks before; what was asked to run again stays.
                asked().<RerunNote>map(asked -> asked.reading(read)),
                picked,
                noLogAsked);
    }

    /**
     * The popup while its checks, now on {@code tip}, are read again from {@code now}, as {@code reading} tells.
     */
    public ForgejoChecksPopup readingAgain(Reading reading, Sha tip, Instant now) {
        return new ForgejoChecksPopup(
                branch, onForgejo, tip, checks, reading, refreshFailed, now, from, rerun, picked, noLogAsked);
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
     * last read that did not fail tells, or failed ones were asked to run again and are not seen to yet, or the last
     * read started {@link #READ_WHEN_DONE} ago or more (ADR 34).
     */
    public boolean dueByItself(Instant now) {
        return refreshFailed.isEmpty() && checksRead().exists(check -> !check.done())
                || rerunAwaited(now)
                || !now.isBefore(readAt.plus(READ_WHEN_DONE));
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
}
