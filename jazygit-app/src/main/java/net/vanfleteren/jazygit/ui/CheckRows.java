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
import net.vanfleteren.jazygit.gh.timing.CheckHistory;
import net.vanfleteren.jazygit.gh.timing.Eta;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.CheckTimes;

/**
 * What the dialogs that list checks one by one have in common (ADR 32): how many are in each state, in which order
 * they are listed, and how each is doing, with when it is expected to be done as far as was learned.
 */
public final class CheckRows {

    // A longer name of a check is cut, to leave room for how long it runs.
    private static final int NAME_WIDTH = 30;
    // The order of the checks by how they are doing: failed, running, queued, passed.
    private static final int FAILED = 0;
    private static final int RUNNING = 1;
    private static final int QUEUED = 2;
    private static final int PASSED = 3;

    private CheckRows() {}

    /**
     * How many of the checks {@code all} of a commit failed, are running and passed, and when they are expected to be
     * done together, as far as {@code times} tells: {@code ❌ 1 failed · ⏳ 2 running · done in ~3m}.
     */
    public static List<StyledElement<?>> counts(List<Check> all, Option<CheckTimes> times, Instant now) {
        return List.of(
                        count(all, Checks.FAILING, "dialog.pullRequest.checks.failed"),
                        count(all, Checks.PENDING, "dialog.pullRequest.checks.running"),
                        count(all, Checks.PASSING, "dialog.pullRequest.checks.passed"),
                        CheckTimesText.together(eta(all, times), learning(times), now)
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
     * How wide the names of {@code listed} are, to line up what follows them: as wide as the widest, up to a limit.
     */
    public static int nameWidth(List<Check> listed) {
        return Math.min(
                NAME_WIDTH,
                listed.map(check -> TextWidth.of(check.name())).max().getOrElse(0));
    }

    /**
     * {@code check}, one of the checks {@code all} of a commit: its symbol and its name, and for one that is not done
     * how it is doing, dimmed: {@code ⏳ acceptance  running 2m · ~2m left}; a queued one is dimmed all along, as its
     * emoji ignores dimming. One that is done shows how long it took when {@code took}: {@code ✅ verify  2m},
     * {@code ❌ lint  failed after 1m}.
     */
    public static List<StyledElement<?>> row(
            Check check, List<Check> all, Option<CheckTimes> times, Instant now, int nameWidth, boolean took) {
        List<StyledElement<?>> symbol = PullRequestSymbols.check(check).toList().map(CheckRows::symbol);
        Option<String> doing = check.done()
                ? Option.when(took, () -> took(check)).flatMap(o -> o)
                : Option.of(CheckTimesText.of(check, eta(check, all, times), learning(times), now));
        TextElement name = text(" " + padded(check.name(), nameWidth) + "  ");
        return symbol.appendAll(doing.map(text -> List.<StyledElement<?>>of(
                        check.queued() ? name.addClass(Theme.Classes.DIM) : name,
                        text(text).addClass(Theme.Classes.DIM)))
                .getOrElse(() -> List.of(text(" " + check.name()))));
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
     * When {@code check}, one of the checks {@code all} of a commit, is expected to be done, as far as was learned.
     */
    public static Eta eta(Check check, List<Check> all, Option<CheckTimes> times) {
        return times.map(known -> known.eta(check, all)).getOrElse(() -> Eta.of(check, all, CheckHistory.EMPTY));
    }

    /**
     * When the checks {@code all} of a commit are expected to be done together, as far as was learned.
     */
    public static Eta eta(List<Check> all, Option<CheckTimes> times) {
        return times.map(known -> known.eta(all)).getOrElse(() -> Eta.of(all, CheckHistory.EMPTY));
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
