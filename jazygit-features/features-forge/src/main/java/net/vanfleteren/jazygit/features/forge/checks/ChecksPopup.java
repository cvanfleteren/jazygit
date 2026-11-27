package net.vanfleteren.jazygit.features.forge.checks;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import lombok.With;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.popup.CheckList;
import net.vanfleteren.jazygit.kernel.state.popup.Popup;

/**
 * Showing the checks on the tip of a branch on a forge one by one (ADR 32, 209), read again every so often (ADR 34),
 * running the failed ones again (ADR 135), and opening the log of the one picked (ADR 160).
 *
 * @param port          the forge the checks are on
 * @param branch        the branch the user asked for the checks of, local or remote
 * @param onForge       the branch on the forge whose checks those are: the one a local branch follows
 * @param commit        the commit the checks are of, for a forge that reads them by commit: the one they were last
 *                      read of, else the one to read them of
 * @param checks        what was read of them, or why that failed; nothing while they are first read
 * @param reading       whether they are read again, while {@code checks} stay shown, and why
 * @param refreshFailed why reading them again failed the last time, so the checks shown are stale; nothing once a
 *                      read succeeds
 * @param readAt        when the last read of them started
 * @param reached       how far the checks were shown to be when they were last read again, as the forge keeps it;
 *                      nothing before that, or for a forge that does not
 * @param from          the popup they were opened from, which Escape goes back to; kept up to date as it is read
 *                      meanwhile
 * @param rerun         what it says of running failed checks again; nothing before {@code r} was pressed
 * @param picked        the check picked, as the user picked it; nothing to pick the first one listed
 * @param noLogAsked    whether {@code e} was pressed on the check picked, so that it says why that has no log, while it
 *                      has none; not since another was picked
 */
@With
public record ChecksPopup(
        ChecksPort port,
        BranchRef branch,
        RemoteBranch onForge,
        Option<Sha> commit,
        Option<Either<Failure, ChecksRead>> checks,
        Reading reading,
        Option<Failure> refreshFailed,
        Instant readAt,
        Option<Reached> reached,
        Option<Popup> from,
        Option<RerunNote> rerun,
        Option<CheckPick> picked,
        boolean noLogAsked)
        implements Popup {

    /**
     * How long after the last read the checks are read again by themselves once they are all done, as they may be run
     * again: just under a minute, so that every other tick of 10 s reads them.
     */
    public static final Duration READ_WHEN_DONE = Duration.ofSeconds(55);

    /**
     * The checks listed at once; the others are scrolled to.
     */
    public static final int VISIBLE_CHECKS = CheckList.VISIBLE;

    /**
     * The popup of the checks on {@code port} where {@code opening} says, while they are first read, from {@code now}.
     */
    public static ChecksPopup loading(ChecksPort port, ChecksPort.Opening opening, Instant now) {
        return new ChecksPopup(
                port,
                opening.branch(),
                opening.onForge(),
                opening.commit(),
                Option.none(),
                Reading.NOT,
                Option.none(),
                now,
                Option.none(),
                opening.from(),
                Option.none(),
                Option.none(),
                false);
    }

    /**
     * The popup saying {@code note} of running failed checks again.
     */
    public ChecksPopup noting(RerunNote note) {
        return withRerun(Option.of(note)).withNoLogAsked(false);
    }

    /**
     * The popup with {@code check} picked (ADR 160): it no longer says why the one picked before has no log.
     */
    public ChecksPopup picking(ForgeCheck check) {
        return withPicked(Option.of(CheckPick.of(check))).withNoLogAsked(false);
    }

    /**
     * Why the check picked in {@code model} has no log, once {@code e} was pressed on it; none while it has one.
     */
    public Option<NoLog> noLog(Model model) {
        return noLogAsked ? pickedCheck(model).flatMap(NoLog::of) : Option.none();
    }

    /**
     * The checks read in the order they are listed in {@code model}: the failed ones first.
     */
    public List<ForgeCheck> ordered(Model model) {
        return port.ordered(model, this);
    }

    /**
     * The check picked among those listed in {@code model}: the one the user picked, while it is listed; else the
     * first one listed. None while none are.
     */
    public Option<ForgeCheck> pickedCheck(Model model) {
        List<ForgeCheck> ordered = ordered(model);
        return picked.flatMap(pick -> ordered.find(pick::matches)).orElse(ordered::headOption);
    }

    /**
     * What was read of the checks, while the last read that did not fail tells.
     */
    public Option<ChecksRead> read() {
        return checks.flatMap(Either::toOption);
    }

    /**
     * The popup showing {@code read}: the checks, or why reading them failed. Checks that were read already stay
     * shown when reading them again fails, marked stale. Failed checks asked to run again are seen to, once one of
     * them is read not done.
     */
    public ChecksPopup showing(Either<Failure, ChecksRead> read) {
        if (read.isLeft() && wasRead()) {
            return withReading(Reading.NOT).withRefreshFailed(Option.of(read.getLeft()));
        }
        List<ForgeCheck> checksRead = read.map(ChecksRead::checks).getOrElse(List.empty());
        return withCommit(read.toOption().flatMap(ChecksRead::commit).orElse(commit))
                .withChecks(Option.of(read))
                .withReading(Reading.NOT)
                .withRefreshFailed(Option.none())
                // Why nothing was run again was of the checks before; what was asked to run again stays.
                .withRerun(asked().map(asked -> asked.reading(checksRead)));
    }

    /**
     * The popup showing {@code read}, as it is shown in {@code model}: how far the checks it shows were stays reached.
     */
    public ChecksPopup showing(Either<Failure, ChecksRead> read, Model model) {
        return reaching(model).showing(read);
    }

    /**
     * The checks may go under the menu of the editors to open a log in, as they are told there what is read of them
     * meanwhile (ADR 206).
     */
    @Override
    public boolean coverable() {
        return true;
    }

    /**
     * Once what was learned of how long checks take changes from what {@code before} knows, how far the checks were
     * shown to be in it stays reached, as when they are read again; in the popup it goes back to too.
     */
    @Override
    public ChecksPopup checkTimesChanging(Model before) {
        return reaching(before).withFrom(from.map(popup -> popup.checkTimesChanging(before)));
    }

    /**
     * The popup keeping how far its checks are shown to be in {@code model}, as the forge keeps it; as it is while none
     * are read.
     */
    private ChecksPopup reaching(Model model) {
        return checksRead().isEmpty() ? this : withReached(port.reached(model, this));
    }

    /**
     * The popup while its checks are read again from {@code now}, as {@code reading} tells.
     */
    public ChecksPopup readingAgain(Reading reading, Instant now) {
        return withReading(reading).withReadAt(now);
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
     * the last read started {@link #READ_WHEN_DONE} ago or more. After a read failed, the checks shown are stale, so
     * they are read every minute, as ADR 19 has a failing read do (ADR 34).
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

    /**
     * The checks read, in the order the forge gave them; none while they are first read, or when that failed.
     */
    public List<ForgeCheck> checksRead() {
        return read().map(ChecksRead::checks).getOrElse(List.empty());
    }

    /**
     * Whether its checks were read, though they may since have failed to be read again.
     */
    public boolean wasRead() {
        return checks.exists(Either::isRight);
    }

    /**
     * How far the checks of a popup were shown to be, as the forge keeps it, which only the forge reads.
     */
    public interface Reached {}
}
