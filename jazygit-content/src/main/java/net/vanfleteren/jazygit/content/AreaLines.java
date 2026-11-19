package net.vanfleteren.jazygit.content;

import static net.vanfleteren.jazygit.content.Reaction.handled;
import static net.vanfleteren.jazygit.content.Reaction.moved;
import static net.vanfleteren.jazygit.content.Reaction.scrolled;

import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.control.Option;
import java.util.function.BiFunction;
import net.vanfleteren.jazygit.content.AreaState.Wanted;
import net.vanfleteren.jazygit.content.diff.DiffCursor;
import net.vanfleteren.jazygit.content.diff.Drawn;
import net.vanfleteren.jazygit.content.diff.LineGoal;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.state.diff.Anchor;
import net.vanfleteren.jazygit.state.diff.DiffCursorAt;
import net.vanfleteren.jazygit.state.diff.Scrolled;
import net.vanfleteren.jazygit.state.view.LineNumberMsg;
import net.vanfleteren.jazygit.state.view.ViewMsg;
import net.vanfleteren.jazygit.ui.i18n.Messages;
import net.vanfleteren.jazygit.ui.keys.Scopes;

/**
 * Going to a line by its number in an area (ADR 140): the line to type it in, and where the cursor goes.
 */
final class AreaLines {

    // The key that opens the line to type a line number in, and how many digits go in it.
    static final char GO_TO_LINE = ':';
    private static final int MOST_DIGITS = 9;

    private AreaLines() {}

    /**
     * {@code :}: opens the line to type a line number in, where the area shows lines of a file; elsewhere the title
     * says there are none.
     */
    static Reaction opened(AreaState state, Viewport view) {
        if (!state.cursor().exists(on -> numbered(state, on))) {
            return handled(state.withNotice(Option.of(Messages.get("content.line.none"))), view);
        }
        return handled(state.withLine(state.line().withNumber(Option.of(""))), view)
                .sending(new LineNumberMsg.Opened());
    }

    /**
     * Whether the area, its cursor {@code on}, shows lines of a file to go to by their number: what {@code :} acts on,
     * and the help lists it as acting on.
     */
    static boolean numbered(AreaState state, DiffCursor on) {
        return state.styled().exists(shown -> LineGoal.numbered(shown.stops(), shown.places(), on.stop()));
    }

    /**
     * {@code state}, as {@code shown} is drawn anew, still waiting for the line asked for while a marker leaves it out,
     * as while the file is read whole. Once it shows, the cursor is on it, as the model has it there; when it is not
     * there, the title says so, and which line the cursor is on.
     */
    static AreaState arrived(AreaState state, Styled shown) {
        Option<Wanted> asked = state.line().wanted();
        Option<LineGoal> goal = asked.flatMap(wanted -> state.cursor()
                .map(on ->
                        LineGoal.of(shown.stops(), shown.places(), on.stop(), ReviewThread.Side.RIGHT, wanted.line())));
        AreaState waiting = state.withLine(
                state.line().withWanted(asked.filter(ignored -> goal.exists(LineGoal.Hidden.class::isInstance))));
        return goal.flatMap(reached -> reached instanceof LineGoal.OnStop(var _, boolean exact, int at) && !exact
                        ? Option.of(Messages.get(
                                "content.line.notShown", asked.get().line(), at))
                        : Option.<String>none())
                .map(says -> waiting.withNotice(Option.of(says)))
                .getOrElse(waiting);
    }

    /**
     * What {@code event} does while the line to type a line number in is open with {@code text}: the keys that
     * {@code edit} it do, as long as it holds only digits; Escape closes it; Enter closes it and goes to the line typed.
     * The other keys do nothing.
     */
    static Reaction typed(
            AreaState state,
            Viewport view,
            String text,
            KeyEvent event,
            BiFunction<String, KeyEvent, Option<String>> edit) {
        AreaState closed = state.withLine(state.line().withNumber(Option.none()));
        if (event.isCancel()) {
            return handled(closed, view).sending(new LineNumberMsg.Closed());
        }
        if (event.isConfirm()) {
            Option<Reaction> gone = Option.when(
                            !text.isEmpty(),
                            () -> closed.cursor()
                                    .flatMap(on -> closed.styled()
                                            .map(shown -> toLine(closed, view, on, shown, Integer.parseInt(text)))))
                    .flatMap(reaction -> reaction);
            return gone.getOrElse(() -> handled(closed, view)).preceded(new LineNumberMsg.Closed());
        }
        return edit.apply(text, event)
                .filter(AreaLines::isNumber)
                .map(edited -> edited(state, view, edited))
                .getOrElse(() -> handled(state, view));
    }

    /**
     * Shows {@code text} in the line to type a line number in, and tells the model.
     */
    static Reaction edited(AreaState state, Viewport view, String text) {
        return handled(state.withLine(state.line().withNumber(Option.of(text))), view)
                .sending(new LineNumberMsg.Edited(text));
    }

    /**
     * Whether {@code text} can be in the line to type a line number in: digits only, no more than fit a number.
     */
    static boolean isNumber(String text) {
        return text.length() <= MOST_DIGITS && text.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    /**
     * Goes to line {@code line} of the file of the cursor {@code on} in {@code shown}, numbered in the file of the side
     * of the cursor in the split view, in the old file in the original, else in the new file.
     */
    private static Reaction toLine(AreaState before, Viewport view, DiffCursor on, Styled shown, int line) {
        ReviewThread.Side side = before.original()
                ? ReviewThread.Side.LEFT
                : shown.split().split() ? on.side() : ReviewThread.Side.RIGHT;
        AreaState state = before.withMatch(Option.none());
        return switch (LineGoal.of(shown.stops(), shown.places(), on.stop(), side, line)) {
            case LineGoal.OnStop(int index, boolean exact, int at) -> {
                Drawn.Stop stop = shown.stops().get(index);
                yield reached(
                        state,
                        view,
                        on.toLine(stop.row(), stop.mark().side()),
                        Option.when(!exact, () -> Messages.get("content.line.notShown", line, at)));
            }
            case LineGoal.Hidden(Anchor.OnLine wanted, Anchor lines, int closest) ->
                hidden(state, view, on, wanted, lines, shown.stops().get(closest));
            case LineGoal.InRow(int row, boolean exact, int at) -> {
                Option<String> says = Option.when(!exact, () -> Messages.get("content.line.notShown", line, at));
                // A row of JSON or YAML rendered is a stop; a block of Markdown is scrolled to the top, as Ctrl+T
                // leaves it, which a key then acts on (ADR 103).
                yield shown.stops().exists(stop -> stop.row() == row)
                        ? reached(state, view, on.toLine(row), says)
                        : scrolled(state.withNotice(says), view.at(row));
            }
            case LineGoal.Nowhere _ -> handled(state.withNotice(Option.of(Messages.get("content.line.none"))), view);
        };
    }

    /**
     * Moves the cursor {@code to} the line gone to, the title saying {@code says}; nowhere when a range is open and the
     * line is outside its hunk, the title saying so.
     */
    private static Reaction reached(AreaState state, Viewport view, Option<DiffCursor> to, Option<String> says) {
        return to.map(on -> moved(state.withNotice(says), view, on))
                .getOrElse(() -> handled(state.withNotice(Option.of(Messages.get("content.line.outsideRange"))), view));
    }

    /**
     * Goes to the line {@code wanted}, which a marker leaves out: shows {@code lines}, those around it, as Enter on the
     * marker shows its lines (ADR 77), and tells the model the cursor is on it, which the area finds once it shows;
     * until then the cursor is on {@code closest}. Refused while a range is open: the line is in no hunk.
     */
    private static Reaction hidden(
            AreaState state, Viewport view, DiffCursor on, Anchor.OnLine wanted, Anchor lines, Drawn.Stop closest) {
        if (on.mark().isDefined()) {
            return handled(state.withNotice(Option.of(Messages.get("content.line.outsideRange"))), view);
        }
        DiffCursor near = on.toLine(closest.row(), closest.mark().side()).getOrElse(on);
        Viewport shown = view.at(near.row());
        Scrolled to =
                new Scrolled(state.content(), shown.top(), state.columns().column(), Option.of(wanted), Option.none());
        AreaState waiting = state.withNotice(Option.none())
                .withLine(state.line().withWanted(Option.of(new Wanted(state.content(), wanted.number()))))
                .withCursor(Option.of(near))
                .told(to);
        return handled(waiting, shown)
                .sending(new ViewMsg.ContentScrolled(state.area(), to))
                .sendingAll(AreaKeys.KEYMAP.handle(
                        Scopes.DIFF,
                        KeyEvent.ofKey(KeyCode.ENTER),
                        Option.of(new DiffCursorAt(
                                state.area(), to, lines, Option.none(), Option.none(), Option.none()))));
    }
}
