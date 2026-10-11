package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.*;

import net.vanfleteren.jazygit.ui.Theme;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The standard popup offering a choice between several options, as a selectable list that always ends in
 * a cancel option. An option is chosen with its letter, or with the arrow keys and Enter; Escape cancels.
 * An option that does not apply is struck through and dimmed, and cannot be chosen. Whether the popup is open
 * lives in the {@link Model}, as a popup of type {@code P}.
 *
 * @param <P> the popup that makes this dialog show
 */
public final class ChoiceDialog<P extends Popup> {

    private static final int WIDTH = 40;
    private static final char CANCEL_KEY = 'c';

    /**
     * An option of the dialog.
     *
     * @param key      the letter that chooses it
     * @param labelKey the message key of its text
     * @param msg      the message for choosing it
     * @param enabled  whether it applies, given the model and the open popup
     */
    public record Choice<P>(char key, String labelKey, Supplier<Msg> msg, BiPredicate<Model, P> enabled) {

        /**
         * An option that always applies.
         */
        public Choice(char key, String labelKey, Supplier<Msg> msg) {
            this(key, labelKey, msg, (model, popup) -> true);
        }
    }

    private final String id;
    private final Class<P> popupType;
    private final Function<P, String> title;
    private final List<Choice<P>> choices;
    private final Consumer<Msg> dispatch;
    private final ListElement<?> list;
    // What the rows show; null before the first render.
    private List<Boolean> shownEnabled;
    // Whether each option applies, as of the last render.
    private List<Boolean> enabled = List.of();

    /**
     * @param id        the id of the list, which takes the focus while the dialog is open
     * @param popupType the popup that makes the dialog show
     * @param title     the title, from the popup
     * @param choices   the options, in order; a cancel option is added after them
     * @param onCancel  the message for cancelling, by the cancel option or by Escape
     * @param dispatch  receives the messages for the user's actions, on the render thread
     */
    public ChoiceDialog(String id, Class<P> popupType, Function<P, String> title, List<Choice<P>> choices,
                        Supplier<Msg> onCancel, Consumer<Msg> dispatch) {
        this.id = id;
        this.popupType = popupType;
        this.title = title;
        List<Choice<P>> all = new ArrayList<>(choices);
        all.add(new Choice<>(CANCEL_KEY, "dialog.choice.cancel", onCancel));
        this.choices = List.copyOf(all);
        this.dispatch = dispatch;
        this.list = list()
                .id(id)
                .focusable()
                .highlightSymbol("")
                .onKeyEvent(this::handleKey);
    }

    public String id() {
        return id;
    }

    /**
     * The popup, while the model has {@code P} open.
     */
    public Optional<Element> render(Model model) {
        return model.popup(popupType).map(popup -> {
            showOptions(choices.stream().map(choice -> choice.enabled().test(model, popup)).toList());
            return dialog(title.apply(popup),
                    list.length(choices.size()),
                    text(""),
                    text(Messages.get("dialog.hint.selectCancel")).addClass(Theme.Classes.DIM))
                    .rounded()
                    .width(WIDTH)
                    .padding(1)
                    .onCancel(() -> choose(choices.getLast()));
        });
    }

    private void showOptions(List<Boolean> nowEnabled) {
        enabled = nowEnabled;
        if (!nowEnabled.equals(shownEnabled)) {
            shownEnabled = nowEnabled;
            list.elements(java.util.stream.IntStream.range(0, choices.size())
                    .mapToObj(i -> row(choices.get(i), nowEnabled.get(i)))
                    .toArray(StyledElement[]::new));
        }
    }

    private static StyledElement<?> row(Choice<?> choice, boolean enabled) {
        String text = Messages.get("dialog.choice.row", String.valueOf(choice.key()), Messages.get(choice.labelKey()));
        return enabled ? text(text) : text(text).addClass(Theme.Classes.DISABLED);
    }

    private EventResult handleKey(KeyEvent event) {
        if (event.code() == KeyCode.ESCAPE) {
            choose(choices.getLast());
            return EventResult.HANDLED;
        }
        if (event.isConfirm()) {
            choose(choices.get(Math.clamp(list.selected(), 0, choices.size() - 1)));
            return EventResult.HANDLED;
        }
        return choices.stream()
                .filter(choice -> event.isChar(choice.key()))
                .findFirst()
                .map(choice -> {
                    choose(choice);
                    return EventResult.HANDLED;
                })
                .orElse(EventResult.UNHANDLED);
    }

    private void choose(Choice<P> choice) {
        // A disabled option is not actionable.
        int index = choices.indexOf(choice);
        if (index < enabled.size() && !enabled.get(index)) {
            return;
        }
        list.selected(0);
        dispatch.accept(choice.msg().get());
    }
}
