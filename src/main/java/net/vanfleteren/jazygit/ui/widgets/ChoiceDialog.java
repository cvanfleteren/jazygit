package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.Element;
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
public final class ChoiceDialog<P extends Popup> implements PopupView {

    private static final int WIDTH = 40;
    private static final char CANCEL_KEY = 'c';
    // The "(k) " before a label, the padding and the border.
    private static final int ROW_OVERHEAD = 4 + 2 + 2;

    /**
     * An option of the dialog.
     *
     * @param key      the letter that chooses it
     * @param label    its text, given the open popup
     * @param msg      the message for choosing it
     * @param enabled  whether it applies, given the model and the open popup
     */
    public record Choice<P>(char key, Function<P, String> label, Supplier<Msg> msg, BiPredicate<Model, P> enabled) {

        /**
         * An option with a fixed text, given by its message key.
         */
        public Choice(char key, String labelKey, Supplier<Msg> msg, BiPredicate<Model, P> enabled) {
            this(key, popup -> Messages.get(labelKey), msg, enabled);
        }

        /**
         * An option with a fixed text that always applies.
         */
        public Choice(char key, String labelKey, Supplier<Msg> msg) {
            this(key, labelKey, msg, (model, popup) -> true);
        }

        /**
         * An option with a text that depends on the popup, that always applies.
         */
        public Choice(char key, Function<P, String> label, Supplier<Msg> msg) {
            this(key, label, msg, (model, popup) -> true);
        }
    }

    /**
     * What the dialog shows and sends.
     *
     * @param id        the id of the list, which takes the focus while the dialog is open
     * @param popupType the popup that makes the dialog show
     * @param title     the title, from the popup
     * @param choices   the options, in order; a cancel option is added after them
     * @param onCancel  the message for cancelling, by the cancel option or by Escape
     */
    public record Spec<P>(String id, Class<P> popupType, Function<P, String> title, List<Choice<P>> choices,
                          Supplier<Msg> onCancel) {
    }

    /**
     * An option as it is shown: its text, and whether it applies.
     */
    private record Row(String label, boolean enabled) {
    }

    private final Spec<P> spec;
    private final List<Choice<P>> choices;
    private final Consumer<Msg> dispatch;
    private final ListElement<?> list;
    // What the rows show, as of the last render.
    private List<Row> shown = List.empty();

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public ChoiceDialog(Spec<P> spec, Consumer<Msg> dispatch) {
        this.spec = spec;
        this.choices = spec.choices().append(new Choice<P>(CANCEL_KEY, "dialog.choice.cancel", spec.onCancel()));
        this.dispatch = dispatch;
        this.list = list()
                .id(spec.id())
                .focusable()
                .highlightSymbol("")
                .onKeyEvent(this::handleKey);
    }

    public String id() {
        return spec.id();
    }

    @Override
    public List<String> ids() {
        return List.of(spec.id());
    }

    /**
     * The popup, while the model has {@code P} open.
     */
    @Override
    public Option<Element> render(Model model) {
        return model.popup(spec.popupType()).map(popup -> {
            show(choices.map(choice -> new Row(choice.label().apply(popup), choice.enabled().test(model, popup))));
            return DialogFrame.withHint(spec.title().apply(popup), Math.max(WIDTH, widest() + ROW_OVERHEAD),
                    "dialog.hint.selectCancel", List.of(list.length(choices.size())));
        });
    }

    private int widest() {
        return shown.map(row -> row.label().length()).max().getOrElse(0);
    }

    private void show(List<Row> rows) {
        if (!rows.equals(shown)) {
            shown = rows;
            list.elements(choices.zipWith(rows, ChoiceDialog::row).toJavaArray(StyledElement[]::new));
        }
    }

    private static StyledElement<?> row(Choice<?> choice, Row row) {
        String text = Messages.get("dialog.choice.row", String.valueOf(choice.key()), row.label());
        return row.enabled() ? text(text) : text(text).addClass(Theme.Classes.DISABLED);
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
