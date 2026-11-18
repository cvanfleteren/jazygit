package net.vanfleteren.jazygit.content;

import static net.vanfleteren.jazygit.content.Reaction.handled;
import static net.vanfleteren.jazygit.content.Reaction.moved;
import static net.vanfleteren.jazygit.content.Reaction.reviewed;
import static net.vanfleteren.jazygit.content.Reaction.scrolled;
import static net.vanfleteren.jazygit.content.Reaction.unhandled;

import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.control.Option;
import java.util.function.BiFunction;
import net.vanfleteren.jazygit.content.diff.DiffCursor;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.diff.Scrolled;
import net.vanfleteren.jazygit.state.diff.ScrolledContent;
import net.vanfleteren.jazygit.state.github.builds.BuildsAt;
import net.vanfleteren.jazygit.state.view.ContentSearchMsg;
import net.vanfleteren.jazygit.state.view.FileStepMsg;
import net.vanfleteren.jazygit.state.view.LineNumberMsg;
import net.vanfleteren.jazygit.state.view.ViewMsg;
import net.vanfleteren.jazygit.ui.keys.Keymap;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * What a key does to an area: a function of the area as last shown, where it is scrolled to and the key. Leaving the
 * area is left to {@link ScrollArea}, between {@link #beforeLeaving} and {@link #afterLeaving}, as only the app knows
 * where the focus goes.
 */
final class AreaKeys {

    static final Keymap KEYMAP = Keymap.standard();
    // The keys that move the cursor of a rich diff by a stop besides the arrows, as the lists of the panes are moved
    // (ADR 60).
    private static final char UP = 'k';
    private static final char DOWN = 'j';
    // The keys that scroll an area sideways besides the arrows, to the end of a long line or a wide code block.
    private static final char LEFT = 'h';
    private static final char RIGHT = 'l';
    // The keys that scroll an area all the way sideways, besides Shift with the arrows.
    private static final char ALL_LEFT = 'H';
    private static final char ALL_RIGHT = 'L';
    // The keys that take the cursor of the split view to the old and the new side of its row (ADR 139).
    private static final char OLD_SIDE = '[';
    private static final char NEW_SIDE = ']';

    private AreaKeys() {}

    /**
     * What {@code event} does before the area is left on it: the lines above the area take the keys while one is open;
     * else Escape, and Left where it leaves as Escape does, drop an open range, then a search kept (ADR 60, 126). Left
     * held down at the left edge stops there: only pressing it again leaves (ADR 118). Unhandled, the area has let go
     * of what the title said.
     */
    static Reaction beforeLeaving(AreaState state, Viewport view, KeyEvent event, Edits edits) {
        boolean focused = state.focused();
        if (focused && state.search().typing().isDefined()) {
            return AreaSearch.typed(state, view, state.search().typing().get(), event, edits.search());
        }
        if (focused && state.line().number().isDefined()) {
            return AreaLines.typed(state, view, state.line().number().get(), event, edits.number());
        }
        AreaState pressed =
                focused ? state.withNotice(Option.none()).withLine(state.line().withWanted(Option.none())) : state;
        if (atLeftEdge(state, event) && event.isRepeat()) {
            return handled(pressed, view);
        }
        boolean leaving = leaving(state, event);
        return pressed.cursor()
                .filter(on -> leaving && focused && on.mark().isDefined())
                .map(ranged -> moved(pressed, view, ranged.marked().get()))
                .orElse(() -> Option.when(
                        focused && event.isCancel() && pressed.searching(),
                        () -> handled(pressed, view).sending(new ContentSearchMsg.Cleared())))
                .getOrElse(() -> unhandled(pressed, view));
    }

    /**
     * Whether {@code event} leaves the area, if nothing before that acts on it: Escape, or Left once the area is
     * scrolled all the way left, as Right in its pane moves into it (ADR 118).
     */
    static boolean leaving(AreaState state, KeyEvent event) {
        return event.isCancel() || atLeftEdge(state, event);
    }

    // Only the arrow leaves: h scrolls, as j and k move, without going to another panel.
    private static boolean atLeftEdge(AreaState state, KeyEvent event) {
        return state.focused()
                && event.matches(Actions.MOVE_LEFT)
                && !event.hasShift()
                && state.columns().column() == 0;
    }

    /**
     * What {@code event} does in the area with the focus, as it did not leave on it: key handlers also see keys typed
     * elsewhere, which the area does not act on.
     */
    static Reaction afterLeaving(AreaState state, Viewport view, KeyEvent event) {
        if (!state.focused()) {
            return unhandled(state, view);
        }
        Option<DiffCursor> cursor = state.cursor();
        return Option.when(
                        event.isChar(AreaLines.GO_TO_LINE) && !event.hasCtrl() && !event.hasAlt(),
                        () -> AreaLines.opened(state, view))
                .orElse(() -> AreaSearch.step(state, view, event))
                .orElse(() -> cursor.flatMap(on -> move(on, event)).map(to -> moved(state, view, to)))
                .orElse(() -> cursor.flatMap(on -> KEYMAP.handle(
                                state.review() ? Scopes.PULL_REQUEST_DIFF : Scopes.DIFF,
                                event,
                                Option.of(at(state, view, on))))
                        .map(msg -> reviewed(state, view, msg)))
                // The builds of a branch have keys of their own, to show another workflow (ADR 131).
                .orElse(() -> Option.when(
                                state.content() instanceof ScrolledContent.Builds,
                                () -> KEYMAP.handle(Scopes.BUILDS, event, Option.of(new BuildsAt(state.searching()))))
                        .flatMap(msg -> msg)
                        .map(msg -> reviewed(state, view, msg)))
                // Without a cursor, as in the plain style, only the keys that move to another file act (ADR 117).
                .orElse(() -> Option.when(cursor.isEmpty(), () -> KEYMAP.handle(Scopes.DIFF, event, Option.none()))
                        .flatMap(msg -> msg)
                        .filter(msg -> msg instanceof FileStepMsg || msg instanceof ContentSearchMsg)
                        .map(msg -> reviewed(state, view, msg)))
                .orElse(() -> scroll(state, view, event))
                .getOrElse(() -> unhandled(state, view));
    }

    /**
     * Where the keys that move the cursor of a rich diff take it, if {@code event} is one of them.
     */
    private static Option<DiffCursor> move(DiffCursor cursor, KeyEvent event) {
        return Option.<DiffCursor>none()
                .orElse(Option.when(event.matches(Actions.MOVE_UP) || event.isChar(UP), cursor::up))
                .orElse(Option.when(event.matches(Actions.MOVE_DOWN) || event.isChar(DOWN), cursor::down))
                .orElse(Option.when(event.matches(Actions.PAGE_UP), cursor::pageUp))
                .orElse(Option.when(event.matches(Actions.PAGE_DOWN), cursor::pageDown))
                .orElse(Option.when(event.matches(Actions.HOME), cursor::home))
                .orElse(Option.when(event.matches(Actions.END), cursor::end))
                // In the split view, on a line: to the old or the new side; on a marker, more lines (ADR 77, 138).
                .orElse(() -> Option.when(event.isChar(OLD_SIDE), () -> cursor.toSide(ReviewThread.Side.LEFT))
                        .flatMap(to -> to))
                .orElse(() -> Option.when(event.isChar(NEW_SIDE), () -> cursor.toSide(ReviewThread.Side.RIGHT))
                        .flatMap(to -> to));
    }

    /**
     * Where the area scrolls to on {@code event}, if it is a key for that, the model told: up and down as its text area
     * would, sideways itself.
     */
    private static Option<Reaction> scroll(AreaState state, Viewport view, KeyEvent event) {
        int column = state.columns().column();
        int widest = state.columns().widest();
        Option<Viewport> down = Option.<Viewport>none()
                .orElse(Option.when(event.matches(Actions.MOVE_UP), () -> view.by(-1)))
                .orElse(Option.when(event.matches(Actions.MOVE_DOWN), () -> view.by(1)))
                .orElse(Option.when(event.matches(Actions.PAGE_UP), view::pageUp))
                .orElse(Option.when(event.matches(Actions.PAGE_DOWN), view::pageDown))
                .orElse(Option.when(event.matches(Actions.HOME), () -> view.at(0)))
                .orElse(Option.when(event.matches(Actions.END), view::bottom));
        Option<Integer> sideways = Option.<Integer>none()
                .orElse(Option.when(allLeft(event), 0))
                .orElse(Option.when(allRight(event), widest))
                .orElse(Option.when(left(event), () -> Math.max(0, column - 1)))
                .orElse(Option.when(right(event), () -> Math.min(widest, column + 1)));
        return down.map(to -> scrolled(state, to))
                .orElse(() -> sideways.map(to -> scrolled(state.withColumn(to), view)));
    }

    private static boolean left(KeyEvent event) {
        return event.matches(Actions.MOVE_LEFT) || event.isChar(LEFT);
    }

    private static boolean right(KeyEvent event) {
        return event.matches(Actions.MOVE_RIGHT) || event.isChar(RIGHT);
    }

    // TamboUI's arrow actions also match the arrows with Shift: these are asked first.
    private static boolean allLeft(KeyEvent event) {
        return (event.isKey(KeyCode.LEFT) && event.hasShift()) || event.isChar(ALL_LEFT);
    }

    private static boolean allRight(KeyEvent event) {
        return (event.isKey(KeyCode.RIGHT) && event.hasShift()) || event.isChar(ALL_RIGHT);
    }

    /**
     * Where {@code cursor} is, as the keys of the diff of a pull request act on it, with where the keys that move it
     * take it.
     */
    static DiffCursorAt at(AreaState state, Viewport view, DiffCursor cursor) {
        Anchor acting = acting(state, cursor);
        // While a search is kept, n and N go to its matches rather than to the threads (ADR 126).
        boolean searching = state.searching();
        return new DiffCursorAt(
                state.area(),
                new Scrolled(
                        state.content(),
                        cursor.row(),
                        state.columns().column(),
                        Option.of(acting),
                        cursor.markAnchor()),
                acting,
                searching
                        ? AreaSearch.toMatch(state, view, cursor, true)
                        : cursor.nextThread().map(next -> movedTo(state, next)),
                searching
                        ? AreaSearch.toMatch(state, view, cursor, false)
                        : cursor.previousThread().map(previous -> movedTo(state, previous)),
                cursor.marked().map(marked -> movedTo(state, marked)),
                cursor.onHalf(),
                Option.when(AreaLines.numbered(state, cursor), LineNumberMsg.Opened::new),
                state.version().exists(version -> version.opening(acting).isDefined()));
    }

    /**
     * What a key acts on with the cursor on {@code cursor}: the stop it is on, but on the bar of a version rendered,
     * which is its only stop, the first line of the block at the top of the area, so a version shown as source again
     * has the cursor there (ADR 103).
     */
    private static Anchor acting(AreaState state, DiffCursor cursor) {
        return cursor.anchor() instanceof Anchor.File
                ? state.styled().flatMap(shown -> shown.top(cursor.row())).getOrElse(cursor::anchor)
                : cursor.anchor();
    }

    private static Msg movedTo(AreaState state, DiffCursor cursor) {
        return new ViewMsg.ContentScrolled(
                state.area(),
                new Scrolled(
                        state.content(),
                        cursor.row(),
                        state.columns().column(),
                        Option.of(cursor.anchor()),
                        cursor.markAnchor()));
    }

    /**
     * How the lines above an area edit their text on a key: the text after it, if it edits it.
     *
     * @param search the line to type the query of a search in (ADR 126)
     * @param number the line to type a line number in (ADR 140)
     */
    record Edits(
            BiFunction<String, KeyEvent, Option<String>> search, BiFunction<String, KeyEvent, Option<String>> number) {}
}
