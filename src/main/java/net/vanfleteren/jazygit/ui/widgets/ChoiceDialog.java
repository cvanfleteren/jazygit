package net.vanfleteren.jazygit.ui.widgets;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;

import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The standard popup offering a choice between several options, as a {@link ChoiceList}. Whether the popup is
 * open lives in the {@link Model}, as a popup of type {@code P}.
 *
 * @param <P> the popup that makes this dialog show
 */
public final class ChoiceDialog<P extends Popup> implements PopupView {


    /**
     * An option of the dialog.
     *
     * @param key      the letter that chooses it
     * @param label    its text, given the open popup
     * @param msg      the message for choosing it
     * @param enabled  whether it applies, given the model and the open popup
     * @param heading  the message key of the heading it starts a group of options under, if any
     * @param onHighlight the message for highlighting it with the arrow keys, if it shows what choosing it does
     */
    public record Choice<P>(char key, Function<P, String> label, Supplier<Msg> msg, BiPredicate<Model, P> enabled,
                            Option<String> heading, Option<Supplier<Msg>> onHighlight) {

        /**
         * An option with a text that depends on the popup, without a heading.
         */
        public Choice(char key, Function<P, String> label, Supplier<Msg> msg, BiPredicate<Model, P> enabled) {
            this(key, label, msg, enabled, Option.none(), Option.none());
        }

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

        /**
         * This option, starting a group of options under the heading with the message key {@code headingKey}.
         */
        public Choice<P> withHeading(String headingKey) {
            return new Choice<>(key, label, msg, enabled, Option.of(headingKey), onHighlight);
        }

        /**
         * This option, sending {@code preview} when it is highlighted, so what choosing it does shows before.
         */
        public Choice<P> withPreview(Supplier<Msg> preview) {
            return new Choice<>(key, label, msg, enabled, heading, Option.of(preview));
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
     * @param minWidth  the width it takes at least, borders included, so it does not grow as the options change
     * @param opensAt   the key of the option highlighted when the dialog opens, given the popup; else the first
     */
    public record Spec<P>(String id, Class<P> popupType, Function<P, String> title, List<Choice<P>> choices,
                          Supplier<Msg> onCancel, int minWidth, Function<P, Option<Character>> opensAt) {

        /**
         * A dialog that opens with its first option highlighted.
         */
        public Spec(String id, Class<P> popupType, Function<P, String> title, List<Choice<P>> choices,
                    Supplier<Msg> onCancel, int minWidth) {
            this(id, popupType, title, choices, onCancel, minWidth, popup -> Option.none());
        }

        /**
         * A dialog as wide as a list, or its widest option.
         */
        public Spec(String id, Class<P> popupType, Function<P, String> title, List<Choice<P>> choices,
                    Supplier<Msg> onCancel) {
            this(id, popupType, title, choices, onCancel, DialogFrame.LIST_WIDTH);
        }

        /**
         * This dialog, opening with the option of the key {@code opensAt} gives highlighted.
         */
        public Spec<P> openingAt(Function<P, Option<Character>> opensAt) {
            return new Spec<>(id, popupType, title, choices, onCancel, minWidth, opensAt);
        }
    }

    private final Spec<P> spec;
    private final ChoiceList<P> list;
    // The popup the highlight was put in place for, so that is only done when it opens.
    private Option<P> shownFor = Option.none();

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public ChoiceDialog(Spec<P> spec, Consumer<Msg> dispatch) {
        this.spec = spec;
        this.list = new ChoiceList<>(spec.id(), spec.choices(), spec.onCancel(), dispatch);
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
        Option<P> open = model.popup(spec.popupType());
        open.filter(popup -> !shownFor.contains(popup)).forEach(popup -> list.highlightKey(spec.opensAt().apply(popup)));
        shownFor = open;
        return open.map(popup -> {
            StyledElement<?> rows = list.render(model, popup);
            return DialogFrame.withHint(spec.title().apply(popup), Math.max(spec.minWidth(), list.width() + DialogFrame.FRAME),
                    "dialog.hint.selectCancel", List.of(rows));
        });
    }
}
