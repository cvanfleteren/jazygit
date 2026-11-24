package net.vanfleteren.jazygit.kernel.ui.widgets;

import dev.tamboui.css.cascade.CssStyleResolver;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.toolkit.element.DefaultRenderContext;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.TextAreaElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.PasteEvent;
import dev.tamboui.tui.event.TextEditing;
import dev.tamboui.widgets.input.TextAreaState;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.kernel.ui.edit.LineEdit;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * A text area where Ctrl+Enter submits instead of inserting a newline, where the terminal tells it from Enter; most
 * send both alike. The plain text area turns
 * every Enter into a newline, whatever the modifiers, and handles its keys before any handler that
 * can be attached to it; so this element registers itself with the same id, ahead of the text area,
 * to see the keys first. It also knows the {@link LineEdit readline keys}, which the text area would take as
 * plain ones. It hands the other keys on to the text area itself, so it can tell when the text changed.
 *
 * <p>A line too long for the area is wrapped at a word over the rows below it, and kept as one line: the text gets no
 * line break it was not given (ADR 193). Up and Down move by the rows shown. A {@link #ruler ruler} can mark the column
 * past the length lines are to keep to, as the commit description does.
 */
public final class SubmittableTextArea extends StyledElement<SubmittableTextArea> {

    private final TextAreaElement area;
    private final TextAreaState state;
    private final Runnable onSubmit;
    private Consumer<String> onChange = text -> {};
    // The rows the text area takes, borders included, if it is told.
    private Option<Integer> height = Option.none();
    // Shown in the bottom border, if any.
    private Option<String> bottomTitle = Option.none();
    // How many columns of text the ruler comes after, if there is one.
    private Option<Integer> ruler = Option.none();

    /**
     * @param area     the text area; it gets the id
     * @param state    the state of the text area
     * @param onSubmit what Ctrl+Enter does
     */
    public SubmittableTextArea(TextAreaElement area, TextAreaState state, String id, Runnable onSubmit) {
        this.area = area.id(id).wrapWord();
        this.state = state;
        this.onSubmit = onSubmit;
        id(id);
    }

    /**
     * Makes the text area this many rows high, borders included.
     */
    public SubmittableTextArea rows(int rows) {
        this.height = Option.of(rows);
        return length(rows);
    }

    /**
     * Shows {@code title} in the bottom border, on the right, as the title is shown in the top one.
     */
    public SubmittableTextArea bottomTitle(String title) {
        this.bottomTitle = Option.of(title);
        return this;
    }

    /**
     * Marks the column after the first {@code columns} of text on every row, in the style of the theme's
     * {@link Theme.Classes#RULER ruler}: text that reaches it is longer than that. A ruler past the area is not drawn.
     * The area is to have a border, as the text starts inside it.
     */
    public SubmittableTextArea ruler(int columns) {
        this.ruler = Option.of(columns);
        return this;
    }

    /**
     * Calls {@code onChange} with the text whenever a key or a paste changed it.
     */
    public SubmittableTextArea onChange(Consumer<String> onChange) {
        this.onChange = onChange;
        return this;
    }

    @Override
    public EventResult handleKeyEvent(KeyEvent event, boolean focused) {
        if (!focused) {
            return EventResult.UNHANDLED;
        }
        if (event.code() == KeyCode.ENTER && event.hasCtrl()) {
            onSubmit.run();
            return EventResult.HANDLED;
        }
        String before = state.text();
        // The text area runs its own onKeyEvent handler for a key it does not use; the router tries no other
        // element under the same id.
        boolean handled = LineEdit.handle(state, event)
                || area.handleKeyEvent(event, true).isHandled();
        if (!state.text().equals(before)) {
            onChange.accept(state.text());
        }
        // A key neither uses goes to the handler set with onKeyEvent on this element.
        return handled ? EventResult.HANDLED : super.handleKeyEvent(event, true);
    }

    /**
     * Puts what is pasted in at the cursor, line breaks and all.
     */
    @Override
    public EventResult handlePasteEvent(PasteEvent event) {
        String before = state.text();
        TextEditing.handle(state, event);
        if (!state.text().equals(before)) {
            onChange.accept(state.text());
        }
        return EventResult.HANDLED;
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        Size size = area.preferredSize(availableWidth, availableHeight, context);
        return height.map(rows -> Size.of(size.widthOr(availableWidth), rows)).getOrElse(size);
    }

    @Override
    protected void renderContent(Frame frame, Rect area, RenderContext context) {
        if (context instanceof DefaultRenderContext rendering) {
            // Registering is first come, first served; the text area would otherwise be ahead.
            rendering.registerElement(this, area);
        }
        this.area.render(frame, area, context);
        // Inside the border: the text starts one column in, between the top and the bottom row.
        ruler.filter(columns -> area.height() > 2 && columns < area.width() - 2)
                .forEach(columns -> frame.buffer()
                        .setStyle(
                                new Rect(area.x() + 1 + columns, area.y() + 1, 1, area.height() - 2),
                                rulerStyle(context)));
        bottomTitle.forEach(title -> BottomTitle.draw(frame, area, title));
    }

    /**
     * What the theme's rule for the ruler declares besides what every span gets, as the colours of {@code *}, which
     * would recolour the text under it: its background, or else its attributes.
     */
    private static Style rulerStyle(RenderContext context) {
        CssStyleResolver base = Option.ofOptional(context.resolveStyle("Span")).getOrElse(CssStyleResolver.empty());
        return Option.ofOptional(context.resolveStyle("Span", Theme.Classes.RULER))
                .map(rule -> Option.ofOptional(rule.background())
                        .filter(bg -> !Option.ofOptional(base.background()).contains(bg))
                        .map(Style.EMPTY::bg)
                        .getOrElse(() -> List.ofAll(rule.modifiers())
                                .removeAll(base.modifiers())
                                .foldLeft(Style.EMPTY, Style::addModifier)))
                .getOrElse(Style.EMPTY);
    }
}
