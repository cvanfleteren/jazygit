package net.vanfleteren.jazygit.kernel.content;

import static net.vanfleteren.jazygit.kernel.content.Reaction.handled;
import static net.vanfleteren.jazygit.kernel.content.Reaction.moved;
import static net.vanfleteren.jazygit.kernel.content.Reaction.reviewed;
import static net.vanfleteren.jazygit.kernel.content.Reaction.scrolled;
import static net.vanfleteren.jazygit.kernel.content.Reaction.unhandled;

import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.Seq;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.content.diff.DiffCursor;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.diff.Anchor;
import net.vanfleteren.jazygit.kernel.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.kernel.state.diff.DiffSide;
import net.vanfleteren.jazygit.kernel.state.diff.Scrolled;
import net.vanfleteren.jazygit.kernel.state.view.ContentSearchMsg;
import net.vanfleteren.jazygit.kernel.state.view.FileStepMsg;
import net.vanfleteren.jazygit.kernel.state.view.LineNumberMsg;
import net.vanfleteren.jazygit.kernel.state.view.ViewMsg;
import net.vanfleteren.jazygit.kernel.ui.content.KeyContext;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;

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
     * What {@code event} does before the area is left on it: the line above the area of one of its {@code tools} takes
     * the keys while it is open; else Escape, and Left where it leaves as Escape does, drop an open range, then what a
     * tool drops on it, as a search kept (ADR 60, 126). Left held down at the left edge stops there: only pressing it
     * again leaves (ADR 118). Unhandled, the area has let go of what the title said, and the tools of what they waited
     * for.
     */
    static Reaction beforeLeaving(AreaState state, Viewport view, KeyEvent event, Seq<AreaTool> tools) {
        boolean focused = state.focused();
        Option<Reaction> typed =
                focused ? AreaTool.first(tools, tool -> tool.typed(state, view, event)) : Option.none();
        if (typed.isDefined()) {
            return typed.get();
        }
        AreaState pressed =
                focused ? tools.foldLeft(state.withNotice(Option.none()), (at, tool) -> tool.pressed(at)) : state;
        if (atLeftEdge(state, event) && event.isRepeat()) {
            return handled(pressed, view);
        }
        boolean leaving = leaving(state, event);
        return pressed.cursor()
                .filter(on -> leaving && focused && on.mark().isDefined())
                .map(ranged -> moved(pressed, view, ranged.marked().get()))
                .orElse(() -> focused
                        ? AreaTool.first(tools, tool -> tool.beforeLeaving(pressed, view, event))
                        : Option.none())
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
     * What {@code event} does in the area with the focus, as it did not leave on it: the keys of its {@code tools}
     * first. Key handlers also see keys typed elsewhere, which the area does not act on.
     */
    static Reaction afterLeaving(AreaState state, Viewport view, KeyEvent event, Seq<AreaTool> tools) {
        if (!state.focused()) {
            return unhandled(state, view);
        }
        Option<DiffCursor> cursor = state.cursor();
        return AreaTool.first(tools, tool -> tool.key(state, view, event))
                .orElse(() -> cursor.flatMap(on -> move(on, event)).map(to -> moved(state, view, to)))
                .orElse(() -> cursor.flatMap(on -> KEYMAP.handle(state.keys(), event, Option.of(at(state, view, on))))
                        .map(msg -> reviewed(state, view, msg)))
                // A body can have keys of its own, as the builds of a branch do to show another workflow (ADR 131).
                .orElse(() -> state.styled()
                        .flatMap(shown -> shown.body().key(event, new KeyContext(KEYMAP, state.searching())))
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
                .orElse(() -> Option.when(event.isChar(OLD_SIDE), () -> cursor.toSide(DiffSide.OLD))
                        .flatMap(to -> to))
                .orElse(() -> Option.when(event.isChar(NEW_SIDE), () -> cursor.toSide(DiffSide.NEW))
                        .flatMap(to -> to));
    }

    /**
     * Where the area scrolls to on {@code event}, if it is a key for that, the model told: up and down as its text area
     * would, sideways itself.
     */
    private static Option<Reaction> scroll(AreaState state, Viewport view, KeyEvent event) {
        int column = state.columns().column();
        int furthest = state.columns().furthest();
        Option<Viewport> down = Option.<Viewport>none()
                .orElse(Option.when(event.matches(Actions.MOVE_UP), () -> view.by(-1)))
                .orElse(Option.when(event.matches(Actions.MOVE_DOWN), () -> view.by(1)))
                .orElse(Option.when(event.matches(Actions.PAGE_UP), view::pageUp))
                .orElse(Option.when(event.matches(Actions.PAGE_DOWN), view::pageDown))
                .orElse(Option.when(event.matches(Actions.HOME), () -> view.at(0)))
                .orElse(Option.when(event.matches(Actions.END), view::bottom));
        Option<Integer> sideways = Option.<Integer>none()
                .orElse(Option.when(allLeft(event), 0))
                .orElse(Option.when(allRight(event), furthest))
                .orElse(Option.when(left(event), () -> Math.max(0, column - 1)))
                .orElse(Option.when(right(event), () -> Math.min(furthest, column + 1)));
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
}
