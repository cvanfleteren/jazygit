package net.vanfleteren.jazygit.features.github.checks;

import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.core.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.core.gh.timing.CheckSpan;
import net.vanfleteren.jazygit.core.repo.Branch;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.BranchRef;
import net.vanfleteren.jazygit.core.repo.RemoteBranch;
import net.vanfleteren.jazygit.features.github.failed.FailedBuilds;
import net.vanfleteren.jazygit.features.github.widgets.CheckRows;
import net.vanfleteren.jazygit.features.github.widgets.CheckTimesText;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.state.github.CheckTimes;
import net.vanfleteren.jazygit.kernel.state.github.GitHubState;
import net.vanfleteren.jazygit.kernel.state.github.Reading;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.text.ErrorText;
import net.vanfleteren.jazygit.kernel.ui.text.ShownTime;
import net.vanfleteren.jazygit.kernel.ui.text.TextWidth;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.kernel.ui.widgets.PopupView;

/**
 * Popup listing the checks on the tip of a branch one by one (ADR 32): how many are in each state and when they are
 * expected to be done; when they started and ended, and how long they took together (ADR 37); a bar of how far they
 * are together (ADR 89); then each, the failed ones first, then the running, the queued and the passed ones, with how
 * long it took, or runs and is expected to take still; while one is not done, with a bar of how far each is after its
 * name, and how long it runs of how long it is expected to take. It scrolls when they do not all fit. While they are read
 * again, they stay shown, with a spinner in the title when the user asked for that (ADR 34); when that fails, the
 * title says they are stale.
 */
public final class ChecksDialog implements PopupView {

    public static final String ID = "checks";

    private static final int WIDTH = 100;
    // The columns inside the border, which the checks take to line up how they are doing on the right.
    private static final int CONTENT_WIDTH = DialogFrame.contentWidth(WIDTH);
    // How wide the bar of each check is, after its name, while one is not done.
    private static final int CHECK_BAR = 24;
    // The checks listed at once; the others are scrolled to.
    private static final int VISIBLE_CHECKS = ChecksPopup.VISIBLE_CHECKS;
    // What marks the check picked (ADR 160), and what stands in its place before the others.
    private static final String PICKED = "▸ ";
    private static final String UNPICKED = "  ";
    // The counts, when they started and ended, how far they are, a blank line and the checks. The dialog is always this
    // high, so it keeps its size when they arrive.
    private static final int CONTENT_HEIGHT = 1 + 1 + 1 + 1 + VISIBLE_CHECKS;
    // A time of day, of today; one of another day has its date before it.
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Consumer<Msg> dispatch;
    private final Clock clock;
    private final Supplier<Keymap> keymap;

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
        this(dispatch, clock, () -> ChecksAction.KEYS.apply(Keymap.empty()));
    }

    /**
     * @param keymap the keys of the popup, asked for when a key is pressed, as the app makes it of every feature
     */
    ChecksDialog(Consumer<Msg> dispatch, Clock clock, Supplier<Keymap> keymap) {
        this.dispatch = dispatch;
        this.clock = clock;
        this.keymap = keymap;
    }

    @Override
    public List<String> ids() {
        return List.of(ID);
    }

    @Override
    public Option<Element> render(Model model) {
        return model.popup(ChecksPopup.class).map(popup -> {
            List<Check> all = popup.checksRead();
            // How far the checks are scrolled is the line of the popup's list cursor: the first one shown (ADR 2).
            ListCursor cursor = model.listCursor();
            int last = Math.max(0, all.size() - VISIBLE_CHECKS);
            int first = Math.clamp(cursor.line().getOrElse(0), 0, last);
            Option<CheckTimes> times = popup.times(model);
            List<StyledElement<?>> lines = popup.checks()
                    .map(read -> read.fold(
                            ChecksDialog::failed,
                            checks -> checks(
                                    all,
                                    first,
                                    times,
                                    popup.progress(model),
                                    popup.noLog(model)
                                            .map(ChecksDialog::noLog)
                                            .orElse(() -> popup.rerun().map(ChecksDialog::rerun)),
                                    popup.pickedCheck(model),
                                    model.now(),
                                    clock.getZone())))
                    .getOrElse(this::loading);
            int blanks = CONTENT_HEIGHT - lines.size();
            Option<FailedBuild> failed = failedBuild(popup, model);
            List<Element> hint = hint();
            return DialogFrame.of(
                            title(popup, model.now()),
                            WIDTH,
                            lines.<Element>map(line -> line)
                                    .appendAll(List.fill(Math.max(0, blanks), () -> text("")))
                                    .append(text(""))
                                    .appendAll(hint)
                                    .toJavaArray(Element[]::new))
                    .id(ID)
                    .focusable()
                    .onKeyEvent(event -> keys(event, cursor, first, last, popup, failed))
                    // The lines, the blank line and the hint, plus the border.
                    .length(CONTENT_HEIGHT + 1 + hint.size() + 2);
        });
    }

    /**
     * The times the dialog shows (ADR 124): every second while a check it lists is not done, as their bars and how
     * long they run move on.
     */
    public static List<ShownTime> shownTimes(Model model) {
        return model.popup(ChecksPopup.class)
                .filter(popup -> popup.checksRead().exists(check -> !check.done()))
                .map(popup -> ShownTime.every(Duration.ofSeconds(1)))
                .toList();
    }

    /**
     * Whether the dialog shows a spinner in {@code model}: while its checks are first read, or read again as the user
     * asked.
     */
    public static boolean showsSpinner(Model model) {
        return model.popup(ChecksPopup.class)
                .exists(popup -> popup.checks().isEmpty() || popup.reading() == Reading.ASKED);
    }

    /**
     * The branch; then a spinner while its checks are read again as the user asked, or a mark when a read failed.
     */
    private String title(ChecksPopup popup, Instant now) {
        String title = Messages.get("github.dialog.checks.title", name(popup.branch()))
                + popup.asked()
                        .filter(asked -> asked.awaited(now))
                        .map(asked -> " · "
                                + Messages.get(
                                        "github.dialog.checks.rerunAsked", timeOfDay(asked.at(), now, clock.getZone())))
                        .getOrElse("");
        return popup.reading() == Reading.ASKED
                ? title + " " + TimeText.spinnerFrame(clock.instant())
                : popup.refreshFailed()
                        .map(failure -> title + " · " + Messages.get("github.dialog.checks.stale"))
                        .getOrElse(title);
    }

    private static String name(BranchRef branch) {
        return switch (branch) {
            case BranchRef.Local(BranchName name) -> name.value();
            case BranchRef.Remote(var remote) -> remote.shortName();
        };
    }

    private List<StyledElement<?>> loading() {
        return List.of(row(
                text(Messages.get("github.dialog.checks.loading") + " ").addClass(Theme.Classes.DIM),
                text(TimeText.spinnerFrame(clock.instant())).addClass(Theme.Classes.SPINNER)));
    }

    private static List<StyledElement<?>> failed(Failure failure) {
        return List.of(DialogFrame.wrapped(
                        Messages.get("github.dialog.checks.failed", ErrorText.of(failure)), WIDTH, CONTENT_HEIGHT)
                .addClass(Theme.Classes.ERROR));
    }

    /**
     * The counts, when they started and ended, how far they are, {@code progress}, what it says of the check picked or
     * of running failed checks again, {@code note}, and the checks that fit from the {@code first} one shown, the one
     * {@code picked} marked; a click on one picks it.
     */
    private List<StyledElement<?>> checks(
            List<Check> all,
            int first,
            Option<CheckTimes> times,
            double progress,
            Option<StyledElement<?>> note,
            Option<Check> picked,
            Instant now,
            ZoneId zone) {
        if (all.isEmpty()) {
            return List.<StyledElement<?>>of(
                            text(Messages.get("github.dialog.checks.none")).addClass(Theme.Classes.DIM))
                    .appendAll(note);
        }
        List<Check> ordered = CheckRows.order(all, all, times);
        // The bars of the checks only while one is not done; once they all are, their names get the room.
        boolean running = all.exists(check -> !check.done());
        // Over all the checks, not only those shown, so the column keeps its width while they scroll.
        int doingWidth = running
                ? CheckRows.barredDoingWidth(ordered, all, times, now)
                : CheckRows.doingWidth(ordered, all, times, now, true);
        // The mark of the check picked takes its columns from each row.
        int width = CONTENT_WIDTH - TextWidth.of(PICKED);
        return List.<StyledElement<?>>of(row(CheckRows.counts(all, times, now).toJavaArray(StyledElement[]::new)))
                .appendAll(CheckSpan.of(all).map(span -> span(span, now, zone)))
                .append(row(gauge(all, times, progress, now).toJavaArray(StyledElement[]::new)))
                // What the check picked or running failed checks again says takes the blank line above the checks.
                .append(note.getOrElse(() -> text("")))
                .appendAll(ordered.drop(first).take(VISIBLE_CHECKS).map(check -> {
                    List<StyledElement<?>> drawn = running
                            ? CheckRows.barred(check, all, times, now, width, CHECK_BAR, doingWidth)
                            : CheckRows.filling(check, all, times, now, width, doingWidth, true);
                    boolean isPicked = picked.contains(check);
                    List<StyledElement<?>> shown = isPicked ? CheckRows.picked(check, drawn) : drawn;
                    StyledElement<?> mark = isPicked ? text(PICKED).addClass(Theme.Classes.BOLD) : text(UNPICKED);
                    return row(shown.prepend(mark).toJavaArray(StyledElement[]::new))
                            .onMouseEvent(event -> clicked(event, check));
                }));
    }

    /**
     * A left click on {@code check} picks it (ADR 160).
     */
    private EventResult clicked(MouseEvent event, Check check) {
        if (event.kind() != MouseEventKind.PRESS || !event.isLeftButton()) {
            return EventResult.UNHANDLED;
        }
        dispatch.accept(new ChecksMsg.Clicked(CheckPick.of(check)));
        return EventResult.HANDLED;
    }

    /**
     * Why the check picked has no log (ADR 160).
     */
    static StyledElement<?> noLog(NoLog why) {
        return text(Messages.get(
                        switch (why) {
                            case NOT_DONE -> "github.dialog.checks.noLogNotDone";
                            case SKIPPED -> "github.dialog.checks.noLogSkipped";
                            case NOT_ACTIONS -> "github.dialog.checks.noLogNotActions";
                        }))
                .addClass(Theme.Classes.DIM);
    }

    /**
     * A bar of how far the checks {@code all} are together, {@code progress}, across the dialog, and how far that is:
     * {@code ━━━━━━━━━━━──────── 62%}. Red once one failed, green once they all passed, yellow while they take longer
     * than usual, and dimmed while when they are done is not known, as it then counts the checks done.
     */
    static List<StyledElement<?>> gauge(List<Check> all, Option<CheckTimes> times, double progress, Instant now) {
        return CheckRows.gauge(all, times, progress, now, CONTENT_WIDTH);
    }

    /**
     * When the checks started, and when they ended and how long they took, or how long they run at {@code now}:
     * {@code started 14:02:10 · ended 14:06:41 · took 4m 31s}, {@code started 14:02:10 · running 4m 31s}.
     */
    private static StyledElement<?> span(CheckSpan span, Instant now, ZoneId zone) {
        String took = CheckTimesText.duration(span.took(now));
        List<StyledElement<?>> ended = span.end()
                .map(end -> List.<StyledElement<?>>of(
                        label("github.dialog.checks.ended", " · "),
                        text(timeOfDay(end, now, zone)),
                        label("github.dialog.checks.took", " · "),
                        text(took)))
                .getOrElse(() -> List.of(label("github.checks.running", " · "), text(took)));
        return row(List.<StyledElement<?>>of(
                        label("github.dialog.checks.started", ""), text(timeOfDay(span.start(), now, zone)))
                .appendAll(ended)
                .toJavaArray(StyledElement[]::new));
    }

    /**
     * What running failed checks again says (ADR 135): why nothing is, that GitHub is asked to, or how that went,
     * {@code rerun 2 of 3 · E2E: <what GitHub said>}, as an error when it refused one.
     */
    static StyledElement<?> rerun(RerunNote note) {
        return switch (note) {
            case RerunNote.Nothing(RerunPlan.Why why) -> text(nothing(why)).addClass(Theme.Classes.DIM);
            case RerunNote.Asked asked ->
                asked.outcomes()
                        .map(outcomes -> {
                            List<RerunCmd.Outcome> refused = asked.refused();
                            String text = List.of(Messages.get(
                                            "github.dialog.checks.rerunDone",
                                            outcomes.size() - refused.size(),
                                            outcomes.size()))
                                    .appendAll(
                                            refused.map(outcome -> outcome.run().workflow() + ": "
                                                    + outcome.refused()
                                                            .map(ErrorText::of)
                                                            .getOrElse("")))
                                    .mkString(" · ");
                            return text(text).addClass(refused.isEmpty() ? Theme.Classes.DIM : Theme.Classes.ERROR);
                        })
                        .getOrElse(() -> text(Messages.get(
                                        "github.dialog.checks.rerunAsking",
                                        asked.runIds().size()))
                                .addClass(Theme.Classes.DIM));
        };
    }

    private static String nothing(RerunPlan.Why why) {
        return Messages.get(
                switch (why) {
                    case NONE_FAILED -> "github.dialog.checks.rerunNoneFailed";
                    case RUNNING -> "github.dialog.checks.rerunRunning";
                    case NOT_ACTIONS -> "github.dialog.checks.rerunNotActions";
                });
    }

    /**
     * The label {@code key}, dimmed, after {@code before} and with a space after it.
     */
    private static StyledElement<?> label(String key, String before) {
        return text(before + Messages.get(key) + " ").addClass(Theme.Classes.DIM);
    }

    /**
     * {@code at} as a time of day in {@code zone}, with its date before it when it is not the day of {@code now}.
     */
    private static String timeOfDay(Instant at, Instant now, ZoneId zone) {
        ZonedDateTime local = at.atZone(zone);
        return local.toLocalDate().equals(now.atZone(zone).toLocalDate())
                ? TIME.format(local)
                : DATE_TIME.format(local);
    }

    /**
     * @param first the check shown first, as drawn
     * @param last  the furthest the checks scroll to: the first one shown when the last one is at the bottom
     */
    private EventResult keys(
            KeyEvent event, ListCursor cursor, int first, int last, ChecksPopup popup, Option<FailedBuild> failed) {
        if (event.isCancel() || event.isConfirm()) {
            dispatch.accept(new ChecksMsg.Closed());
        } else if (event.isChar('j') || event.code() == KeyCode.DOWN) {
            scroll(cursor, first + 1, last);
        } else if (event.isChar('k') || event.code() == KeyCode.UP) {
            scroll(cursor, first - 1, last);
        } else if (event.code() == KeyCode.PAGE_DOWN) {
            scroll(cursor, first + VISIBLE_CHECKS, last);
        } else if (event.code() == KeyCode.PAGE_UP) {
            scroll(cursor, first - VISIBLE_CHECKS, last);
        } else if (event.isChar('?')) {
            // The help of a popup is not shown over it.
            return EventResult.UNHANDLED;
        } else {
            // Its keys, and those other features add to the failed build it shows (ADR 161).
            Option<Msg> msg = keymap.get()
                    .handle(
                            ChecksAction.SCOPE,
                            event,
                            Option.of(popup),
                            new Keymap.Hosted<>(Scopes.FAILED_BUILD, failed));
            msg.forEach(dispatch);
            return msg.isDefined() ? EventResult.HANDLED : EventResult.UNHANDLED;
        }
        return EventResult.HANDLED;
    }

    /**
     * What failed of the checks {@code popup} shows in {@code model}, once they were read, for the keys other features
     * add to a failed build (ADR 161).
     */
    static Option<FailedBuild> failedBuild(ChecksPopup popup, Model model) {
        BranchName branch = switch (popup.branch()) {
            case BranchRef.Local(BranchName name) -> name;
            case BranchRef.Remote(var remote) -> follower(model, remote);
        };
        return popup.checks()
                .flatMap(read -> read.toOption())
                .map(read -> FailedBuilds.of(
                        branch,
                        read.repository(),
                        popup.checksRead(),
                        model.part(GitHubState.PART).pullRequest(popup.onGitHub())));
    }

    /**
     * The local branch whose build the checks of {@code remote} are: of the local branches that follow it, the one named
     * as it is, else the only one. Else its name on the remote, which no worktree has checked out, so the agent is not
     * started on a guess: several follow it once branches are made from it, as {@code git switch -c feature
     * origin/main}, until they are pushed; and a local branch of the same name may follow another remote.
     */
    private static BranchName follower(Model model, RemoteBranch remote) {
        List<Branch> followers = model.repo().followers(remote);
        return followers
                .find(branch -> branch.name().equals(remote.branch()))
                .orElse(() -> Option.when(followers.size() == 1, followers::head))
                .map(Branch::name)
                .getOrElse(() -> new BranchName(remote.shortName()));
    }

    /**
     * The hint, and on a line of its own, as the first is full, the keys other features add to the failed build it
     * shows, if any (ADR 161).
     */
    private List<Element> hint() {
        List<String> added = keymap.get()
                .bindings(Scopes.FAILED_BUILD.id())
                .map(binding -> "(" + binding.label() + ") " + Messages.get(binding.descriptionKey()));
        return List.of(Messages.get("github.dialog.checks.hint"))
                .appendAll(Option.when(added.nonEmpty(), () -> added.mkString("   ")))
                .map(line -> text(line).addClass(Theme.Classes.DIM));
    }

    private void scroll(ListCursor cursor, int first, int last) {
        dispatch.accept(new PopupMsg.ListCursorChanged(cursor.at(Math.clamp(first, 0, last))));
    }
}
