package net.vanfleteren.jazygit.kernel.ui.checks;

import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.TextElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.build.FailedBuild;
import net.vanfleteren.jazygit.kernel.state.popup.CheckList;
import net.vanfleteren.jazygit.kernel.state.popup.ListCursor;
import net.vanfleteren.jazygit.kernel.state.popup.PopupMsg;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Scope;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.text.ErrorText;
import net.vanfleteren.jazygit.kernel.ui.text.TextWidth;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.kernel.ui.widgets.Symbol;

/**
 * The popup that lists the checks on the tip of a branch one by one, drawn the same by every forge (ADR 32, 195): the
 * counts, when they started and ended, a bar of how far they are together (ADR 89), a note, and the checks that fit,
 * the one picked marked (ADR 160); a click on one picks it. It scrolls with the arrows, j/k and the page keys; Enter
 * and Escape close it; its other keys are those of its scope, which hosts the keys other features add to a failed
 * build (ADR 161). It is always as high, so it keeps its size when the checks arrive. What each check says is the
 * forge's: it gives the rows drawn.
 *
 * @param <P> the popup it draws, whose keys act on it
 */
public final class ChecksFrame<P> {

    public static final int WIDTH = 100;

    /**
     * The columns inside the border.
     */
    public static final int CONTENT_WIDTH = DialogFrame.contentWidth(WIDTH);

    /**
     * The checks listed at once; the others are scrolled to.
     */
    public static final int VISIBLE_CHECKS = CheckList.VISIBLE;

    // What marks the check picked (ADR 160), and what stands in its place before the others.
    private static final String PICKED = "▸ ";
    private static final String UNPICKED = "  ";

    /**
     * The columns a check's row takes: the mark of the check picked takes its columns from each.
     */
    public static final int ROW_WIDTH = CONTENT_WIDTH - TextWidth.of(PICKED);

    // The counts, when they started and ended, how far they are, a note or a blank line, and the checks.
    private static final int CONTENT_HEIGHT = 1 + 1 + 1 + 1 + VISIBLE_CHECKS;
    // How wide how far the checks are together is written, after their bar and a space: "100%".
    private static final int PERCENT = 4;
    // A time of day, of today; one of another day has its date before it.
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String id;
    private final Scope<P> scope;
    private final Consumer<Msg> dispatch;
    private final Supplier<Keymap> keymap;
    private final Supplier<Msg> closed;

    /**
     * @param id       the id of the popup's element
     * @param scope    the scope of the popup's keys
     * @param dispatch receives the messages for the user's actions, on the render thread
     * @param keymap   the keys, asked for when a key is pressed, as the app makes it of every feature
     * @param closed   what Enter and Escape say
     */
    public ChecksFrame(
            String id, Scope<P> scope, Consumer<Msg> dispatch, Supplier<Keymap> keymap, Supplier<Msg> closed) {
        this.id = id;
        this.scope = scope;
        this.dispatch = dispatch;
        this.keymap = keymap;
        this.closed = closed;
    }

    /**
     * The popup {@code popup}, titled {@code title}, showing {@code content}: the checks, or why reading them failed;
     * while they are first read, {@code loading} and a spinner at {@code spinnerAt}. Under them the hint
     * {@code hint}, and the keys other features add to {@code failed}, what failed of the checks.
     */
    public Element render(
            Model model,
            P popup,
            String title,
            Option<Either<Failure, Listing>> content,
            String loading,
            Instant spinnerAt,
            String hint,
            Option<FailedBuild> failed) {
        // How far the checks are scrolled is the line of the popup's list cursor: the first one shown (ADR 2).
        ListCursor cursor = model.listCursor();
        int size = content.flatMap(Either::toOption)
                .map(listing -> listing.rows().size())
                .getOrElse(0);
        int last = Math.max(0, size - VISIBLE_CHECKS);
        int first = Math.clamp(cursor.line().getOrElse(0), 0, last);
        List<StyledElement<?>> lines = content.map(
                        read -> read.fold(ChecksFrame::failed, listing -> listed(listing, first)))
                .getOrElse(() -> loading(loading, spinnerAt));
        int blanks = CONTENT_HEIGHT - lines.size();
        List<Element> hints = hint(hint);
        return DialogFrame.of(
                        title,
                        WIDTH,
                        lines.<Element>map(line -> line)
                                .appendAll(List.fill(Math.max(0, blanks), () -> text("")))
                                .append(text(""))
                                .appendAll(hints)
                                .toJavaArray(Element[]::new))
                .id(id)
                .focusable()
                .onKeyEvent(event -> keys(event, cursor, first, last, popup, failed))
                // The lines, the blank line and the hint, plus the border.
                .length(CONTENT_HEIGHT + 1 + hints.size() + 2);
    }

    /**
     * {@code title}, then a spinner at {@code spinnerAt} while the checks are read again as the user asked (ADR 34),
     * or a mark when the last read of them failed, so they are stale.
     */
    public static String title(String title, boolean asked, boolean stale, Instant spinnerAt) {
        return asked
                ? title + " " + TimeText.spinnerFrame(spinnerAt)
                : stale ? title + " · " + Messages.get("checks.dialog.stale") : title;
    }

    private static List<StyledElement<?>> loading(String loading, Instant spinnerAt) {
        return List.of(row(
                text(loading + " ").addClass(Theme.Classes.DIM),
                text(TimeText.spinnerFrame(spinnerAt)).addClass(Theme.Classes.SPINNER)));
    }

    private static List<StyledElement<?>> failed(Failure failure) {
        return List.of(
                DialogFrame.wrapped(Messages.get("checks.dialog.failed", ErrorText.of(failure)), WIDTH, CONTENT_HEIGHT)
                        .addClass(Theme.Classes.ERROR));
    }

    /**
     * The counts, when they started and ended, how far they are, the note, and the checks that fit from the
     * {@code first} one shown; or that there are none, and the note.
     */
    private List<StyledElement<?>> listed(Listing listing, int first) {
        if (listing.rows().isEmpty()) {
            return List.<StyledElement<?>>of(
                            text(Messages.get("checks.dialog.none")).addClass(Theme.Classes.DIM))
                    .appendAll(listing.note());
        }
        return List.<StyledElement<?>>of(row(listing.counts().toJavaArray(StyledElement[]::new)))
                .appendAll(listing.span())
                .append(row(listing.gauge().toJavaArray(StyledElement[]::new)))
                // What it says of the check picked, or of running failed checks again, takes the blank line.
                .append(listing.note().getOrElse(() -> text("")))
                .appendAll(listing.rows().drop(first).take(VISIBLE_CHECKS).map(this::drawn));
    }

    private StyledElement<?> drawn(Row checkRow) {
        StyledElement<?> mark = checkRow.picked() ? text(PICKED).addClass(Theme.Classes.BOLD) : text(UNPICKED);
        return row(checkRow.drawn().prepend(mark).toJavaArray(StyledElement[]::new))
                .onMouseEvent(event -> clicked(event, checkRow.clicked()));
    }

    /**
     * A left click on a check picks it (ADR 160).
     */
    private EventResult clicked(MouseEvent event, Msg msg) {
        if (event.kind() != MouseEventKind.PRESS || !event.isLeftButton()) {
            return EventResult.UNHANDLED;
        }
        dispatch.accept(msg);
        return EventResult.HANDLED;
    }

    /**
     * A bar of how far the checks are together, {@code progress}, across the dialog, in {@code cssClass}, and how far
     * that is: {@code ━━━━━━━━━━━──────── 62%}.
     */
    public static List<StyledElement<?>> gauge(double progress, String cssClass) {
        String percent = Messages.get("checks.dialog.progress", String.valueOf(ProgressBar.percent(progress)));
        return ProgressBar.of(progress, CONTENT_WIDTH - 1 - PERCENT, cssClass)
                .append(text(" " + TextWidth.alignRight(percent, PERCENT)));
    }

    /**
     * When the checks started, and when they ended and how long they took, or how long they run at {@code now}:
     * {@code started 14:02:10 · ended 14:06:41 · took 4m 31s}, {@code started 14:02:10 · running 4m 31s}.
     */
    public static StyledElement<?> span(Instant start, Option<Instant> end, Instant now, ZoneId zone) {
        String took = CheckDurations.exact(Duration.between(start, end.getOrElse(now)));
        List<StyledElement<?>> ended = end.map(at -> List.<StyledElement<?>>of(
                        label("checks.dialog.ended", " · "),
                        text(timeOfDay(at, now, zone)),
                        label("checks.dialog.took", " · "),
                        text(took)))
                .getOrElse(() -> List.of(label("checks.dialog.running", " · "), text(took)));
        return row(List.<StyledElement<?>>of(label("checks.dialog.started", ""), text(timeOfDay(start, now, zone)))
                .appendAll(ended)
                .toJavaArray(StyledElement[]::new));
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
    public static String timeOfDay(Instant at, Instant now, ZoneId zone) {
        ZonedDateTime local = at.atZone(zone);
        return local.toLocalDate().equals(now.atZone(zone).toLocalDate())
                ? TIME.format(local)
                : DATE_TIME.format(local);
    }

    /**
     * A check taking {@code width} columns: its {@code symbols} and its {@code name} on the left, cut to leave room,
     * and how it is doing or how long it took, {@code doing}, on the right, in a column {@code doingWidth} wide, lined
     * up on the right and dimmed: {@code ● acceptance  running 2m 30s}. Its name is dimmed when {@code dim}, as of a
     * queued one. Without {@code doing}, its name takes the room.
     */
    public static List<StyledElement<?>> filling(
            List<Symbol> symbols, String name, boolean dim, Option<String> doing, int width, int doingWidth) {
        int left =
                width - symbols.map(symbol -> TextWidth.of(symbol.text())).sum().intValue() - 1;
        return symbols.<StyledElement<?>>map(Symbol::element)
                .appendAll(doing.map(text -> {
                            TextElement named = text(" " + padded(name, left - 2 - doingWidth) + "  ");
                            return List.<StyledElement<?>>of(
                                    dim ? named.addClass(Theme.Classes.DIM) : named,
                                    text(TextWidth.alignRight(text, doingWidth)).addClass(Theme.Classes.DIM));
                        })
                        .getOrElse(() -> List.of(text(" " + TextWidth.cut(name, left)))));
    }

    /**
     * {@code row}, a check's row drawn after {@code symbols} symbols, with its name in bold: the check picked (ADR
     * 160).
     */
    public static List<StyledElement<?>> picked(int symbols, List<StyledElement<?>> row) {
        return symbols < row.size() ? row.update(symbols, row.get(symbols).addClass(Theme.Classes.BOLD)) : row;
    }

    /**
     * {@code name}, cut to {@code width} columns, or with spaces after it to take them, so what follows lines up.
     */
    public static String padded(String name, int width) {
        String cut = TextWidth.cut(name, width);
        return cut + " ".repeat(Math.max(0, width - TextWidth.of(cut)));
    }

    /**
     * @param first the check shown first, as drawn
     * @param last  the furthest the checks scroll to: the first one shown when the last one is at the bottom
     */
    private EventResult keys(
            KeyEvent event, ListCursor cursor, int first, int last, P popup, Option<FailedBuild> failed) {
        if (event.isCancel() || event.isConfirm()) {
            dispatch.accept(closed.get());
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
                    .handle(scope, event, Option.of(popup), new Keymap.Hosted<>(Scopes.FAILED_BUILD, failed));
            msg.forEach(dispatch);
            return msg.isDefined() ? EventResult.HANDLED : EventResult.UNHANDLED;
        }
        return EventResult.HANDLED;
    }

    /**
     * The hint, and on a line of its own, as the first is full, the keys other features add to a failed build, if
     * any (ADR 161).
     */
    private List<Element> hint(String hint) {
        List<String> added = keymap.get()
                .bindings(Scopes.FAILED_BUILD.id())
                .map(binding -> "(" + binding.label() + ") " + Messages.get(binding.descriptionKey()));
        return List.of(hint)
                .appendAll(Option.when(added.nonEmpty(), () -> added.mkString("   ")))
                .map(line -> text(line).addClass(Theme.Classes.DIM));
    }

    private void scroll(ListCursor cursor, int first, int last) {
        dispatch.accept(new PopupMsg.ListCursorChanged(cursor.at(Math.clamp(first, 0, last))));
    }

    /**
     * What the popup lists once the checks were read.
     *
     * @param counts how many are in each state, and when they are expected to be done
     * @param span   when they started and ended; none when that is not known
     * @param gauge  how far they are together
     * @param note   what it says of the check picked, or of running failed checks again, in the blank line above the
     *               checks
     * @param rows   the checks, in the order they are listed
     */
    public record Listing(
            List<StyledElement<?>> counts,
            Option<StyledElement<?>> span,
            List<StyledElement<?>> gauge,
            Option<StyledElement<?>> note,
            List<Row> rows) {}

    /**
     * A check, as listed.
     *
     * @param drawn   what it shows, after the mark of the one picked
     * @param picked  whether it is the one picked
     * @param clicked what a click on it says
     */
    public record Row(List<StyledElement<?>> drawn, boolean picked, Msg clicked) {}
}
