package net.vanfleteren.jazygit.ui.widgets;

import static dev.tamboui.toolkit.Toolkit.*;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.style.Style;
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

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The standard popup to pick one of a list of names that comes from the open popup, like the branches. A name is
 * picked with the arrow keys and Enter; Escape cancels. When there are no names, it can say so in a dimmed line,
 * which cannot be picked. Whether the popup is open lives in the {@link Model}, as
 * a popup of type {@code P}.
 *
 * @param <P> the popup that makes this dialog show
 */
public final class PickDialog<P extends Popup> implements PopupView {

    // The rows shown at most; the list scrolls through the others.
    private static final int MAX_ROWS = 15;

    /**
     * What the dialog shows and sends.
     *
     * @param id        the id of the list, which takes the focus while the dialog is open
     * @param popupType the popup that makes the dialog show
     * @param title     the title, from the popup
     * @param options   the names to pick from, from the popup
     * @param onPick    the message for picking a name
     * @param onCancel  the message for cancelling, by Escape
     * @param emptyKey  the message key of the line shown when there are no names to pick, if any
     */
    public record Spec<P>(String id, Class<P> popupType, Function<P, String> title, Function<P, List<String>> options,
                          Function<String, Msg> onPick, Supplier<Msg> onCancel, Option<String> emptyKey) {

        /**
         * A dialog that shows no line when there are no names to pick.
         */
        public Spec(String id, Class<P> popupType, Function<P, String> title, Function<P, List<String>> options,
                    Function<String, Msg> onPick, Supplier<Msg> onCancel) {
            this(id, popupType, title, options, onPick, onCancel, Option.none());
        }
    }

    private final Spec<P> spec;
    private final Consumer<Msg> dispatch;
    private final ListElement<?> list;
    // Shown instead of the list when there are no names: the same id, so it takes the focus and Escape the same
    // way, but its one line is not highlighted.
    private final Option<ListElement<?>> empty;
    // The names the rows show, as of the last render.
    private List<String> shown = List.empty();

    /**
     * @param dispatch receives the messages for the user's actions, on the render thread
     */
    public PickDialog(Spec<P> spec, Consumer<Msg> dispatch) {
        this.spec = spec;
        this.dispatch = dispatch;
        this.list = list()
                .id(spec.id())
                .focusable()
                .highlightSymbol("")
                .onKeyEvent(this::handleKey);
        this.empty = spec.emptyKey().map(key -> list()
                .id(spec.id())
                .focusable()
                .highlightSymbol("")
                .highlightStyle(Style.EMPTY)
                .onKeyEvent(this::handleKey)
                .elements(text(Messages.get(key)).addClass(Theme.Classes.DIM)));
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
            show(spec.options().apply(popup));
            int widest = shown.map(String::length).max().getOrElse(0);
            ListElement<?> rows = empty.filter(none -> shown.isEmpty()).getOrElse(list);
            return DialogFrame.withHint(spec.title().apply(popup), Math.max(DialogFrame.LIST_WIDTH, widest + DialogFrame.FRAME),
                    "dialog.hint.pickCancel", List.of(rows.length(Math.clamp(shown.size(), 1, MAX_ROWS))));
        });
    }

    private void show(List<String> options) {
        if (!options.equals(shown)) {
            shown = options;
            list.selected(0);
            list.elements(options.<StyledElement<?>>map(option -> text(option)).toJavaArray(StyledElement[]::new));
        }
    }

    private EventResult handleKey(KeyEvent event) {
        Option<Msg> msg = event.code() == KeyCode.ESCAPE ? Option.of(spec.onCancel().get())
                : event.isConfirm() ? shown.lift().apply(list.selected()).map(spec.onPick())
                : Option.none();
        msg.forEach(this::send);
        return msg.isDefined() ? EventResult.HANDLED : EventResult.UNHANDLED;
    }

    private void send(Msg msg) {
        // The next time it opens, it starts at the top again.
        shown = List.empty();
        dispatch.accept(msg);
    }
}
