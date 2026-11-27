package net.vanfleteren.jazygit.features.github.checks;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.core.gh.timing.CheckSpan;
import net.vanfleteren.jazygit.features.forge.checks.CheckPick;
import net.vanfleteren.jazygit.features.forge.checks.ChecksAction;
import net.vanfleteren.jazygit.features.forge.checks.ChecksMsg;
import net.vanfleteren.jazygit.features.forge.checks.ChecksPopup;
import net.vanfleteren.jazygit.features.forge.widgets.ChecksTexts;
import net.vanfleteren.jazygit.features.github.failed.FailedBuilds;
import net.vanfleteren.jazygit.features.github.state.CheckTimes;
import net.vanfleteren.jazygit.features.github.state.GitHubState;
import net.vanfleteren.jazygit.features.github.widgets.CheckRows;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.ui.checks.ChecksFrame;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Scope;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.widgets.PopupView;

/**
 * Popup listing the checks on the tip of a branch on GitHub one by one (ADR 32), in the frame every forge lists them in
 * (ADR 195, 209): how many are in each state and when they are expected to be done; when they started and ended, and
 * how long they took together (ADR 32); a bar of how far they are together (ADR 89); then each, the failed ones first,
 * then the running, the queued and the passed ones, with how long it took, or runs and is expected to take still;
 * while one is not done, with a bar of how far each is after its name, and how long it runs of how long it is expected
 * to take. It scrolls when they do not all fit. While they are read again, they stay shown, with a spinner in the title
 * when the user asked for that (ADR 34); when that fails, the title says they are stale.
 */
public final class ChecksDialog implements PopupView {

    public static final String ID = "checks";

    /**
     * The checks popup of GitHub, whose keys act on what it shows.
     */
    public static final Scope<ChecksPopup> SCOPE = ChecksAction.scope(ID);

    /**
     * The keys of the popup, which host those of a failed build.
     */
    public static final UnaryOperator<Keymap> KEYS = ChecksAction.keys(SCOPE);

    // How wide the bar of each check is, after its name, while one is not done.
    private static final int CHECK_BAR = 24;

    private final Clock clock;
    private final ChecksFrame<ChecksPopup> frame;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public ChecksDialog(Consumer<Msg> dispatch) {
        this(dispatch, Clock.systemDefaultZone(), Keymap::standard);
    }

    /**
     * The popup with only its own keys, as without the app.
     *
     * @param clock animates the spinner, and its zone is the one times of day are shown in; how long checks run is
     *              told by the time of the model
     */
    ChecksDialog(Consumer<Msg> dispatch, Clock clock) {
        this(dispatch, clock, () -> KEYS.apply(Keymap.empty()));
    }

    /**
     * @param keymap the keys of the popup, asked for when a key is pressed, as the app makes it of every feature
     */
    ChecksDialog(Consumer<Msg> dispatch, Clock clock, Supplier<Keymap> keymap) {
        this.clock = clock;
        this.frame = new ChecksFrame<>(ID, SCOPE, dispatch, keymap, ChecksMsg.Closed::new);
    }

    @Override
    public List<String> ids() {
        return List.of(ID);
    }

    @Override
    public Option<Element> render(Model model) {
        return ChecksTexts.shown(model, GitHubChecksPort.PORT).map(popup -> {
            Option<CheckTimes> times = GitHubChecksPort.times(model, popup);
            return frame.render(
                    model,
                    popup,
                    ChecksTexts.title(popup, model.now(), clock),
                    popup.checks()
                            .map(read -> read.map(checks -> listing(
                                    GitHubChecksPort.checks(popup.checksRead()),
                                    times,
                                    GitHubChecksPort.progress(model, popup),
                                    ChecksTexts.note(popup, model),
                                    popup.pickedCheck(model)
                                            .flatMap(picked -> GitHubChecksPort.checks(List.of(picked))
                                                    .headOption()),
                                    model.now(),
                                    clock.getZone()))),
                    ChecksTexts.loading(GitHubChecksPort.PORT.forge()),
                    clock.instant(),
                    ChecksTexts.hint(),
                    failedBuild(popup, model));
        });
    }

    /**
     * The times the dialog shows (ADR 124): every second while a check it lists is not done, as their bars and how
     * long they run move on.
     */
    public static List<ShownTime> shownTimes(Model model) {
        return ChecksTexts.shownTimes(model, GitHubChecksPort.PORT);
    }

    /**
     * Whether the dialog shows a spinner in {@code model}: while its checks are first read, or read again as the user
     * asked.
     */
    public static boolean showsSpinner(Model model) {
        return ChecksTexts.showsSpinner(model, GitHubChecksPort.PORT);
    }

    /**
     * The counts, when they started and ended, how far they are, {@code progress}, what it says of the check picked or
     * of running failed checks again, {@code note}, and the checks, the one {@code picked} marked; a click on one picks
     * it. While one is not done, each has a bar of how far it is after its name.
     */
    private static ChecksFrame.Listing listing(
            List<Check> all,
            Option<CheckTimes> times,
            double progress,
            Option<StyledElement<?>> note,
            Option<Check> picked,
            Instant now,
            ZoneId zone) {
        List<Check> ordered = CheckRows.order(all, all, times);
        // The bars of the checks only while one is not done; once they all are, their names get the room.
        boolean running = all.exists(check -> !check.done());
        // Over all the checks, not only those shown, so the column keeps its width while they scroll.
        int doingWidth = running
                ? CheckRows.barredDoingWidth(ordered, all, times, now)
                : CheckRows.doingWidth(ordered, all, times, now, true);
        int width = ChecksFrame.ROW_WIDTH;
        return new ChecksFrame.Listing(
                CheckRows.counts(all, times, now),
                CheckSpan.of(all).map(span -> ChecksFrame.span(span.start(), span.end(), now, zone)),
                gauge(all, times, progress, now),
                note,
                ordered.map(check -> {
                    List<StyledElement<?>> drawn = running
                            ? CheckRows.barred(check, all, times, now, width, CHECK_BAR, doingWidth)
                            : CheckRows.filling(check, all, times, now, width, doingWidth, true);
                    boolean isPicked = picked.contains(check);
                    return new ChecksFrame.Row(
                            isPicked ? CheckRows.picked(check, drawn) : drawn,
                            isPicked,
                            new ChecksMsg.Clicked(CheckPick.of(GitHubChecksPort.of(check))));
                }));
    }

    /**
     * A bar of how far the checks {@code all} are together, {@code progress}, across the dialog, and how far that is:
     * {@code ━━━━━━━━━━━──────── 62%}. Red once one failed, green once they all passed, yellow while they take longer
     * than usual, and dimmed while when they are done is not known, as it then counts the checks done.
     */
    static List<StyledElement<?>> gauge(List<Check> all, Option<CheckTimes> times, double progress, Instant now) {
        return CheckRows.gauge(all, times, progress, now, ChecksFrame.CONTENT_WIDTH);
    }

    /**
     * What failed of the checks {@code popup} shows in {@code model}, once they were read, for the keys other features
     * add to a failed build (ADR 161).
     */
    static Option<FailedBuild> failedBuild(ChecksPopup popup, Model model) {
        return popup.read()
                .map(read -> FailedBuilds.of(
                        ChecksTexts.local(model, popup),
                        read.repository(),
                        GitHubChecksPort.checks(read.checks()),
                        model.part(GitHubState.PART).pullRequest(popup.onForge())));
    }
}
