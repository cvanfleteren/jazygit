package net.vanfleteren.jazygit.ui;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.CheckRun;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.timing.Eta;
import net.vanfleteren.jazygit.i18n.Messages;

/**
 * How long checks have run and are expected to take still (ADR 32). In the dialogs how long they ran, which is
 * exact, is shown to the second, from an hour on to the minute (ADR 37); what is left, an estimate, to the nearest 5s
 * while that is under 5m, else to the nearest minute (ADR 34). In the branches pane, which has no room for seconds, in
 * whole minutes.
 */
public final class CheckTimesText {

    // From how long on an estimate in a dialog is shown in whole minutes, not to 5s.
    private static final long TO_THE_SECOND = Duration.ofMinutes(5).toSeconds();

    private CheckTimesText() {}

    /**
     * How long it is since {@code since}, in a dialog, rounded down as a clock that counts up, as it is exact: to the
     * second, {@code 0s}, {@code 47s}, {@code 2m}, {@code 7m 41s}; from an hour on to the minute, {@code 1h 5m}.
     */
    public static String duration(Duration since) {
        long seconds = Math.max(0, since.toSeconds());
        return seconds >= 3600 ? minutes(seconds / 60) : minutesAndSeconds(seconds);
    }

    /**
     * How long something is expected to take still, {@code left}, in a dialog, to the nearest 5s, {@code ~45s},
     * {@code ~2m 5s}; from 5m on to the nearest minute, {@code ~7m}. Only when it is not {@link #due}.
     */
    public static String left(Duration left) {
        long seconds = Math.round(left.toSeconds() / 5.0) * 5;
        return Messages.get(
                "checks.about",
                seconds >= TO_THE_SECOND ? minutes(Math.round(left.toSeconds() / 60.0)) : minutesAndSeconds(seconds));
    }

    /**
     * {@code seconds}, under an hour: {@code 45s}, {@code 2m}, {@code 2m 5s}.
     */
    private static String minutesAndSeconds(long seconds) {
        return seconds < 60
                ? Messages.get("checks.seconds", String.valueOf(seconds))
                : seconds % 60 == 0
                        ? minutes(seconds / 60)
                        : Messages.get(
                                "checks.minutesSeconds", String.valueOf(seconds / 60), String.valueOf(seconds % 60));
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
     * How long checks have waited to start, {@code since}, in the branches pane: in whole minutes, rounded down as it
     * is exact, {@code 3m}, {@code 1h 5m}, or {@code <1m} (ADR 35).
     */
    public static String waited(Duration since) {
        return since.toSeconds() < 60 ? Messages.get("checks.underAMinute") : minutes(since.toMinutes());
    }

    /**
     * {@code minutes}, one or more: {@code 2m}, {@code 1h}, {@code 1h 5m}; a whole hour without its {@code 0m}, as a
     * whole minute is without its {@code 0s}.
     */
    private static String minutes(long minutes) {
        return minutes < 60
                ? Messages.get("checks.minutes", String.valueOf(minutes))
                : minutes % 60 == 0
                        ? Messages.get("checks.wholeHours", String.valueOf(minutes / 60))
                        : Messages.get("checks.hours", String.valueOf(minutes / 60), String.valueOf(minutes % 60));
    }

    /**
     * How a check that runs shows how it is doing at its widest, {@code running 59m 59s · ~4m 55s left}, so a column
     * of them can keep its width while their seconds move on.
     */
    public static String widestRunning() {
        return widestState() + " · " + widestLeft();
    }

    /**
     * How long a check that is not done runs or is queued at its widest: {@code running 59m 59s}.
     */
    private static String widestState() {
        String widest = duration(Duration.ofSeconds(3599));
        return widest(List.of(
                Messages.get("checks.runningFor", widest),
                Messages.get("checks.queuedFor", widest),
                Messages.get("checks.running"),
                Messages.get("checks.queued")));
    }

    /**
     * How long a check that is not done is expected to take still at its widest: {@code ~4m 55s left}.
     */
    private static String widestLeft() {
        return widest(List.of(
                Messages.get("checks.left", left(Duration.ofSeconds(297))),
                Messages.get("checks.left", unknown(false)),
                Messages.get("checks.left", unknown(true)),
                Messages.get("checks.due"),
                Messages.get("checks.overdue")));
    }

    private static String widest(List<String> texts) {
        return texts.maxBy(TextWidth::of).get();
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
     * "queued 1m 30s · ~4m left", "running 2m 30s · due", "running 3m · overdue"; only "running" for a commit status, which
     * is not timed.
     */
    public static String of(Check check, Eta eta, boolean learning, Instant now) {
        return state(check, now)
                + remaining(check, eta, learning, now).map(left -> " · " + left).getOrElse("");
    }

    /**
     * Like {@link #of}, in two columns as wide as those of a check that runs can get, so those of checks under each
     * other line up and keep their place as the seconds move on: how long it runs on the left, how long it takes still
     * on the right, {@code running 45s     ·     ~1m left}. Wider only when one is wider still, as one of hours.
     */
    public static String lined(Check check, Eta eta, boolean learning, Instant now) {
        String state = state(check, now);
        int leftWidth = TextWidth.of(widestLeft());
        return state
                + " ".repeat(Math.max(0, TextWidth.of(widestState()) - TextWidth.of(state)))
                + remaining(check, eta, learning, now)
                        .map(left -> " · " + TextWidth.alignRight(left, leftWidth))
                        .getOrElse(() -> " ".repeat(TextWidth.of(" · ") + leftWidth));
    }

    /**
     * How long {@code check}, which is not done, runs or is queued at {@code now}: "running 2m", "queued 1m 30s";
     * only "running" for a commit status, which is not timed.
     */
    private static String state(Check check, Instant now) {
        if (check.run().isEmpty()) {
            return Messages.get("checks.running");
        }
        return check.queued()
                ? queued(check, now)
                : check.run()
                        .flatMap(CheckRun::startedAt)
                        .map(started -> Messages.get("checks.runningFor", duration(Duration.between(started, now))))
                        .getOrElse(() -> Messages.get("checks.running"));
    }

    /**
     * How long {@code check}, which is not done, is expected to take still at {@code now}, with {@code eta}:
     * "~2m 5s left", "due", "overdue"; nothing for a commit status, which is not timed.
     */
    private static Option<String> remaining(Check check, Eta eta, boolean learning, Instant now) {
        if (check.run().isEmpty()) {
            return Option.none();
        }
        return switch (eta) {
            case Eta.Expected expected when expected.overdue(now) -> Option.of(Messages.get("checks.overdue"));
            case Eta.Expected expected when due(expected.left(now)) -> Option.of(Messages.get("checks.due"));
            case Eta.Expected expected -> Option.of(Messages.get("checks.left", left(expected.left(now))));
            case Eta.Unknown u -> Option.of(Messages.get("checks.left", unknown(learning)));
            case Eta.Done done -> Option.none();
        };
    }

    /**
     * How long {@code check}, which is queued, has been queued at {@code now}: since its workflow run was created,
     * "queued 3m 20s"; only "queued" for a re-run, as GitHub does not say when an attempt started (ADR 35).
     */
    private static String queued(Check check, Instant now) {
        return check.run()
                .filter(run -> run.runAttempt() == 1)
                .map(run -> Messages.get("checks.queuedFor", duration(Duration.between(run.runCreatedAt(), now))))
                .getOrElse(() -> Messages.get("checks.queued"));
    }

    private static String unknown(boolean learning) {
        return Messages.get(learning ? "checks.learning" : "checks.unknown");
    }
}
