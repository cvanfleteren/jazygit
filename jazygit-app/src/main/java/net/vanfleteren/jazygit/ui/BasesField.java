package net.vanfleteren.jazygit.ui;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.style.Style;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import java.util.function.Function;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.ListCursor;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.ui.widgets.FilteredCursor;
import net.vanfleteren.jazygit.ui.widgets.FocusBordered;
import net.vanfleteren.jazygit.ui.widgets.FocusHighlighted;
import net.vanfleteren.jazygit.ui.widgets.ListFilter;
import net.vanfleteren.jazygit.ui.widgets.ListKeys;

/**
 * The list of the branches a pull request can merge into, in the popups that create one (ADR 18) and edit one (ADR
 * 59): about 5 rows high, the highlighted one the base, which its border names. The arrows move the highlight, and
 * typing filters the list straight away. Enter goes on to the next field; Escape clears the filter while there is
 * one. Where the user is in it lives in the model's {@code ListCursor}, which every move is sent to.
 *
 * @param <B> what a base is
 */
public final class BasesField<B> {

    // With Ctrl held, clears the filter of the bases, as it clears a line in readline.
    private static final char CLEAR_KEY = 'u';
    // The bases shown at once, past which the list scrolls; the border takes two more lines.
    private static final int ROWS = 5;
    // A base that cannot be changed takes a line, and the border two more.
    private static final int FIXED_HEIGHT = 3;

    private final String id;
    private final Function<B, String> name;
    private final ListElement<?> list;
    // Shown instead of the bases when the filter shows none: the same id, so it keeps the focus and the keys.
    private final ListElement<?> noMatches;
    private final Consumer<Msg> dispatch;

    /**
     * @param id       the id of the list, which takes the focus
     * @param name     how a base is shown
     * @param dispatch receives the messages for the user's moves, on the render thread
     */
    public BasesField(String id, Function<B, String> name, Consumer<Msg> dispatch) {
        this.id = id;
        this.name = name;
        this.list = list().id(id).focusable().highlightSymbol("");
        this.noMatches = list().id(id)
                .focusable()
                .highlightSymbol("")
                .highlightStyle(Style.EMPTY)
                .elements(ListFilter.noMatches());
        this.dispatch = dispatch;
    }

    /**
     * The list of {@code bases}, those the filter shows, with {@code base} highlighted, and named in its border. The
     * base stays highlighted while another field has the focus; the border shows whether the list has it.
     *
     * @param cursor where the user is in the list, as the model has it
     * @param moved  what the popup is told of a move
     * @param keys   the keys of every field of the popup, which come after Escape and Enter here
     */
    public Element render(
            List<B> bases, B base, ListCursor cursor, Moved<B> moved, Function<KeyEvent, EventResult> keys) {
        View<B> view = View.of(bases, base, name, cursor, moved);
        FilteredCursor<B> shown = view.list();
        ListElement<?> rows = shown.lines().isEmpty()
                ? noMatches
                : list.elements(shown.lines()
                                .<StyledElement<?>>map(line -> text(name.apply(line)))
                                .toJavaArray(StyledElement[]::new))
                        .selected(shown.selected());
        rows.onKeyEvent(event -> keys(view, event, keys))
                .onMouseEvent(event -> ListKeys.wheel(
                        event,
                        shown.selected(),
                        shown.size(),
                        shown::highlightable,
                        line -> dispatch.accept(view.at(line))));
        String named = name.apply(base);
        // In a panel, which the themes give the border of the fields; a list's own border is not themed.
        return new FocusBordered(
                panel(
                                shown.cursor()
                                        .filter()
                                        .map(typed -> Messages.get("dialog.newPullRequest.baseFiltered", named, typed))
                                        .getOrElse(() -> Messages.get("dialog.newPullRequest.base", named)),
                                shown.lines().isEmpty() ? rows : new FocusHighlighted(rows))
                        .length(ROWS + 2),
                id);
    }

    /**
     * {@code base}, which cannot be changed, named in the border as the list names it, and {@code why} dimmed under
     * it. It takes no focus, so Tab goes past it.
     */
    public Element fixed(B base, String why) {
        return panel(
                        Messages.get("dialog.newPullRequest.base", name.apply(base)),
                        text(why).addClass(Theme.Classes.DIM))
                .length(FIXED_HEIGHT);
    }

    /**
     * Escape clears the filter while there is one, and Enter goes on to the next field; {@code common}, the keys of
     * every field, come next, then those that edit the filter, then those that move the highlight. The filter shows
     * in the border of the list, where there is no cursor to move, so it only takes what is typed at its end.
     */
    private EventResult keys(View<B> view, KeyEvent event, Function<KeyEvent, EventResult> common) {
        FilteredCursor<B> shown = view.list();
        if (event.code() == KeyCode.ESCAPE && shown.cursor().filter().isDefined()) {
            dispatch.accept(view.filtered(Option.none()));
            return EventResult.HANDLED;
        }
        if (event.code() == KeyCode.ENTER) {
            return EventResult.FOCUS_NEXT;
        }
        EventResult handled = common.apply(event);
        if (handled.isHandled()) {
            return handled;
        }
        Option<String> edited = filtered(shown.cursor().filter().getOrElse(""), event);
        if (edited.isDefined()) {
            dispatch.accept(view.filtered(edited.filter(typed -> !typed.isEmpty())));
            return EventResult.HANDLED;
        }
        Option<Integer> line = ListKeys.move(event, shown.selected(), shown.size(), shown::highlightable);
        line.filter(at -> at != shown.selected()).forEach(at -> dispatch.accept(view.at(at)));
        return line.isDefined() ? EventResult.HANDLED : EventResult.UNHANDLED;
    }

    /**
     * The filter {@code typed} after {@code event}, if it edits it: a character typed goes at its end, Backspace
     * takes the last one off, and Ctrl+U clears it.
     */
    static Option<String> filtered(String typed, KeyEvent event) {
        if (event.code() == KeyCode.BACKSPACE) {
            return Option.of(
                    typed.isEmpty() ? typed : typed.substring(0, typed.offsetByCodePoints(typed.length(), -1)));
        }
        if (event.hasCtrl() && event.isChar(CLEAR_KEY)) {
            return Option.of("");
        }
        return Option.when(
                event.code() == KeyCode.CHAR && !event.hasCtrl() && !event.hasAlt() && event.codePoint() >= ' ',
                () -> typed + event.string());
    }

    /**
     * What the popup is told of a move in the list: where its cursor is now, and the base highlighted, if the filter
     * shows any.
     */
    @FunctionalInterface
    public interface Moved<B> {
        Msg moved(ListCursor cursor, Option<B> base);
    }

    /**
     * The bases as the list shows them, with the model's cursor in it. The base is the highlighted one, wherever the
     * cursor says.
     *
     * @param base the base picked
     * @param list the bases that contain the filter
     */
    private record View<B>(B base, FilteredCursor<B> list, Moved<B> moved) {

        static <B> View<B> of(List<B> bases, B base, Function<B, String> name, ListCursor cursor, Moved<B> moved) {
            FilteredCursor<B> list = FilteredCursor.of(
                    filter -> bases.filter(each -> ListFilter.matches(filter, name.apply(each))), each -> true, cursor);
            // While the filter hides the base, there is none to highlight.
            return new View<>(base, list.at(cursor.at(Math.max(0, list.lines().indexOf(base)))), moved);
        }

        /**
         * The move to the base on {@code line}.
         */
        Msg at(int line) {
            return moved.moved(
                    list.at(line), list.lines().asPartialFunction().lift().apply(line));
        }

        /**
         * The move to {@code filter}: the base stays while it shows, else the first one shown is the base; while
         * none shows, the base stays too, and comes back once the filter shows it again.
         */
        Msg filtered(Option<String> filter) {
            ListCursor after = list.refiltered(filter, Option.of(base));
            return moved.moved(after, list.at(after).highlighted());
        }
    }
}
