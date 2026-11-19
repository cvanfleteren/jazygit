package net.vanfleteren.jazygit.content;

import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.tui.bindings.KeyTrigger;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.PasteEvent;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.ui.widgets.FilterLine;

/**
 * Going to a line by its number as a tool of an area (ADR 140): {@code :} opens the line to type the number in, and a
 * key lets go of a line asked for that the area still waits for. What they do is {@link AreaLines}'.
 */
final class GoToLineTool implements AreaTool {

    // The line keeps where its cursor is; its text is the area's.
    private final FilterLine line = new FilterLine(AreaLines.GO_TO_LINE);

    @Override
    public Option<StyledElement<?>> line(AreaState state) {
        return state.line().number().map(line::field);
    }

    @Override
    public Option<Reaction> typed(AreaState state, Viewport view, KeyEvent event) {
        return state.line().number().map(text -> AreaLines.typed(state, view, text, event, line::edit));
    }

    @Override
    public AreaState pressed(AreaState state) {
        return state.withLine(state.line().withWanted(Option.none()));
    }

    @Override
    public Option<Reaction> key(AreaState state, Viewport view, KeyEvent event) {
        return Option.when(KeyTrigger.ch(AreaLines.GO_TO_LINE).matches(event), () -> AreaLines.opened(state, view));
    }

    @Override
    public Option<Reaction> pasted(AreaState state, Viewport view, PasteEvent event) {
        return state.line()
                .number()
                .map(text -> line.paste(text, event))
                // The open line keeps what it refuses, as it keeps the keys it does not take.
                .map(text ->
                        AreaLines.isNumber(text) ? AreaLines.edited(state, view, text) : Reaction.handled(state, view));
    }
}
