package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;
import net.vanfleteren.jazygit.ui.Theme;
import net.vanfleteren.jazygit.ui.widgets.ChoiceDialog.Choice;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A selectable list of options that ends in a cancel option, for inside a popup. An option is chosen with its
 * letter, or with the arrow keys and Enter; Escape cancels. An option that does not apply is struck through
 * and dimmed, and cannot be chosen.
 *
 * @param <P> the popup the list is shown in
 */
public final class ChoiceList<P extends Popup> {

    private static final char CANCEL_KEY = 'c';

    /**
     * An option as it is shown: its text, and whether it applies.
     */
    private record Row(String label, boolean enabled) {
    }

    private final List<Choice<P>> choices;
    private final Consumer<Msg> dispatch;
    private final ListElement<?> list;
    // What the rows show, as of the last render.
    private List<Row> shown = List.empty();

    /**
     * @param id       the id of the list, which takes the focus while the popup is open
     * @param choices  the options, in order; a cancel option is added after them
     * @param onCancel the message for cancelling, by the cancel option or by Escape
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public ChoiceList(String id, List<Choice<P>> choices, Supplier<Msg> onCancel, Consumer<Msg> dispatch) {
        this.choices = choices.append(new Choice<P>(CANCEL_KEY, "dialog.choice.cancel", onCancel));
        this.dispatch = dispatch;
        this.list = list()
                .id(id)
                .focusable()
                .highlightSymbol("")
                .onKeyEvent(this::handleKey);
    }

    /**
     * The list for {@code popup}, one line per option.
     */
    public StyledElement<?> render(Model model, P popup) {
        show(choices.map(choice -> new Row(choice.label().apply(popup), choice.enabled().test(model, popup))));
        return list.length(height());
    }

    /**
     * How many lines the list takes.
     */
    public int height() {
        return choices.size();
    }

    /**
     * The width of the widest option, as of the last render, with its letter.
     */
    public int width() {
        return shown.zipWith(choices, (row, choice) -> rowText(choice, row).length()).max().getOrElse(0);
    }

    private void show(List<Row> rows) {
        if (!rows.equals(shown)) {
            shown = rows;
            list.elements(choices.zipWith(rows, ChoiceList::row).toJavaArray(StyledElement[]::new));
        }
    }

    private static StyledElement<?> row(Choice<?> choice, Row row) {
        String text = rowText(choice, row);
        return row.enabled() ? text(text) : text(text).addClass(Theme.Classes.DISABLED);
    }

    private static String rowText(Choice<?> choice, Row row) {
        return Messages.get("dialog.choice.row", String.valueOf(choice.key()), row.label());
    }

    private EventResult handleKey(KeyEvent event) {
        Option<Choice<P>> chosen = event.code() == KeyCode.ESCAPE ? Option.of(choices.last())
                : event.isConfirm() ? choices.lift().apply(Math.clamp(list.selected(), 0, choices.size() - 1))
                : choices.find(choice -> event.isChar(choice.key()));
        chosen.forEach(this::choose);
        return chosen.isDefined() ? EventResult.HANDLED : EventResult.UNHANDLED;
    }

    private void choose(Choice<P> choice) {
        // A disabled option is not actionable.
        boolean disabled = shown.lift().apply(choices.indexOf(choice)).exists(row -> !row.enabled());
        if (!disabled) {
            list.selected(0);
            dispatch.accept(choice.msg().get());
        }
    }
}
