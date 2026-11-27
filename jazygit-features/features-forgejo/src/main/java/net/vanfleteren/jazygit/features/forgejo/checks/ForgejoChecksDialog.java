package net.vanfleteren.jazygit.features.forgejo.checks;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.core.forgejo.CommitStatus;
import net.vanfleteren.jazygit.core.forgejo.ForgejoCheck;
import net.vanfleteren.jazygit.features.forge.checks.CheckPick;
import net.vanfleteren.jazygit.features.forge.checks.ChecksAction;
import net.vanfleteren.jazygit.features.forge.checks.ChecksMsg;
import net.vanfleteren.jazygit.features.forge.checks.ChecksPopup;
import net.vanfleteren.jazygit.features.forge.widgets.ChecksTexts;
import net.vanfleteren.jazygit.features.forgejo.pulls.ForgejoPullRequests;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.ui.checks.CheckDurations;
import net.vanfleteren.jazygit.kernel.ui.checks.ChecksFrame;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Scope;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.text.TextWidth;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.CheckSymbols;
import net.vanfleteren.jazygit.kernel.ui.widgets.PopupView;
import net.vanfleteren.jazygit.kernel.ui.widgets.Symbol;

/**
 * Popup listing the checks on the tip of a branch on Forgejo one by one (ADR 195, 209), in the frame GitHub's are
 * listed in: how many are in each state; when they started and ended, and how long they took together; a bar of how
 * far they are together, counting those done, as Forgejo does not tell how long they take; then each, the failed ones
 * first, then the running, the queued and the passed ones, with how long it took, or has been running or queued. It
 * scrolls when they do not all fit. While they are read again, they stay shown, with a spinner in the title when the
 * user asked for that (ADR 34); when that fails, the title says they are stale.
 */
public final class ForgejoChecksDialog implements PopupView {

    public static final String ID = "forgejo-checks";

    /**
     * The checks popup on Forgejo, whose keys act on what it shows.
     */
    public static final Scope<ChecksPopup> SCOPE = ChecksAction.scope(ID);

    /**
     * The keys of the popup, which host those of a failed build.
     */
    public static final UnaryOperator<Keymap> KEYS = ChecksAction.keys(SCOPE);

    private final Clock clock;
    private final ChecksFrame<ChecksPopup> frame;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public ForgejoChecksDialog(Consumer<Msg> dispatch) {
        this(dispatch, Clock.systemDefaultZone(), Keymap::standard);
    }

    /**
     * @param clock  animates the spinner, and its zone is the one times of day are shown in; how long checks run is told
     *               by the time of the model
     * @param keymap the keys of the popup, asked for when a key is pressed, as the app makes it of every feature
     */
    ForgejoChecksDialog(Consumer<Msg> dispatch, Clock clock, Supplier<Keymap> keymap) {
        this.clock = clock;
        this.frame = new ChecksFrame<>(ID, SCOPE, dispatch, keymap, ChecksMsg.Closed::new);
    }

    @Override
    public List<String> ids() {
        return List.of(ID);
    }

    @Override
    public Option<Element> render(Model model) {
        return ChecksTexts.shown(model, ForgejoChecksPort.PORT)
                .map(popup -> frame.render(
                        model,
                        popup,
                        ChecksTexts.title(popup, model.now(), clock),
                        popup.checks()
                                .map(read -> read.map(checks -> listing(
                                        ForgejoChecksPort.checks(checks.checks()),
                                        ForgejoChecksPort.checks(popup.ordered(model)),
                                        ChecksTexts.note(popup, model),
                                        popup.pickedCheck(model)
                                                .flatMap(picked -> ForgejoChecksPort.checks(List.of(picked))
                                                        .headOption()),
                                        model.now(),
                                        clock.getZone()))),
                        ChecksTexts.loading(ForgejoChecksPort.PORT.forge()),
                        clock.instant(),
                        ChecksTexts.hint(),
                        failedBuild(popup, model)));
    }

    /**
     * The times the dialog shows (ADR 124): every second while a check it lists is not done, as how long they run moves
     * on.
     */
    public static List<ShownTime> shownTimes(Model model) {
        return ChecksTexts.shownTimes(model, ForgejoChecksPort.PORT);
    }

    /**
     * Whether the dialog shows a spinner in {@code model}: while its checks are first read, or read again as the user
     * asked.
     */
    public static boolean showsSpinner(Model model) {
        return ChecksTexts.showsSpinner(model, ForgejoChecksPort.PORT);
    }

    /**
     * The counts, when they started and ended, how far they are, {@code note}, and the checks {@code all}, listed as
     * {@code ordered}, the one {@code picked} marked; a click on one picks it.
     */
    static ChecksFrame.Listing listing(
            List<ForgejoCheck> all,
            List<ForgejoCheck> ordered,
            Option<StyledElement<?>> note,
            Option<ForgejoCheck> picked,
            Instant now,
            ZoneId zone) {
        // As wide as the widest, and while one runs as wide as one that runs can get, so the names do not move.
        int doingWidth = ordered.map(check -> TextWidth.of(doing(check, now)))
                .appendAll(Option.when(all.exists(check -> !check.done()), () -> TextWidth.of(widestRunning())))
                .max()
                .getOrElse(0);
        return new ChecksFrame.Listing(
                CheckSymbols.counts(
                        all.count(check -> check.state() == CommitStatus.State.FAILED),
                        all.count(check -> !check.done()),
                        all.count(check -> check.state() == CommitStatus.State.PASSED)),
                span(all, now, zone),
                ChecksFrame.gauge(progress(all), gauged(all)),
                note,
                ordered.map(check -> {
                    List<StyledElement<?>> drawn = ChecksFrame.filling(
                            List.of(symbol(check)),
                            check.name(),
                            check.queued(),
                            Option.of(doing(check, now)),
                            ChecksFrame.ROW_WIDTH,
                            doingWidth);
                    boolean isPicked = picked.contains(check);
                    return new ChecksFrame.Row(
                            isPicked ? ChecksFrame.picked(1, drawn) : drawn,
                            isPicked,
                            new ChecksMsg.Clicked(CheckPick.of(ForgejoChecksPort.of(check))));
                }));
    }

    /**
     * How far the checks {@code all} are together, from 0 to 1: how many of them are done, as Forgejo does not tell
     * how long they take.
     */
    static double progress(List<ForgejoCheck> all) {
        return all.isEmpty() ? 1 : (double) all.count(ForgejoCheck::done) / all.size();
    }

    /**
     * When the checks {@code all} started, and once they are all done, ended; none while none has.
     */
    private static Option<StyledElement<?>> span(List<ForgejoCheck> all, Instant now, ZoneId zone) {
        Option<Instant> end = all.forAll(ForgejoCheck::done)
                ? all.flatMap(ForgejoCheck::ended).max()
                : Option.none();
        return all.flatMap(ForgejoCheck::started).min().map(start -> ChecksFrame.span(start, end, now, zone));
    }

    /**
     * The colour of the bar of how far the checks {@code all} are: red once one failed, green once they all passed,
     * dimmed while it counts those done, as when they are done is not known.
     */
    private static String gauged(List<ForgejoCheck> all) {
        return all.exists(check -> check.state() == CommitStatus.State.FAILED)
                ? Theme.Classes.CHECKS_FAILED
                : all.forAll(ForgejoCheck::done) ? Theme.Classes.CHECKS_PASSED : Theme.Classes.DIM;
    }

    private static Symbol symbol(ForgejoCheck check) {
        return switch (check.state()) {
            case FAILED -> CheckSymbols.FAILED;
            case PASSED -> CheckSymbols.PASSED;
            case RUNNING, NONE -> check.queued() ? CheckSymbols.QUEUED : CheckSymbols.RUNNING;
        };
    }

    /**
     * How {@code check} is doing at {@code now}: how long it took, {@code 2m}, {@code failed after 1m 30s},
     * {@code skipped}; or how long it has been running, {@code running 2m 5s}, or queued, {@code queued 40s}.
     */
    static String doing(ForgejoCheck check, Instant now) {
        if (check.skipped()) {
            return Messages.get("forgejo.checks.skipped");
        }
        Instant from = check.started().getOrElse(check.created());
        String took = CheckDurations.exact(Duration.between(from, check.ended().getOrElse(now)));
        if (check.done()) {
            return check.state() == CommitStatus.State.FAILED ? Messages.get("forgejo.checks.failedAfter", took) : took;
        }
        return check.queued()
                ? Messages.get("forgejo.checks.queuedFor", CheckDurations.exact(Duration.between(check.created(), now)))
                : Messages.get("forgejo.checks.runningFor", took);
    }

    /**
     * How a check that is not done says how it is doing at its widest, under an hour.
     */
    private static String widestRunning() {
        String widest = CheckDurations.exact(Duration.ofSeconds(59 * 60 + 59));
        String running = Messages.get("forgejo.checks.runningFor", widest);
        String queued = Messages.get("forgejo.checks.queuedFor", widest);
        return TextWidth.of(running) >= TextWidth.of(queued) ? running : queued;
    }

    /**
     * What failed of the checks {@code popup} shows in {@code model}, once they were read, for the keys other features
     * add to a failed build (ADR 161): the logs are where each status links to, as Forgejo's API reads a job's log
     * only by an id its links do not show.
     */
    static Option<FailedBuild> failedBuild(ChecksPopup popup, Model model) {
        return popup.read().map(read -> {
            List<ForgejoCheck> all = ForgejoChecksPort.checks(read.checks());
            List<ForgejoCheck> failed = all.filter(check -> check.state() == CommitStatus.State.FAILED);
            return new FailedBuild(
                    Option.of(read.repository()),
                    ChecksTexts.local(model, popup),
                    read.commit(),
                    model.part(ForgejoPullRequests.PART)
                            .open()
                            .flatMap(open -> open.get(popup.onForge()))
                            .map(pr -> new FailedBuild.ChangeRequest("pull request", pr.number())),
                    failed.map(check -> new FailedBuild.Job(check.name(), check.url())),
                    all.exists(check -> !check.done()),
                    failed.map(check -> check.name() + ": its log is on Forgejo"
                                    + check.url().map(url -> ", at " + url).getOrElse(""))
                            .mkString("\n"));
        });
    }
}
