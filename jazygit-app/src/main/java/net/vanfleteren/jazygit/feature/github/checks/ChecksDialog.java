package net.vanfleteren.jazygit.feature.github.checks;

import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Clock;
import java.time.Instant;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.gh.model.PullRequestDetails.Check;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.BranchRef;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.CheckTimes;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.ListCursor;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.CheckRows;
import net.vanfleteren.jazygit.ui.ErrorText;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.TimeText;
import net.vanfleteren.jazygit.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * Popup listing the checks on the tip of a branch one by one (ADR 32): how many are in each state and when they are
 * expected to be done; then each, the failed ones first, then the running, the queued and the passed ones, with how
 * long it took, or runs and is expected to take still. It scrolls when they do not all fit. While they are read
 * again, they stay shown with a spinner in the title; when that fails, the title says they are stale.
 */
public final class ChecksDialog implements PopupView {

    public static final String ID = "checks";

    private static final int WIDTH = 80;
    // The columns inside the border, which the checks take to line up how they are doing on the right.
    private static final int CONTENT_WIDTH = DialogFrame.contentWidth(WIDTH);
    // The checks listed at once; the others are scrolled to.
    private static final int VISIBLE_CHECKS = 16;
    // The counts, a blank line and the checks. The dialog is always this high, so it keeps its size when they arrive.
    private static final int CONTENT_HEIGHT = 1 + 1 + VISIBLE_CHECKS;

    private final Consumer<Msg> dispatch;
    private final Clock clock;

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public ChecksDialog(Consumer<Msg> dispatch) {
        this(dispatch, Clock.systemDefaultZone());
    }

    /**
     * @param clock animates the spinner; how long checks run is told by the time of the model
     */
    ChecksDialog(Consumer<Msg> dispatch, Clock clock) {
        this.dispatch = dispatch;
        this.clock = clock;
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
            Option<CheckTimes> times = popup.checks()
                    .flatMap(read -> read.toOption())
                    .flatMap(read -> model.gitHub()
                            .checkTimes()
                            .filter(known -> known.repository().equals(read.repository())));
            List<StyledElement<?>> lines = popup.checks()
                    .map(read -> read.fold(ChecksDialog::failed, checks -> checks(all, first, times, model.now())))
                    .getOrElse(this::loading);
            int blanks = CONTENT_HEIGHT - lines.size();
            return DialogFrame.withHint(
                            title(popup),
                            WIDTH,
                            "dialog.checks.hint",
                            lines.appendAll(List.fill(Math.max(0, blanks), () -> text(""))))
                    .id(ID)
                    .focusable()
                    .onKeyEvent(event -> keys(event, cursor, first, last))
                    // The lines, the blank line and the hint, plus the border.
                    .length(CONTENT_HEIGHT + 2 + 2);
        });
    }

    /**
     * Whether the dialog shows a spinner in {@code model}: while its checks are first read, or read again.
     */
    public static boolean showsSpinner(Model model) {
        return model.popup(ChecksPopup.class).exists(popup -> popup.checks().isEmpty() || popup.refreshing());
    }

    /**
     * The branch; then a spinner while its checks are read again, or a mark when that failed.
     */
    private String title(ChecksPopup popup) {
        String title = Messages.get("dialog.checks.title", name(popup.branch()));
        return popup.refreshing()
                ? title + " " + TimeText.spinnerFrame(clock.instant())
                : popup.refreshFailed()
                        .map(failure -> title + " · " + Messages.get("dialog.checks.stale"))
                        .getOrElse(title);
    }

    private static String name(BranchRef branch) {
        return switch (branch) {
            case BranchRef.Local(BranchName name) -> name.value();
            case BranchRef.Remote(var remote) ->
                remote.remote() + "/" + remote.branch().value();
        };
    }

    private List<StyledElement<?>> loading() {
        return List.of(row(
                text(Messages.get("dialog.checks.loading") + " ").addClass(Theme.Classes.DIM),
                text(TimeText.spinnerFrame(clock.instant())).addClass(Theme.Classes.SPINNER)));
    }

    private static List<StyledElement<?>> failed(Failure failure) {
        return List.of(
                DialogFrame.wrapped(Messages.get("dialog.checks.failed", ErrorText.of(failure)), WIDTH, CONTENT_HEIGHT)
                        .addClass(Theme.Classes.ERROR));
    }

    /**
     * The counts, and the checks that fit from the {@code first} one shown.
     */
    private static List<StyledElement<?>> checks(List<Check> all, int first, Option<CheckTimes> times, Instant now) {
        if (all.isEmpty()) {
            return List.of(text(Messages.get("dialog.checks.none")).addClass(Theme.Classes.DIM));
        }
        List<Check> ordered = CheckRows.order(all, all, times);
        // Over all the checks, not only those shown, so the column keeps its width while they scroll.
        int doingWidth = CheckRows.doingWidth(ordered, all, times, now);
        return List.<StyledElement<?>>of(row(CheckRows.counts(all, times, now).toJavaArray(StyledElement[]::new)))
                .append(text(""))
                .appendAll(ordered.drop(first)
                        .take(VISIBLE_CHECKS)
                        .map(check -> row(CheckRows.filling(check, all, times, now, CONTENT_WIDTH, doingWidth)
                                .toJavaArray(StyledElement[]::new))));
    }

    /**
     * @param first the check shown first, as drawn
     * @param last  the furthest the checks scroll to: the first one shown when the last one is at the bottom
     */
    private EventResult keys(KeyEvent event, ListCursor cursor, int first, int last) {
        if (event.code() == KeyCode.ESCAPE || event.code() == KeyCode.ENTER) {
            dispatch.accept(new ChecksMsg.Closed());
        } else if (event.isChar('R')) {
            dispatch.accept(new ChecksMsg.RefreshChosen());
        } else if (event.isChar('j') || event.code() == KeyCode.DOWN) {
            scroll(cursor, first + 1, last);
        } else if (event.isChar('k') || event.code() == KeyCode.UP) {
            scroll(cursor, first - 1, last);
        } else if (event.code() == KeyCode.PAGE_DOWN) {
            scroll(cursor, first + VISIBLE_CHECKS, last);
        } else if (event.code() == KeyCode.PAGE_UP) {
            scroll(cursor, first - VISIBLE_CHECKS, last);
        } else {
            return EventResult.UNHANDLED;
        }
        return EventResult.HANDLED;
    }

    private void scroll(ListCursor cursor, int first, int last) {
        dispatch.accept(new Msg.ListCursorChanged(cursor.at(Math.clamp(first, 0, last))));
    }
}
