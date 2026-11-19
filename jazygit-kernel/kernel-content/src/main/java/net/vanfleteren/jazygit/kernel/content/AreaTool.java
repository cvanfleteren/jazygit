package net.vanfleteren.jazygit.kernel.content;

import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.PasteEvent;
import io.vavr.collection.Seq;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.function.Function;

/**
 * A tool of a {@link ScrollArea}, like the search of the content panel (ADR 126) or going to a line (ADR 140): keys it
 * acts on in the area, and a line above the area to type in while it is open. Its state is part of the
 * {@link AreaState}; the tool keeps only where the cursor of its line is. The area asks its tools in order and takes the
 * first answer, so it names none of them: adding a tool is adding it to {@link #standard()}.
 */
interface AreaTool {

    /**
     * The tools of an area, the first asked first: the line of a search goes before the line to type a line number in.
     * Each area has tools of its own, as a tool keeps where the cursor of its line is.
     */
    static Vector<AreaTool> standard() {
        return Vector.of(new SearchTool(), new GoToLineTool());
    }

    /**
     * The first answer of {@code tools} to {@code asked}, asking them in order until one answers.
     */
    static <T> Option<T> first(Seq<AreaTool> tools, Function<AreaTool, Option<T>> asked) {
        return tools.iterator().flatMap(asked).headOption();
    }

    /**
     * The line above the area with the focus that the tool has open in {@code state}, if it has one.
     */
    Option<StyledElement<?>> line(AreaState state);

    /**
     * What {@code event} does in the area with the focus while the tool's line is open in {@code state}: it takes every
     * key then, so none while its line is closed.
     */
    Option<Reaction> typed(AreaState state, Viewport view, KeyEvent event);

    /**
     * What a key in the area with the focus, which no line takes, does to the tool's part of {@code state} first, as a
     * key lets go of what the tool waited for.
     */
    default AreaState pressed(AreaState state) {
        return state;
    }

    /**
     * What {@code event} does in the area with the focus before the area is left on it, if the tool acts on it, as
     * Escape drops a search kept (ADR 126).
     */
    default Option<Reaction> beforeLeaving(AreaState state, Viewport view, KeyEvent event) {
        return Option.none();
    }

    /**
     * What {@code event} does in the area with the focus, as it did not leave on it, if it is a key of the tool: before
     * the keys that move the cursor or scroll.
     */
    Option<Reaction> key(AreaState state, Viewport view, KeyEvent event);

    /**
     * What pasting {@code event} does while the tool's line is open in {@code state}: none while it is closed. While it
     * is open, the line keeps the paste even when it does not take it, as {@link #typed} keeps every key, so no tool
     * behind it gets it.
     */
    Option<Reaction> pasted(AreaState state, Viewport view, PasteEvent event);
}
