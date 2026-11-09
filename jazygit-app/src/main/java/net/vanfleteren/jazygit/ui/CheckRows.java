package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.TextElement;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.gh.model.PullRequest.Checks;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.gh.timing.CheckProgress;
import net.vanfleteren.jazygit.gh.timing.CheckSpan;
import net.vanfleteren.jazygit.gh.timing.Eta;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.CheckTimes;

/**
 * What the dialogs that list checks one by one have in common (ADR 32): how many are in each state, in which order
 * they are listed, and how each is doing, with when it is expected to be done as far as was learned.
 */
public final class CheckRows {

    // The order of the checks by how they are doing: failed, running, queued, passed.
    private static final int FAILED = 0;
    private static final int RUNNING = 1;
    private static final int QUEUED = 2;
    private static final int PASSED = 3;
    // How wide how far the checks are together is written, after their bar and a space: "100%".
    private static final int PERCENT = 4;

    private CheckRows() {}

    /**
     * How many of the checks {@code all} of a commit failed, are running and passed, and when they are expected to be
     * done together, as far as {@code times} tells: {@code ✗ 1 failed · ● 2 running · done in ~3m}.
     */
    public static List<StyledElement<?>> counts(List<Check> all, Option<CheckTimes> times, Instant now) {
        return List.of(
                        count(all, Checks.FAILING, "dialog.pullRequest.checks.failed"),
                        count(all, Checks.PENDING, "dialog.pullRequest.checks.running"),
                        count(all, Checks.PASSING, "dialog.pullRequest.checks.passed"),
                        CheckTimesText.together(eta(all, times), CheckSpan.current(all), learning(times), now)
                                .map(together -> List.<StyledElement<?>>of(text(together))))
                .flatMap(o -> o)
                .zipWithIndex()
                .flatMap(indexed -> indexed._2 > 0 ? indexed._1.prepend(text(" · ")) : indexed._1);
    }

    /**
     * The symbol of {@code state} and how many of {@code checks} are in it; nothing when none are.
     */
    private static Option<List<StyledElement<?>>> count(List<Check> checks, Checks state, String key) {
        int count = checks.count(check -> check.state() == state);
        return Option.when(
                count > 0,
                () -> PullRequestSymbols.checks(state)
                        .toList()
                        .<StyledElement<?>>map(CheckRows::symbol)
                        .append(text(" " + Messages.get(key, String.valueOf(count)))));
    }

    /**
     * {@code checks}, some of the checks {@code all} of a commit, the failed ones first, then the running ones, the
     * queued ones and the passed ones; those not done the soonest done first, and those not known last.
     */
    public static List<Check> order(List<Check> checks, List<Check> all, Option<CheckTimes> times) {
        return checks.sortBy(check -> eta(check, all, times) instanceof Eta.Expected(Instant at) ? at : Instant.MAX)
                .sortBy(CheckRows::rank);
    }

    private static int rank(Check check) {
        return check.state() == Checks.FAILING
                ? FAILED
                : check.state() == Checks.PASSING ? PASSED : check.queued() ? QUEUED : RUNNING;
    }

    /**
     * {@code check}, one of the checks {@code all} of a commit, taking {@code width} columns: its symbol and its name
     * on the left, cut to leave room, and how it is doing or how long it took on the right, in a column
     * {@code doingWidth} wide, lined up on the right: {@code ● acceptance  running 2m 30s · ~1m 5s left}, dimmed; a
     * queued one is dimmed all along. One that is done shows how long it took when
     * {@code took}: {@code ✓ verify          2m}, {@code ✗ lint  failed after 1m 30s}.
     */
    public static List<StyledElement<?>> filling(
            Check check,
            List<Check> all,
            Option<CheckTimes> times,
            Instant now,
            int width,
            int doingWidth,
            boolean took) {
        List<PullRequestSymbols.Symbol> symbols =
                PullRequestSymbols.check(check).toList();
        int left =
                width - symbols.map(symbol -> TextWidth.of(symbol.text())).sum().intValue() - 1;
        return symbols.<StyledElement<?>>map(CheckRows::symbol)
                .appendAll(doing(check, all, times, now, took)
                        .map(text -> {
                            TextElement name = text(" " + padded(check.name(), left - 2 - doingWidth) + "  ");
                            return List.<StyledElement<?>>of(
                                    check.queued() ? name.addClass(Theme.Classes.DIM) : name,
                                    text(TextWidth.alignRight(text, doingWidth)).addClass(Theme.Classes.DIM));
                        })
                        .getOrElse(() -> List.of(text(" " + TextWidth.cut(check.name(), left)))));
    }

    /**
     * How wide the column {@link #filling} shows how {@code listed}, some of the checks {@code all} of a commit, are
     * doing in is: as wide as the widest, and while one runs as wide as one that runs can get, so the names do not move
     * as the seconds do (ADR 34). How long those done took counts when {@code took}.
     */
    public static int doingWidth(
            List<Check> listed, List<Check> all, Option<CheckTimes> times, Instant now, boolean took) {
        return listed.flatMap(check -> doing(check, all, times, now, took))
                .map(TextWidth::of)
                .appendAll(Option.when(
                        listed.exists(check -> !check.done()), () -> TextWidth.of(CheckTimesText.widestRunning())))
                .max()
                .getOrElse(0);
    }

    /**
     * How {@code check}, one of the checks {@code all} of a commit, is doing when it is not done; when it is, how long
     * it took when {@code took}.
     */
    private static Option<String> doing(
            Check check, List<Check> all, Option<CheckTimes> times, Instant now, boolean took) {
        return check.done()
                ? Option.when(took, () -> took(check)).flatMap(o -> o)
                : Option.of(CheckTimesText.lined(check, eta(check, all, times), learning(times), now));
    }

    /**
     * {@code check}, one of the checks {@code all} of a commit, taking {@code width} columns, with a bar of how far it
     * is after its name (ADR 89): its symbol and its name on the left, cut to leave room, then the bar,
     * {@code barWidth} wide, and how long it runs of how long it is expected to take, or how long it took, on the right,
     * in a column {@code doingWidth} wide, lined up on the right:
     * {@code ● acceptance  ━━━━━━────────  1m 35s/~3m 40s}. A check that is done has no bar; a queued one has an empty
     * one, and is dimmed all along.
     */
    public static List<StyledElement<?>> barred(
            Check check,
            List<Check> all,
            Option<CheckTimes> times,
            Instant now,
            int width,
            int barWidth,
            int doingWidth) {
        List<PullRequestSymbols.Symbol> symbols =
                PullRequestSymbols.check(check).toList();
        int name = width
                - symbols.map(symbol -> TextWidth.of(symbol.text())).sum().intValue()
                - 1
                - 2
                - barWidth
                - 2
                - doingWidth;
        TextElement named = text(" " + padded(check.name(), name) + "  ");
        String doing = barredDoing(check, all, times, now).getOrElse("");
        return symbols.<StyledElement<?>>map(CheckRows::symbol)
                .append(check.queued() ? named.addClass(Theme.Classes.DIM) : named)
                .appendAll(check.done() ? List.of(text(" ".repeat(barWidth))) : bar(check, all, times, now, barWidth))
                .append(text("  " + TextWidth.alignRight(doing, doingWidth)).addClass(Theme.Classes.DIM));
    }

    /**
     * How wide the column {@link #barred} shows how {@code listed}, some of the checks {@code all} of a commit, are
     * doing in is: as wide as the widest, and while one is not done as wide as one that runs can get, so the names do
     * not move as the seconds do.
     */
    public static int barredDoingWidth(List<Check> listed, List<Check> all, Option<CheckTimes> times, Instant now) {
        return listed.flatMap(check -> barredDoing(check, all, times, now))
                .map(TextWidth::of)
                .appendAll(Option.when(
                        listed.exists(check -> !check.done()), () -> TextWidth.of(CheckTimesText.widestOfTotal())))
                .max()
                .getOrElse(0);
    }

    /**
     * How long {@code check}, one of the checks {@code all} of a commit, runs of how long it is expected to take, or
     * how long it took.
     */
    private static Option<String> barredDoing(Check check, List<Check> all, Option<CheckTimes> times, Instant now) {
        return check.done()
                ? took(check)
                : Option.of(CheckTimesText.ofTotal(check, eta(check, all, times), learning(times), now));
    }

    /**
     * A bar {@code width} wide of how far {@code check}, one of the checks {@code all} that is not done, is at
     * {@code now}: yellow once it is overdue, dashed while how far it is is not known.
     */
    private static List<StyledElement<?>> bar(
            Check check, List<Check> all, Option<CheckTimes> times, Instant now, int width) {
        Eta eta = eta(check, all, times);
        String done = eta instanceof Eta.Expected expected && expected.overdue(now)
                ? Theme.Classes.CHECKS_RUNNING
                : Theme.Classes.CHECKS_PROGRESS;
        return CheckProgress.of(check, eta, now)
                .map(progress -> ProgressBar.of(progress, width, done))
                .getOrElse(() -> List.of(ProgressBar.unknown(width)));
    }

    /**
     * How long {@code check}, which is done, took: {@code 2m}, or {@code failed after 1m}; nothing when it is not
     * timed.
     */
    private static Option<String> took(Check check) {
        Option<String> duration = check.run()
                .flatMap(run -> run.startedAt()
                        .flatMap(started -> run.completedAt()
                                .map(completed -> CheckTimesText.duration(Duration.between(started, completed)))));
        return check.state() == Checks.FAILING
                ? Option.of(duration.map(took -> Messages.get("checks.failedAfter", took))
                        .getOrElse(() -> Messages.get("checks.failed")))
                : duration;
    }

    /**
     * A bar of how far the checks {@code all} are together, {@code progress}, and how far that is, {@code width} wide
     * in all: {@code ━━━━━━━━━━━──────── 62%}. Red once one failed, green once they all passed, yellow while they take
     * longer than usual, and dimmed while when they are done is not known, as it then counts the checks done (ADR 89).
     */
    public static List<StyledElement<?>> gauge(
            List<Check> all, Option<CheckTimes> times, double progress, Instant now, int width) {
        Eta eta = eta(all, times);
        String done = all.exists(check -> check.state() == Checks.FAILING)
                ? Theme.Classes.CHECKS_FAILED
                : switch (eta) {
                    case Eta.Done passed -> Theme.Classes.CHECKS_PASSED;
                    case Eta.Unknown unknown -> Theme.Classes.DIM;
                    case Eta.Expected expected when expected.overdue(now) -> Theme.Classes.CHECKS_RUNNING;
                    case Eta.Expected expected -> Theme.Classes.CHECKS_PROGRESS;
                };
        String percent = Messages.get("dialog.checks.progress", String.valueOf(ProgressBar.percent(progress)));
        return ProgressBar.of(progress, width - 1 - PERCENT, done)
                .append(text(" " + TextWidth.alignRight(percent, PERCENT)));
    }

    /**
     * When {@code check}, one of the checks {@code all} of a commit, is expected to be done, as far as was learned.
     */
    public static Eta eta(Check check, List<Check> all, Option<CheckTimes> times) {
        return CheckTimes.eta(times, check, all);
    }

    /**
     * When the checks {@code all} of a commit are expected to be done together, as far as was learned.
     */
    public static Eta eta(List<Check> all, Option<CheckTimes> times) {
        return CheckTimes.eta(times, all);
    }

    private static boolean learning(Option<CheckTimes> times) {
        return times.exists(CheckTimes::toppingUp);
    }

    /**
     * {@code name}, cut to {@code width} columns, or with spaces after it to take them, so what follows lines up.
     */
    private static String padded(String name, int width) {
        String cut = TextWidth.cut(name, width);
        return cut + " ".repeat(Math.max(0, width - TextWidth.of(cut)));
    }

    private static StyledElement<?> symbol(PullRequestSymbols.Symbol symbol) {
        return text(symbol.text()).addClass(symbol.cssClass());
    }
}
