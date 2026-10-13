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
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Popup;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The standard popup to pick one of a list of names that comes from the open popup, like the branches. A name is
 * picked with the arrow keys and Enter; Escape cancels. Whether the popup is open lives in the {@link Model}, as
 * a popup of type {@code P}.
 *
 * @param <P> the popup that makes this dialog show
 */
public final class PickDialog<P extends Popup> implements PopupView {

    private static final int WIDTH = 40;
    // The padding and the border.
    private static final int ROW_OVERHEAD = 2 + 2;
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
     */
    public record Spec<P>(String id, Class<P> popupType, Function<P, String> title, Function<P, List<String>> options,
                          Function<String, Msg> onPick, Supplier<Msg> onCancel) {
    }

    private final Spec<P> spec;
    private final Consumer<Msg> dispatch;
    private final ListElement<?> list;
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
            return DialogFrame.withHint(spec.title().apply(popup), Math.max(WIDTH, widest + ROW_OVERHEAD),
                    "dialog.hint.pickCancel", List.of(list.length(Math.clamp(shown.size(), 1, MAX_ROWS))));
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
