package net.vanfleteren.jazygit.feature.github.checks;

import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Either;
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
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.PendingChecks;
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
    // The checks listed at once; the others are scrolled to.
    private static final int VISIBLE_CHECKS = 16;
    // The counts, a blank line and the checks. The dialog is always this high, so it keeps its size when they arrive.
    private static final int CONTENT_HEIGHT = 1 + 1 + VISIBLE_CHECKS;

    private final Consumer<Msg> dispatch;
    private final Clock clock;
    // How far the checks are scrolled: the first one shown. Where a list is scrolled is the view's (ADR 2).
    private int scrolled;
    // The branch whose checks were scrolled; another one starts at the top.
    private Option<BranchRef> scrolledOf = Option.none();

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
            if (!scrolledOf.contains(popup.branch())) {
                scrolled = 0;
                scrolledOf = Option.of(popup.branch());
            }
            Option<CheckTimes> times = popup.checks()
                    .flatMap(read -> read.toOption())
                    .flatMap(read -> model.gitHub()
                            .checkTimes()
                            .filter(known -> known.repository().equals(read.repository())));
            List<StyledElement<?>> lines = popup.checks()
                    .map(read -> read.fold(ChecksDialog::failed, checks -> checks(popup, checks, times, model.now())))
                    .getOrElse(this::loading);
            int blanks = CONTENT_HEIGHT - lines.size();
            return DialogFrame.withHint(
                            title(popup),
                            WIDTH,
                            "dialog.checks.hint",
                            lines.appendAll(List.fill(Math.max(0, blanks), () -> text(""))))
                    .id(ID)
                    .focusable()
                    .onKeyEvent(this::keys)
                    // The lines, the blank line and the hint, plus the border.
                    .length(CONTENT_HEIGHT + 2 + 2);
        });
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
     * The counts, and the checks that fit from where they are scrolled to.
     */
    private List<StyledElement<?>> checks(
            ChecksPopup popup, PendingChecks read, Option<CheckTimes> times, Instant now) {
        List<Check> all = popup.read(Either.right(read));
        if (all.isEmpty()) {
            return List.of(text(Messages.get("dialog.checks.none")).addClass(Theme.Classes.DIM));
        }
        List<Check> ordered = CheckRows.order(all, all, times);
        scrolled = Math.max(0, Math.min(scrolled, ordered.size() - VISIBLE_CHECKS));
        int nameWidth = CheckRows.nameWidth(ordered);
        return List.<StyledElement<?>>of(row(CheckRows.counts(all, times, now).toJavaArray(StyledElement[]::new)))
                .append(text(""))
                .appendAll(ordered.drop(scrolled)
                        .take(VISIBLE_CHECKS)
                        .map(check -> row(CheckRows.row(check, all, times, now, nameWidth, true)
                                .toJavaArray(StyledElement[]::new))));
    }

    private EventResult keys(KeyEvent event) {
        if (event.code() == KeyCode.ESCAPE || event.code() == KeyCode.ENTER) {
            dispatch.accept(new ChecksMsg.Closed());
        } else if (event.isChar('R')) {
            dispatch.accept(new ChecksMsg.RefreshChosen());
        } else if (event.isChar('j') || event.code() == KeyCode.DOWN) {
            scrolled++;
        } else if (event.isChar('k') || event.code() == KeyCode.UP) {
            scrolled = Math.max(0, scrolled - 1);
        } else if (event.code() == KeyCode.PAGE_DOWN) {
            scrolled += VISIBLE_CHECKS;
        } else if (event.code() == KeyCode.PAGE_UP) {
            scrolled = Math.max(0, scrolled - VISIBLE_CHECKS);
        } else {
            return EventResult.UNHANDLED;
        }
        return EventResult.HANDLED;
    }
}
