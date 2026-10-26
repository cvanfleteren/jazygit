package net.vanfleteren.jazygit.ui;

import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.timing.Eta;
import net.vanfleteren.jazygit.i18n.Messages;

/**
 * How long checks have run and are expected to take still (ADR 32), in seconds rounded to the nearest 5: the clock moves
 * in steps of 5s.
 */
public final class CheckTimesText {

    private CheckTimesText() {}

    /**
     * How long it is since {@code since}, to the nearest 5s: {@code 0s}, {@code 45s}, {@code 2m}, {@code 2m 5s}, {@code 1h 5m}.
     */
    public static String duration(Duration since) {
        long seconds = Math.max(0, Math.round(since.toSeconds() / 5.0) * 5);
        long minutes = seconds / 60;
        return minutes == 0
                ? Messages.get("checks.seconds", String.valueOf(seconds))
                : minutes < 60 && seconds % 60 != 0
                        ? Messages.get("checks.minutesSeconds", String.valueOf(minutes), String.valueOf(seconds % 60))
                        : minutes < 60
                                ? Messages.get("checks.minutes", String.valueOf(minutes))
                                : Messages.get(
                                        "checks.hours", String.valueOf(minutes / 60), String.valueOf(minutes % 60));
    }

    /**
     * How long something is expected to take still, {@code left}: {@code ~2m 5s}, to the nearest 5s, or
     * {@code <5s}, also once it is past.
     */
    public static String left(Duration left) {
        return Math.round(left.toSeconds() / 5.0) <= 0
                ? Messages.get("checks.underFiveSeconds")
                : Messages.get("checks.about", duration(left));
    }

    /**
     * How the checks of a commit are expected to be done together, {@code eta}, at {@code now}: "done in ~3m",
     * "done in ?", or "…" for the estimate while the checks that ran lately are read; nothing when they are done.
     */
    public static Option<String> together(Eta eta, boolean learning, Instant now) {
        return switch (eta) {
            case Eta.Done done -> Option.none();
            case Eta.Unknown unknown -> Option.of(Messages.get("checks.doneIn", unknown(learning)));
            case Eta.Expected expected ->
                Option.of(
                        expected.overdue(now)
                                ? Messages.get("checks.longerThanUsual")
                                : Messages.get("checks.doneIn", left(expected.left(now))));
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
                Option.of(expected.overdue(now) ? Messages.get("checks.late") : left(expected.left(now)));
        };
    }

    /**
     * How {@code check}, which is not done, is doing at {@code now}, with {@code eta}: "running 2m · ~2m left",
     * "queued · ~4m left", "running 3m · overdue"; only "running" for a commit status, which is not timed.
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
