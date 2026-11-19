package net.vanfleteren.jazygit.kernel.content;

import static net.vanfleteren.jazygit.kernel.content.Reaction.handled;

import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.PasteEvent;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.view.ContentSearchMsg;
import net.vanfleteren.jazygit.kernel.ui.widgets.FilterLine;

/**
 * The search of the content panel as a tool of an area (ADR 126): the line to type its query in, Escape dropping a
 * search kept, and {@code n} and {@code N} going from match to match. What they do is {@link AreaSearch}'s.
 */
final class SearchTool implements AreaTool {

    // The line keeps where its cursor is; its text is the area's.
    private final FilterLine line = new FilterLine();

    @Override
    public Option<StyledElement<?>> line(AreaState state) {
        return state.search().typing().map(line::field);
    }

    @Override
    public Option<Reaction> typed(AreaState state, Viewport view, KeyEvent event) {
        return state.search().typing().map(text -> AreaSearch.typed(state, view, text, event, line::edit));
    }

    @Override
    public Option<Reaction> beforeLeaving(AreaState state, Viewport view, KeyEvent event) {
        return Option.when(
                event.isCancel() && state.searching(),
                () -> handled(state, view).sending(new ContentSearchMsg.Cleared()));
    }

    @Override
    public Option<Reaction> key(AreaState state, Viewport view, KeyEvent event) {
        return AreaSearch.step(state, view, event);
    }

    @Override
    public Option<Reaction> pasted(AreaState state, Viewport view, PasteEvent event) {
        return state.search()
                .typing()
                .map(text -> handled(state, view).sending(new ContentSearchMsg.Edited(line.paste(text, event))));
    }
}
