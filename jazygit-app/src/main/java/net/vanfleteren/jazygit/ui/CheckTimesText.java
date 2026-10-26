package net.vanfleteren.jazygit.ui;

import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.timing.Eta;
import net.vanfleteren.jazygit.i18n.Messages;

/**
 * How long checks have run and are expected to take still (ADR 32). In the dialogs how long they ran, which is
 * exact, is rounded down to 5s; what is left, an estimate, to the nearest 5s while that is under 5m, else to the
 * nearest minute (ADR 34). In the branches pane, which has no room for seconds, in whole minutes.
 */
public final class CheckTimesText {

    // From how long on an estimate in a dialog is shown in whole minutes, not to 5s.
    private static final long TO_THE_SECOND = Duration.ofMinutes(5).toSeconds();

    private CheckTimesText() {}

    /**
     * How long it is since {@code since}, in a dialog, rounded down to 5s as a clock that counts up, as it is exact:
     * {@code <5s}, {@code 45s}, {@code 2m}, {@code 7m 40s}, {@code 1h 5m 40s}.
     */
    public static String duration(Duration since) {
        long seconds = since.toSeconds();
        return seconds < 5 ? Messages.get("checks.underFiveSeconds") : toFiveSeconds(seconds / 5 * 5);
    }

    /**
     * How long something is expected to take still, {@code left}, in a dialog, to the nearest 5s, {@code ~45s},
     * {@code ~2m 5s}; from 5m on to the nearest minute, {@code ~7m}. Only when it is not {@link #due}.
     */
    public static String left(Duration left) {
        long seconds = Math.round(left.toSeconds() / 5.0) * 5;
        return Messages.get(
                "checks.about",
                seconds >= TO_THE_SECOND ? minutes(Math.round(left.toSeconds() / 60.0)) : toFiveSeconds(seconds));
    }

    /**
     * {@code seconds}, a step of 5s: {@code 45s}, {@code 2m}, {@code 2m 5s}, {@code 1h 5m 40s}.
     */
    private static String toFiveSeconds(long seconds) {
        return seconds < 60
                ? Messages.get("checks.seconds", String.valueOf(seconds))
                : seconds % 60 == 0
                        ? minutes(seconds / 60)
                        : seconds < 3600
                                ? Messages.get(
                                        "checks.minutesSeconds",
                                        String.valueOf(seconds / 60),
                                        String.valueOf(seconds % 60))
                                : Messages.get(
                                        "checks.hoursSeconds",
                                        String.valueOf(seconds / 3600),
                                        String.valueOf(seconds / 60 % 60),
                                        String.valueOf(seconds % 60));
    }

    /**
     * Whether what is expected to take {@code left} still, in a dialog, should be done by now: it rounds to no time
     * left, or is past.
     */
    private static boolean due(Duration left) {
        return Math.round(left.toSeconds() / 5.0) <= 0;
    }

    /**
     * How long something is expected to take still, {@code left}, in the branches pane: {@code ~3m}, to the nearest
     * minute, or {@code <1m}, also once it is past.
     */
    private static String leftInMinutes(Duration left) {
        return left.toSeconds() < 60
                ? Messages.get("checks.underAMinute")
                : Messages.get("checks.about", minutes(Math.round(left.toSeconds() / 60.0)));
    }

    /**
     * {@code minutes}, one or more: {@code 2m}, {@code 1h 5m}.
     */
    private static String minutes(long minutes) {
        return minutes < 60
                ? Messages.get("checks.minutes", String.valueOf(minutes))
                : Messages.get("checks.hours", String.valueOf(minutes / 60), String.valueOf(minutes % 60));
    }

    /**
     * How the checks of a commit are expected to be done together, {@code eta}, at {@code now}: "done in ~3m",
     * "done any moment" once that is due, "done in ?", or "…" for the estimate while the checks that ran lately are
     * read; nothing when they are done.
     */
    public static Option<String> together(Eta eta, boolean learning, Instant now) {
        return switch (eta) {
            case Eta.Done done -> Option.none();
            case Eta.Unknown unknown -> Option.of(Messages.get("checks.doneIn", unknown(learning)));
            case Eta.Expected expected when expected.overdue(now) -> Option.of(Messages.get("checks.longerThanUsual"));
            case Eta.Expected expected when due(expected.left(now)) -> Option.of(Messages.get("checks.doneAnyMoment"));
            case Eta.Expected expected -> Option.of(Messages.get("checks.doneIn", left(expected.left(now))));
        };
    }

    /**
     * When the checks of a branch are expected to be done, {@code eta}, at {@code now}, to show next to their symbol
     * in a list: {@code ~4m}, {@code ?}, {@code late}, or {@code …} while the checks that ran lately are read;
     * nothing when they are done.
     */
    public static Option<String> brief(Eta eta, boolean learning, Instant now) {
        return switch (eta) {
            case Eta.Done done -> Option.none();
            case Eta.Unknown unknown -> Option.of(unknown(learning));
            case Eta.Expected expected ->
                Option.of(expected.overdue(now) ? Messages.get("checks.late") : leftInMinutes(expected.left(now)));
        };
    }

    /**
     * How {@code check}, which is not done, is doing at {@code now}, with {@code eta}: "running 2m · ~2m 5s left",
     * "queued · ~4m left", "running 2m 30s · due", "running 3m · overdue"; only "running" for a commit status, which
     * is not timed.
     */
    public static String of(Check check, Eta eta, boolean learning, Instant now) {
        if (check.run().isEmpty()) {
            return Messages.get("checks.running");
        }
        String state = check.queued()
                ? Messages.get("checks.queued")
                : check.run()
                        .flatMap(CheckRun::startedAt)
                        .map(started -> Messages.get("checks.runningFor", duration(Duration.between(started, now))))
                        .getOrElse(() -> Messages.get("checks.running"));
        String left = switch (eta) {
            case Eta.Expected expected when expected.overdue(now) -> Messages.get("checks.overdue");
            case Eta.Expected expected when due(expected.left(now)) -> Messages.get("checks.due");
            case Eta.Expected expected -> Messages.get("checks.left", left(expected.left(now)));
            case Eta.Unknown u -> Messages.get("checks.left", unknown(learning));
            case Eta.Done done -> "";
        };
        return left.isEmpty() ? state : state + " · " + left;
    }

    private static String unknown(boolean learning) {
        return Messages.get(learning ? "checks.learning" : "checks.unknown");
    }
}
