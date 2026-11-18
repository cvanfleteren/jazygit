package net.vanfleteren.jazygit.content;

import io.vavr.collection.Vector;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.content.diff.DiffCursor;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.diff.Found;
import net.vanfleteren.jazygit.state.diff.Scrolled;
import net.vanfleteren.jazygit.state.view.ViewMsg;

/**
 * What a key or the mouse does to an area: the area after it, where it is scrolled to, and the messages it sends, in
 * order. {@link ScrollArea} takes the area on before it sends them.
 *
 * @param state   the area after it
 * @param view    where it is scrolled to after it
 * @param sent    the messages it sends
 * @param handled whether the area acted on it; one it did not act on may still have changed it, as a key clears what
 *                the title said
 */
record Reaction(AreaState state, Viewport view, Vector<Msg> sent, boolean handled) {

    /**
     * The area acted on it, as {@code state} and scrolled to {@code view}, and sends nothing.
     */
    static Reaction handled(AreaState state, Viewport view) {
        return new Reaction(state, view, Vector.empty(), true);
    }

    /**
     * The area did not act on it, but is {@code state} now.
     */
    static Reaction unhandled(AreaState state, Viewport view) {
        return new Reaction(state, view, Vector.empty(), false);
    }

    /**
     * This, sending {@code msg} after what it sends.
     */
    Reaction sending(Msg msg) {
        return new Reaction(state, view, sent.append(msg), true);
    }

    /**
     * This, sending {@code msg} before what it sends.
     */
    Reaction preceded(Msg msg) {
        return new Reaction(state, view, sent.prepend(msg), true);
    }

    /**
     * This, sending each of {@code msgs} after what it sends.
     */
    Reaction sendingAll(Iterable<? extends Msg> msgs) {
        return new Reaction(state, view, sent.appendAll(msgs), true);
    }

    /**
     * {@code msg}, which a key of the diff of a pull request made, sent, the area told first where it scrolls to if it
     * does.
     */
    static Reaction reviewed(AreaState state, Viewport view, Msg msg) {
        AreaState told = msg instanceof ViewMsg.ContentScrolled(var ignored, Scrolled to) ? state.told(to) : state;
        return handled(told, view).sending(msg);
    }

    /**
     * The cursor {@code moved}, and the model told where it is and where the area is scrolled to, in one message, so
     * the model never has the cursor off the screen.
     */
    static Reaction moved(AreaState state, Viewport view, DiffCursor moved) {
        return scrolled(state.withCursor(Option.of(moved)), view.at(moved.row()), Option.none());
    }

    /**
     * The model told where the area is scrolled to: {@code view}.
     */
    static Reaction scrolled(AreaState state, Viewport view) {
        return scrolled(state, view, Option.none());
    }

    /**
     * The model told where the area is scrolled to, {@code view}, and the match of the search it went to, if it did
     * (ADR 126): any other move lets go of it.
     */
    static Reaction scrolled(AreaState state, Viewport view, Option<Found> found) {
        Scrolled to = new Scrolled(
                state.content(),
                view.top(),
                state.columns().column(),
                state.cursor().map(DiffCursor::anchor),
                state.cursor().flatMap(DiffCursor::markAnchor),
                found);
        return handled(state.told(to), view).sending(new ViewMsg.ContentScrolled(state.area(), to));
    }
}
